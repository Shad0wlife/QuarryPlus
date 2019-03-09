/*
 * Copyright (C) 2012,2013 yogpstop This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.yogpc.qp.tile;

import java.util.*;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import com.yogpc.qp.PowerManager;
import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.QuarryPlusI;
import com.yogpc.qp.block.BlockPump;
import com.yogpc.qp.gui.TranslationKeys;
import com.yogpc.qp.packet.PacketHandler;
import com.yogpc.qp.packet.pump.Mappings;
import com.yogpc.qp.packet.pump.Now;
import com.yogpc.qp.version.VersionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import scala.Symbol;

import static io.github.opencubicchunks.cubicchunks.api.util.Bits.*;

public class TilePump extends APacketTile implements IEnchantableTile, ITickable, IDebugSender {
    @SuppressWarnings("NullableProblems")
    @Nullable
    public EnumFacing connectTo = null;
    private boolean initialized = false;

    private EnumFacing preFacing;

    protected byte unbreaking;
    protected byte fortune;
    protected boolean silktouch;
    private final LinkedList<FluidStack> liquids = new LinkedList<>();
    public final EnumMap<EnumFacing, LinkedList<String>> mapping = new EnumMap<>(EnumFacing.class);
    public final EnumMap<EnumFacing, PumpTank> tankMap = new EnumMap<>(EnumFacing.class);

    {
        for (EnumFacing value : EnumFacing.VALUES) {
            tankMap.put(value, new PumpTank(value));
            mapping.put(value, new LinkedList<>());
        }
    }

    public TileBasic G_connected() {
        if (connectTo != null) {
            final TileEntity te = getWorld().getTileEntity(getPos().offset(connectTo));
            if (te instanceof TileBasic)
                return (TileBasic) te;
            else {
                setConnectTo(null);
                if (!getWorld().isRemote)
                    S_sendNowPacket();
                return null;
            }
        }
        return null;
    }

    public boolean G_working() {
        return this.py >= this.cy;
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbttc) {
        super.readFromNBT(nbttc);
        this.silktouch = nbttc.getBoolean("silktouch");
        this.fortune = nbttc.getByte("fortune");
        this.unbreaking = nbttc.getByte("unbreaking");
        if (nbttc.hasKey("connectTo")) {
            setConnectTo(EnumFacing.getFront(nbttc.getByte("connectTo")));
            preFacing = this.connectTo;
        }
        if (nbttc.getTag("mapping0") instanceof NBTTagList)
            for (int i = 0; i < this.mapping.size(); i++)
                readStringCollection(nbttc.getTagList("mapping" + i, Constants.NBT.TAG_STRING), this.mapping.get(EnumFacing.getFront(i)));
        this.range = nbttc.getByte("range");
        this.quarryRange = nbttc.getBoolean("quarryRange");
        if (this.silktouch) {
            this.liquids.clear();
            final NBTTagList nbttl = nbttc.getTagList("liquids", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < nbttl.tagCount(); i++)
                this.liquids.add(FluidStack.loadFluidStackFromNBT(nbttl.getCompoundTagAt(i)));
        }
    }

    private static void readStringCollection(final NBTTagList nbttl, final Collection<String> target) {
        target.clear();
        IntStream.range(0, nbttl.tagCount()).mapToObj(nbttl::getStringTagAt).forEach(target::add);
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound nbttc) {
        nbttc.setBoolean("silktouch", this.silktouch);
        nbttc.setByte("fortune", this.fortune);
        nbttc.setByte("unbreaking", this.unbreaking);
        if (connectTo != null)
            nbttc.setByte("connectTo", (byte) this.connectTo.ordinal());
        for (int i = 0; i < this.mapping.size(); i++)
            nbttc.setTag("mapping" + i, writeStringCollection(this.mapping.get(EnumFacing.getFront(i))));
        nbttc.setByte("range", this.range);
        nbttc.setBoolean("quarryRange", this.quarryRange);
        if (this.silktouch) {
            final NBTTagList nbttl = new NBTTagList();
            for (final FluidStack l : this.liquids)
                nbttl.appendTag(l.writeToNBT(new NBTTagCompound()));
            nbttc.setTag("liquids", nbttl);
        }
        return super.writeToNBT(nbttc);
    }

    private static NBTTagList writeStringCollection(final Collection<String> target) {
        final NBTTagList nbttl = new NBTTagList();
        target.stream().map(NBTTagString::new).forEach(nbttl::appendTag);
        return nbttl;
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos offset = getPos().offset(facing);
                IBlockState state = getWorld().getBlockState(offset);
                if (state.getBlock().hasTileEntity(state)) {
                    TileEntity tileEntity = getWorld().getTileEntity(offset);
                    if (tileEntity != null && tileEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite())) {
                        IFluidHandler handler = tileEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite());
                        if (handler != null) {
                            PumpTank tank = tankMap.get(facing);
                            FluidStack resource = tank.drain(Fluid.BUCKET_VOLUME, false);
                            if (resource != null) {
                                int fill = handler.fill(resource, false);
                                if (fill > 0) {
                                    handler.fill(tank.drain(fill, true), true);
                                }
                            }
                        }
                    }
                }
            }
            if (!initialized) {
                if (connectTo != null) {
                    TileEntity te = getWorld().getTileEntity(getPos().offset(connectTo));
                    if (te instanceof TileBasic && ((TileBasic) te).S_connectPump(this.connectTo.getOpposite())) {
                        S_sendNowPacket();
                        this.initialized = true;
                    } else if (getWorld().isAirBlock(getPos().offset(connectTo))) {
                        setConnectTo(null);
                        S_sendNowPacket();
                        this.initialized = true;
                    }
                }
            }
        }
    }

    @Override
    public void G_reinit() {
        if (!getWorld().isRemote) {
            TileEntity te;
            for (EnumFacing facing : EnumFacing.VALUES) {
                te = getWorld().getTileEntity(getPos().offset(facing));
                if (te instanceof TileBasic && ((TileBasic) te).S_connectPump(facing.getOpposite())) {
                    setConnectTo(facing);
                    S_sendNowPacket();
                    return;
                }
            }
            setConnectTo(null);
            S_sendNowPacket();
        }
    }

    private void S_sendNowPacket() {
        //when connection changed or working changed
        if (preFacing != connectTo || getWorld().getBlockState(getPos()).getValue(BlockPump.ACTING) != G_working()) {
            preFacing = connectTo;
            PacketHandler.sendToAround(Now.create(this), getWorld(), getPos());
        }
    }

    public void setConnectTo(@Nullable EnumFacing connectTo) {
        this.connectTo = connectTo;
        if (hasWorld()) {
            IBlockState state = getWorld().getBlockState(getPos());
            if (connectTo == null && state.getValue(BlockPump.CONNECTED)) {
                validate();
                getWorld().setBlockState(getPos(), state.withProperty(BlockPump.CONNECTED, false));
                validate();
                getWorld().setTileEntity(getPos(), this);
            } else if (connectTo != null && !state.getValue(BlockPump.CONNECTED)) {
                validate();
                getWorld().setBlockState(getPos(), state.withProperty(BlockPump.CONNECTED, true));
                validate();
                getWorld().setTileEntity(getPos(), this);
            }
        }
    }

    public void setWorking(boolean b) {
        if (b) {
            this.cy = this.py = -1;
        } else {
            this.py = Integer.MIN_VALUE;
        }
        if (!getWorld().isRemote) {
            IBlockState state = getWorld().getBlockState(getPos());
            validate();
            getWorld().setBlockState(getPos(), state.withProperty(BlockPump.ACTING, b));
            validate();
            getWorld().setTileEntity(getPos(), this);
        }
    }

    public void S_OpenGUI(EnumFacing facing, final EntityPlayer ep) {
        PacketHandler.sendToClient(Mappings.All.create(this, facing), (EntityPlayerMP) ep);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private byte range = 0;
    private boolean quarryRange = true;

    private int  py = Integer.MIN_VALUE;
    private int cy = -1;

    public void S_changeRange(final EntityPlayer ep) {
        if (this.range >= (this.fortune + 1) * 2) {
            if (G_connected() instanceof TileQuarry)
                this.quarryRange = true;
            this.range = 0;
        } else if (this.quarryRange)
            this.quarryRange = false;
        else
            this.range++;
        if (this.quarryRange)
            VersionUtil.sendMessage(ep, new TextComponentTranslation(TranslationKeys.PUMP_RTOGGLE_QUARRY));
        else
            VersionUtil.sendMessage(ep, new TextComponentTranslation(TranslationKeys.PUMP_RTOGGLE_NUM, Integer.toString(this.range * 2 + 1)));
    }

    /*

     */
    private static long blockPosToLong(BlockPos source, BlockPos blockPos){
        return packSignedToLong(blockPos.getX() - source.getX(), 16, 48) |
                packUnsignedToLong( source.getY() - blockPos.getY(), 31, 16) |
                packSignedToLong(blockPos.getZ() - source.getZ(), 16, 0);
    }

    private static BlockPos longToBlockPos(BlockPos source, long l){
        return new BlockPos(
                unpackSigned(l, 16, 48) + source.getX(),
                source.getY() - unpackUnsigned(l, 31, 16) ,
                unpackSigned(l, 16,0) + source.getZ()
        );
    }

    LongArrayList liquidCoords = new LongArrayList();
    /**
     * Finds liquids in the layer of the given block coordinates in the range of the Mining device
     * Uses a slightly modified breadth first search
     * @param miner The mining device giving the range
     * @param srcX X Coordinate Source
     * @param srcY Y Coordinate Source
     * @param srcZ Z Coordinate Source
     */
    void findLiquidsInLayer(final TileBasic miner, final int srcX, final int srcY, final int srcZ){
        QuarryPlus.LOGGER.debug("[Pump] Starting to discover water");
        BlockPos targetPos = new BlockPos(srcX, srcY, srcZ);
        //If the current block was already registered, we don't need the tracking again, the BFS should've found everything
        if(liquidCoords.contains(blockPosToLong(this.getPos(), targetPos))){
            QuarryPlus.LOGGER.debug("[Pump] Current location already listed. Returning.");
            return;
        }
        IBlockState targetState = getBlockStateAt(targetPos);

        if (isLiquid(targetState, false, getWorld(), targetPos)) {
            //If the target is a liquid (source doesn't matter, it has to come from somewhere) search for sources

            boolean isQuarry = miner instanceof TileQuarry;
            if(isQuarry){
                LongArrayFIFOQueue q = new LongArrayFIFOQueue(); //Queue for elements to check
                LongArrayList liquidCoordsLocal = new LongArrayList(); //List of checked elements (including flowing water)
                TileQuarry quarry = (TileQuarry)miner;
                long currentLong = blockPosToLong(this.getPos(), targetPos);

                //Handle source node
                q.enqueue(currentLong);
                liquidCoordsLocal.add(blockPosToLong(this.getPos(), targetPos));
                //If it's a source, add it to the drain list too
                if (isLiquid(targetState, true, getWorld(), targetPos)){
                    liquidCoords.add(blockPosToLong(this.getPos(), targetPos));
                }

                //QuarryPlus.LOGGER.debug("[Pump] Started with ({}, {}, {}) as {}", targetPos.getX(), targetPos.getY(), targetPos.getZ(), currentLong);
                while(!q.isEmpty()){
                    long elem = q.dequeueLong();
                    BlockPos currentPos = longToBlockPos(this.getPos(), elem);
                    //QuarryPlus.LOGGER.debug("[Pump] Dequeued ({}, {}, {}) from {}", currentPos.getX(), currentPos.getY(), currentPos.getZ(), elem);

                    //Add all neighbours
                    for(int cntDirs = 0; cntDirs < 4; cntDirs++){
                        BlockPos nextPos;
                        switch (cntDirs){
                            case 0:
                                nextPos = currentPos.west();
                                break;
                            case 1:
                                nextPos = currentPos.north();
                                break;
                            case 2:
                                nextPos = currentPos.east();
                                break;
                            default:
                                nextPos = currentPos.south();
                                break;
                        }
                        if(isInXZBounds(nextPos, quarry.xMin, quarry.xMax, quarry.zMin, quarry.zMax)){
                            //New position is in quarry range
                            long nextPosLong = blockPosToLong(this.getPos(), nextPos);
                            if(!liquidCoordsLocal.contains(nextPosLong) && !liquidCoords.contains(nextPosLong)){
                                //new position isn't in the handled objects yet
                                IBlockState nextState = getBlockStateAt(nextPos);
                                if (isLiquid(nextState, false, getWorld(), nextPos)) {
                                    liquidCoordsLocal.add(nextPosLong);
                                    q.enqueue(nextPosLong);
                                }
                                if (isLiquid(nextState, true, getWorld(), targetPos)){
                                    liquidCoords.add(nextPosLong);
                                }
                            }
                        }
                    }
                }
                //Remove all stuck flowing water (only flowing water but no sources discovered)
                if(liquidCoords.isEmpty() && !liquidCoordsLocal.isEmpty()){
                    while (!liquidCoordsLocal.isEmpty()){
                        removeLiquidAndSetAt(longToBlockPos(this.getPos(), liquidCoordsLocal.removeLong(0)), Blocks.AIR.getDefaultState());
                    }
                }
            }else{
                liquidCoords.add(blockPosToLong(this.getPos(), targetPos));
            }
        }
    }

    LongArrayList borderCoords = new LongArrayList();

    /**
     * Handles all potential border spots in a layer
     * @param miner The mining device of which the borders are to be checked
     * @param y The y layer to check on
     */
    void discoverBorders(final TileBasic miner, final int y){
        boolean isQuarry = miner instanceof TileQuarry;
        if(isQuarry){
            TileQuarry quarry = (TileQuarry)miner;
            int minX = quarry.xMin;
            int maxX = quarry.xMax;
            int minZ = quarry.zMin;
            int maxZ = quarry.zMax;
            BlockPos bp1 = new BlockPos(minX, y, minZ); //NW Corner
            BlockPos bp2 = new BlockPos(maxX, y, minZ); //NE Corner
            BlockPos bp3 = new BlockPos(maxX, y, maxZ); //SE Corner
            BlockPos bp4 = new BlockPos(minX, y, maxZ); //SW Corner
            int diffX = maxX - minX;
            int diffZ = maxZ - minZ;

            BlockPos pumpBP = this.getPos();
            QuarryPlus.LOGGER.info("[Pump][bps] Pump location ({}, {}, {})", pumpBP.getX(), pumpBP.getY(), pumpBP.getZ());

            for(int cnt1 = 0; cnt1 < diffX; cnt1++){
                long bp1l = blockPosToLong(this.getPos(), bp1);
                borderCoords.add(bp1l);
                bp1 = bp1.east();
            }
            for(int cnt2 = 0; cnt2 < diffZ; cnt2++){
                long bp2l = blockPosToLong(this.getPos(), bp2);
                borderCoords.add(bp2l);
                bp2 = bp2.south();
            }
            for(int cnt3 = 0; cnt3 < diffX; cnt3++){//
                long bp3l = blockPosToLong(this.getPos(), bp3);
                borderCoords.add(bp3l);
                bp3 = bp3.west();
            }
            for(int cnt4 = 0; cnt4 < diffZ; cnt4++){//
                long bp4l = blockPosToLong(this.getPos(), bp4);
                borderCoords.add(bp4l);
                bp4 = bp4.north();
            }
        }else if(miner instanceof TileMiningWell){
            TileMiningWell tmw = (TileMiningWell)miner;
            BlockPos mwPos = new BlockPos(tmw.getPos().getX(), y, tmw.getPos().getZ());
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.east()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.east().north()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.north()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.west().north()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.west()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.west().south()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.south()));
            borderCoords.add(blockPosToLong(this.getPos(), mwPos.east().south()));
        }
    }

    /**
     * Dams the border locations stored in borderCoords
     * @param miner The mining device to draw energy from
     */
    void damBorders(final TileBasic miner){
        //QuarryPlus.LOGGER.info("[Pump] Starting to dam {} water", dammingRemaining());
        while(!borderCoords.isEmpty()){
            BlockPos targetPos = longToBlockPos(this.getPos(), borderCoords.getLong(0));
            IBlockState checkState = getBlockStateAt(targetPos);
            if (TilePump.isLiquid(checkState, true, getWorld(), targetPos)) {
                if(!PowerManager.useEnergyPump(miner, this.unbreaking, 1, 1)) {
                    return;
                }
                //Source block gets drained
                removeLiquidAndSetAt(targetPos, QuarryPlusI.blockFrame().getDammingState());
                borderCoords.removeLong(0);
            }else if(TilePump.isLiquid(checkState, false, getWorld(), targetPos)){
                if(!PowerManager.useEnergyPump(miner, this.unbreaking, 0, 1)) {
                    return;
                }
                //Only set to dam, but it's not a source so don't drain
                getWorld().setBlockState(targetPos, QuarryPlusI.blockFrame().getDammingState());
                borderCoords.removeLong(0);
            }else{
                //Not liquid, don't dam
                borderCoords.removeLong(0);
            }
        }
        //QuarryPlus.LOGGER.info("[Pump] Stopping to dam with {} water left", dammingRemaining());
    }

    /**
     * Checks a spot for liquid and places a dam if liquid is found
     * TODO marked for removal
     * @param thatPos Pos to check
     */
    void checkAndSetFrame(BlockPos thatPos){
        IBlockState checkState = getBlockStateAt(thatPos);
        if (TilePump.isLiquid(checkState)) {
            if (TilePump.isLiquid(checkState, true, getWorld(), thatPos)) {
                //Source block gets drained
                removeLiquidAndSetAt(thatPos, QuarryPlusI.blockFrame().getDammingState());
            }else {
                getWorld().setBlockState(thatPos, QuarryPlusI.blockFrame().getDammingState());
            }
        }
    }

    /**
     * Tries to drain as much of the registered liquid as poissible
     * @param powerTile Device to draw the power from
     */
    void S_removeLiquids(final APowerTile powerTile) {
        //Handle all found liquid sources
        //QuarryPlus.LOGGER.info("[Pump] Starting to remove {} water", liquidsToRemove());
        while(!liquidCoords.isEmpty()) {
            if(!PowerManager.useEnergyPump(powerTile, this.unbreaking, 1, 0)) {
                return;
            }
            BlockPos liquidPos = longToBlockPos(this.getPos(), liquidCoords.removeLong(0)); //remove 1st element
            removeLiquidAndSetAt(liquidPos, Blocks.AIR.getDefaultState());
        }
        //QuarryPlus.LOGGER.info("[Pump] Stopped to remove water. {} remaining.", liquidsToRemove());
    }

    /**
     * Drains liquid at specified position
     * @param liquidPos Position to drain
     * @param toSet IBlockState to place in the spot of the drained liquid
     */
    private void removeLiquidAndSetAt(BlockPos liquidPos, IBlockState toSet){
        IFluidHandler handler = FluidUtil.getFluidHandler(getWorld(), liquidPos, EnumFacing.UP);
        if (handler != null) {
            FluidStack stack = handler.drain(Fluid.BUCKET_VOLUME, true);
            if (stack != null) {
                final int index = this.liquids.indexOf(stack);
                if (index != -1)
                    this.liquids.get(index).amount += stack.amount;
                else
                    this.liquids.add(stack);
            }
            getWorld().setBlockState(liquidPos, toSet);
        }
    }

    /**
     * @return The number of liquid blocks left to remove
     */
    int liquidsToRemove(){
        return this.liquidCoords.size();
    }

    /**
     * @return The number of liquid blocks left to dam
     */
    int dammingRemaining(){
        return this.borderCoords.size();
    }

    /**
     * Checks if a given BlockPos is inside of the given x/z boundaries
     * @param pos BlockPos to check for
     * @param minX Lower X bound
     * @param maxX Upper X bound
     * @param minZ Lower Z bound
     * @param maxZ Upper Z bound
     * @return
     */
    private static boolean isInXZBounds(BlockPos pos, int minX, int maxX, int minZ, int maxZ){
        return minX < pos.getX() && maxX > pos.getX() && minZ < pos.getZ() && maxZ > pos.getZ();
    }

    /**
     * Gets the blockstate at a BlockPos, depending on world type
     * @param targetPos The BlockPos from where the state is required
     * @return the IBlockState at the BlockPos
     */
    private IBlockState getBlockStateAt(BlockPos targetPos){
        IBlockState targetState;
        if(((ICubicWorld)getWorld()).isCubicWorld()){
            //Get the cube and populate if neccessary
            ICubicWorldServer serverWorld = (ICubicWorldServer)getWorld();
            ICubeProviderServer cubeCache = serverWorld.getCubeCache();
            CubePos cubePos = serverWorld.getCubeFromBlockCoords(targetPos).getCoords();
            ICube cube = cubeCache.getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ICubeProviderServer.Requirement.POPULATE);
            targetState = cube.getBlockState(targetPos);
        }else{
            //Vanilla way to the blockstate (see above)
            Chunk targetChunk = getWorld().getChunkProvider().getLoadedChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4);
            if (targetChunk != null) {
                targetState = targetChunk.getBlockState(targetPos);
            } else {
                targetState = getWorld().getBlockState(targetPos);
            }
        }
        return targetState;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param state      BlockState
     * @param findSource if true, return whether you can drain the liquid.
     * @param world      When source is false, it can be null.
     * @param pos        When source is false, it can be any value.
     * @return true if the blockstate is liquid state.
     */
    public static boolean isLiquid(@Nonnull final IBlockState state, final boolean findSource, final World world, final BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof IFluidBlock)
            return !findSource || ((IFluidBlock) block).canDrain(world, pos);
        else {
            return (block == Blocks.WATER || block == Blocks.FLOWING_WATER || block == Blocks.LAVA || block == Blocks.FLOWING_LAVA)
                && (!findSource || state.getValue(BlockLiquid.LEVEL) == 0);
        }
    }

    public static boolean isLiquid(@Nonnull IBlockState state) {
        return isLiquid(state, false, null, null);
    }

    @Override
    protected Symbol getSymbol() {
        return Symbol.apply("PumpPlus");
    }

    private class PumpTank extends FluidTank {
        final EnumFacing facing;

        private PumpTank(EnumFacing facing) {
            super(Integer.MAX_VALUE);
            this.facing = facing;
            setCanFill(false);
            setTileEntity(TilePump.this);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) {
                return null;
            }
            final int index = liquids.indexOf(resource);
            if (index == -1)
                return null;
            final FluidStack fs = liquids.get(index);
            if (fs == null)
                return null;

            int drained = Math.min(fs.amount, resource.amount);
            final FluidStack ret = new FluidStack(fs, drained);
            if (doDrain) {
                fs.amount -= ret.amount;
                if (fs.amount <= 0) {
                    liquids.remove(fs);
                }
                onContentsChanged();
                FluidEvent.fireEvent(new FluidEvent.FluidDrainingEvent(fs.amount <= 0 ? null : fs, getWorld(), getPos(), this, drained));
            }
            return ret;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            final LinkedList<FluidTankProperties> ret = new LinkedList<>();
            if (mapping.get(facing).isEmpty()) {
                if (liquids.isEmpty())
                    for (Fluid fluid : FluidRegistry.getRegisteredFluids().values())
                        ret.add(new FluidTankProperties(new FluidStack(fluid, 0), Integer.MAX_VALUE, false, true));
                else
                    for (final FluidStack fs : liquids)
                        ret.add(new FluidTankProperties(fs, Integer.MAX_VALUE, false, true));
            } else {
                for (final String s : mapping.get(facing)) {
                    Optional.ofNullable(FluidRegistry.getFluidStack(s, 0)).ifPresent(fluidStack -> {
                        int index = liquids.indexOf(fluidStack);
                        if (index != -1)
                            ret.add(new FluidTankProperties(liquids.get(index), Integer.MAX_VALUE, false, true));
                        else
                            ret.add(new FluidTankProperties(fluidStack, Integer.MAX_VALUE, false, true));
                    });
                }
            }
            return ret.toArray(new FluidTankProperties[0]);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (mapping.get(facing).isEmpty()) {
                return liquids.isEmpty() ? null : drainI(0, maxDrain, doDrain);
            }
            int index;
            FluidStack fs;
            for (final String s : mapping.get(facing)) {
                fs = FluidRegistry.getFluidStack(s, maxDrain);
                if (fs != null) {
                    index = liquids.indexOf(fs);
                    if (index != -1) {
                        return drainI(index, maxDrain, doDrain);
                    }
                }
            }
            return null;
        }

        private FluidStack drainI(int index, int maxDrain, boolean doDrain) {
            FluidStack stack = liquids.get(index);
            int drained = Math.min(maxDrain, stack.amount);
            FluidStack ret = new FluidStack(stack, drained);
            if (doDrain) {
                stack.amount -= drained;
                if (stack.amount <= 0) {
                    liquids.remove(index);
                }
                onContentsChanged();
                FluidEvent.fireEvent(new FluidEvent.FluidDrainingEvent(stack.amount <= 0 ? null : stack, getWorld(), getPos(), this, drained));
            }
            return ret;
        }
    }

    public List<ITextComponent> C_getNames() {
        if (!liquids.isEmpty()) {
            List<ITextComponent> list = new ArrayList<>(liquids.size() + 1);
            list.add(new TextComponentTranslation(TranslationKeys.PUMP_CONTAIN));
            liquids.forEach(s -> list.add(new TextComponentTranslation(TranslationKeys.LIQUID_FORMAT,
                new TextComponentTranslation(s.getUnlocalizedName()), Integer.toString(s.amount))));
            return list;
        } else {
            return Collections.singletonList(new TextComponentTranslation(TranslationKeys.PUMP_CONTAIN_NO));
        }
    }

    @Override
    public List<ITextComponent> getDebugmessages() {
        ArrayList<ITextComponent> list = new ArrayList<>();
        list.add(toComponentString.apply("Connection : " + this.connectTo));
        for (EnumFacing facing : EnumFacing.VALUES) {
            this.mapping.get(facing).stream()
                .reduce(combiner).map(toComponentString)
                .ifPresent(list::add);
        }
        if (!liquids.isEmpty()) {
            list.add(new TextComponentTranslation(TranslationKeys.PUMP_CONTAIN));
            liquids.stream().map(fluidStack -> fluidStack.getLocalizedName() + fluidStack.amount + "mB")
                .reduce(combiner).map(toComponentString)
                .ifPresent(list::add);
        } else {
            list.add(new TextComponentTranslation(TranslationKeys.PUMP_CONTAIN_NO));
        }
        return list;
    }

    @Override
    public String getDebugName() {
        return TranslationKeys.pump;
    }

    @Override
    public ImmutableMap<Integer, Integer> getEnchantments() {
        ImmutableMap.Builder<Integer, Integer> builder = ImmutableMap.builder();
        if (this.fortune > 0)
            builder.put(FortuneID, (int) this.fortune);
        if (this.unbreaking > 0)
            builder.put(UnbreakingID, (int) this.unbreaking);
        if (this.silktouch)
            builder.put(SilktouchID, 1);
        return builder.build();
    }

    @Override
    public void setEnchantent(final short id, final short val) {
        if (id == FortuneID)
            this.fortune = (byte) val;
        else if (id == UnbreakingID)
            this.unbreaking = (byte) val;
        else if (id == SilktouchID)
            this.silktouch = val > 0;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    private final IFluidHandler tankAll = (IDummyFluidHandler) () -> {
        IFluidTankProperties[] array = TilePump.this.liquids.stream()
            .map(fluidStack -> new FluidTankProperties(fluidStack, fluidStack.amount, false, false))
            .toArray(IFluidTankProperties[]::new);
        return array.length == 0 ? IDummyFluidHandler.emptyPropertyArray : array;
    };

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(facing == null ? tankAll : tankMap.get(facing));
        } else {
            return super.getCapability(capability, facing);
        }
    }
}
