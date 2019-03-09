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

import java.util.Map;
import java.util.Optional;

import com.yogpc.qp.PowerManager;
import com.yogpc.qp.QuarryPlusI;
import com.yogpc.qp.block.BlockMiningWell;
import com.yogpc.qp.compat.InvUtils;
import com.yogpc.qp.gui.TranslationKeys;
import com.yogpc.qp.packet.PacketHandler;
import com.yogpc.qp.packet.TileMessage;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import scala.Symbol;

import static jp.t2v.lab.syntax.MapStreamSyntax.byEntry;
import static jp.t2v.lab.syntax.MapStreamSyntax.entryToMap;

public class TileMiningWell extends TileBasic implements ITickable {
    public static final scala.Symbol SYMBOL = scala.Symbol.apply("MiningwellPlus");

    private boolean working;

    @Override
    public void G_renew_powerConfigure() {
        byte pmp = 0;
        if (hasWorld()) {
            Map<IAttachment.Attachments<?>, EnumFacing> map = facingMap.entrySet().stream()
                .filter(byEntry((attachments, facing) -> attachments.test(getWorld().getTileEntity(getPos().offset(facing)))))
                .collect(entryToMap());
            facingMap.putAll(map);
            pmp = Optional.ofNullable(facingMap.get(IAttachment.Attachments.FLUID_PUMP))
                .map(getPos()::offset)
                .map(getWorld()::getTileEntity)
                .flatMap(IAttachment.Attachments.FLUID_PUMP)
                .map(p -> p.unbreaking).orElse((byte) 0);
        }

        if (this.working)
            PowerManager.configureMiningWell(this, this.efficiency, this.unbreaking, pmp);
        else
            PowerManager.configure0(this);
    }

    @Override
    public String getName() {
        return TranslationKeys.miningwell;
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && !machineDisabled) {
            //Starting position: 1 block below Device
            int depth = getPos().getY() - 1;

            if (this.working) {
                //Find next block to mine and make sure the mining pipe is in place
                while (!S_checkTarget(depth)) {
                    getWorld().setBlockState(new BlockPos(getPos().getX(), depth, getPos().getZ()), QuarryPlusI.blockPlainPipe().getDefaultState());
                    //PUMP ONLY TODO testing
                    //Searches for damming positions around the mining pipe
                    //This is inefficient to do every time, but without it, the dam would only be built before minable layers
                    //Meaning that if the MW hits an air pocket and in the ring above were liquid, that wouldn't be dammed
                    if(this.pump != null) {
                        TileEntity te = getWorld().getTileEntity(getPos().offset(this.pump));
                        if (te instanceof TilePump) {
                            TilePump pumpTE = (TilePump) te;
                            pumpTE.discoverBorders(this, depth);
                        } else {
                            this.pump = null;
                        }
                    }
                    depth--;
                }

                //PUMP ONLY TODO testing
                //Dams all found damming position
                if(this.pump != null) {
                    TileEntity te = getWorld().getTileEntity(getPos().offset(this.pump));
                    if (te instanceof TilePump) {
                        TilePump pumpTE = (TilePump) te;
                        pumpTE.damBorders(this);
                    } else {
                        this.pump = null;
                    }
                }

                //Proceed to break next target block
                S_breakBlock(getPos().getX(), depth, getPos().getZ());
            }
            S_pollItems();
        }
    }

    @Override
    protected boolean isWorking() {
        return working;
    }

    private boolean S_checkTarget(final int depth) {
        //TODO handle yLevel default
        //if (depth < ((ICubicWorld)getWorld()).getMinHeight() + 1) {
        if (depth < yLevel) {
            G_destroy();
            finishWork();
            return true;
        }
        BlockPos pos = new BlockPos(getPos().getX(), depth, getPos().getZ());
        //final IBlockState b = getWorld().getBlockState(pos); TODO
        final IBlockState b = getBlockStateAt(pos);
        final float h = b.getBlockHardness(getWorld(), pos);
        if (h < 0 || b.getBlock() == QuarryPlusI.blockPlainPipe() || b.getBlock().isAir(b, getWorld(), pos)) {
            return false;
        }
        if (!facingMap.containsKey(IAttachment.Attachments.FLUID_PUMP) && b.getMaterial().isLiquid())
            return false;
        if (!this.working) {
            //Find something to break!
            G_ReInit();
        }
        return true;
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setWorking(nbt.getBoolean("working"));
        G_renew_powerConfigure();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound nbt) {
        nbt.setBoolean("working", this.working);
        return super.writeToNBT(nbt);
    }

    @Override
    public void G_ReInit() {
        setWorking(true);
        G_renew_powerConfigure();
        if (!getWorld().isRemote)
            PacketHandler.sendToAround(TileMessage.create(this), getWorld(), getPos());
    }

    @Override
    protected void G_destroy() {
        if (!getWorld().isRemote) {
            working = false; //TODO method cause loop. -> check.
            G_renew_powerConfigure();
            PacketHandler.sendToAround(TileMessage.create(this), getWorld(), getPos());
            removePipes();
        }
    }

    public void removePipes() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(getPos());
        for (int depth = getPos().getY() - 1; depth > 0; depth--) {
            pos.setY(depth);
            if (getWorld().getBlockState(pos).getBlock() != QuarryPlusI.blockPlainPipe())
                break;
            getWorld().setBlockToAir(pos);
        }
    }

    private void setWorking(boolean working) {
        this.working = working;
        if (working)
            startWork();
        if (hasWorld()) {
            IBlockState old = getWorld().getBlockState(getPos());
            InvUtils.setNewState(getWorld(), getPos(), this, old.withProperty(BlockMiningWell.ACTING, working));
        }
    }

    @Override
    protected Symbol getSymbol() {
        return SYMBOL;
    }
}
