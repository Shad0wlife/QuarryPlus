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

package com.yogpc.qp.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.ReflectionHelper;
import com.yogpc.qp.compat.BuildCraftHelper;
import com.yogpc.qp.compat.EnchantmentHelper;
import com.yogpc.qp.compat.InvUtils;
import com.yogpc.qp.item.ItemBlockBreaker;
import com.yogpc.qp.item.ItemTool;
import com.yogpc.qp.tile.IEnchantableTile;
import com.yogpc.qp.tile.TileBasic;
import com.yogpc.qp.tile.TileBreaker;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

public class BlockBreaker extends ADismCBlock {

    private final ArrayList<ItemStack> drops = new ArrayList<>();

    public BlockBreaker() {
        super(Material.ROCK, QuarryPlus.Names.breaker, ItemBlockBreaker::new);
        setHardness(3.5F);
        setSoundType(SoundType.STONE);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(POWERED, false));
        //Random tick setting is Config.
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
        super.updateTick(worldIn, pos, state, rand);
        if (!worldIn.isRemote) {
            EnumFacing facing = state.getValue(FACING);
            BlockPos pos1 = pos.offset(facing);
            if (pos1.getY() < 1)
                return;
            IBlockState blockState = worldIn.getBlockState(pos1);
            if (blockState.getBlock().isAir(blockState, worldIn, pos1))
                return;
            final EntityPlayer player = FakePlayerFactory.getMinecraft((WorldServer) worldIn);
            blockState.getBlock().onBlockHarvested(worldIn, pos1, blockState, player);
            if (blockState.getBlock().removedByPlayer(blockState, worldIn, pos1, player, true)) {
                blockState.getBlock().onBlockDestroyedByPlayer(worldIn, pos1, blockState);
            } else {
                return;
            }
            final TileBreaker tile = (TileBreaker) worldIn.getTileEntity(pos);
            List<ItemStack> stackList;
            if (tile.silktouch && blockState.getBlock().canSilkHarvest(worldIn, pos1, blockState, player)) {
                stackList = Collections.singletonList((ItemStack) ReflectionHelper.invoke(TileBasic.createStackedBlock, blockState.getBlock(), blockState));
            } else {
                stackList = blockState.getBlock().getDrops(worldIn, pos1, blockState, tile.fortune);
            }
            for (final ItemStack is : stackList) {
                ItemStack inserted = InvUtils.injectToNearTile(worldIn, pos, is);
                if (!inserted.isEmpty()) {
                    InventoryHelper.spawnItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), is);
                }
            }
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, POWERED);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!worldIn.isRemote) {
            worldIn.setBlockState(pos, state.withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer)), 2);
            EnchantmentHelper.init((IEnchantableTile) worldIn.getTileEntity(pos), stack.getEnchantmentTagList());
        }
    }

    @Override
    public boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return base_state.getValue(FACING) != side;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
        boolean flag = worldIn.isBlockPowered(pos);

        if (flag != state.getValue(POWERED)) {
            state = state.withProperty(POWERED, flag);
            if (flag)
                updateTick(worldIn, pos, state, worldIn.rand);
            TileEntity entity = worldIn.getTileEntity(pos);
            assert entity != null;
            entity.validate();
            worldIn.setBlockState(pos, state.withProperty(POWERED, flag), 3);
            entity.validate();
            worldIn.setTileEntity(pos, entity);
        }
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        return drops;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        final TileBreaker tile = (TileBreaker) worldIn.getTileEntity(pos);
        this.drops.clear();
        final int count = quantityDropped(state, 0, worldIn.rand);
        ItemStack is;
        for (int i = 0; i < count; i++) {
            final Item id1 = getItemDropped(state, worldIn.rand, 0);
            is = new ItemStack(id1, 1, damageDropped(state));
            EnchantmentHelper.enchantmentToIS(tile, is);
            this.drops.add(is);
        }
        super.breakBlock(worldIn, pos, state);
    }

    @Nullable
    @Override
    public TileBreaker createNewTileEntity(World worldIn, int meta) {
        return new TileBreaker();
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = playerIn.getHeldItem(hand);
        if (BuildCraftHelper.isWrench(playerIn, hand, stack, new RayTraceResult(new Vec3d(hitX, hitY, hitZ), facing, pos))) {
            TileEntity entity = worldIn.getTileEntity(pos);
            assert entity != null;
            entity.validate();
            worldIn.setBlockState(pos, state.cycleProperty(FACING), 3);
            entity.validate();
            worldIn.setTileEntity(pos, entity);
            return true;
        }
        if (stack.getItem() instanceof ItemTool && stack.getItemDamage() == 0) {
            if (!worldIn.isRemote) {
                EnchantmentHelper.getEnchantmentsChat((IEnchantableTile) worldIn.getTileEntity(pos)).forEach(playerIn::sendMessage);
            }
            return true;
        }
        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        boolean powered = state.getValue(POWERED);
        EnumFacing facing = state.getValue(FACING);
        return facing.getIndex() | (powered ? 8 : 0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta & 7)).withProperty(POWERED, (meta & 8) == 8);
    }
}