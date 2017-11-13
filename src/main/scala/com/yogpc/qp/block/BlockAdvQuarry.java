package com.yogpc.qp.block;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.QuarryPlusI;
import com.yogpc.qp.compat.BuildCraftHelper;
import com.yogpc.qp.compat.EnchantmentHelper;
import com.yogpc.qp.compat.InvUtils;
import com.yogpc.qp.item.ItemBlockEnchantable;
import com.yogpc.qp.tile.TileAdvQuarry;
import javax.annotation.Nullable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAdvQuarry extends ADismCBlock {

    public BlockAdvQuarry() {
        super(Material.IRON, QuarryPlus.Names.advquarry, ItemBlockEnchantable::new);
        setHardness(1.5F);
        setResistance(10F);
        setSoundType(SoundType.STONE);
        setDefaultState(getBlockState().getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(ACTING, false));
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(World worldIn, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        worldIn.setBlockToAir(pos);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        TileEntity entity = world.getTileEntity(pos);
        if (TileAdvQuarry.class.isInstance(entity)) {
            TileAdvQuarry quarry = (TileAdvQuarry) entity;
            ItemStack stack = new ItemStack(QuarryPlusI.blockChunkdestroyer, 1, 0);
            EnchantmentHelper.enchantmentToIS(quarry, stack);
            drops.add(stack);
        }
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = playerIn.getHeldItem(hand);
        if (InvUtils.isDebugItem(playerIn, hand)) return true;
        if (BuildCraftHelper.isWrench(playerIn, hand, stack, new RayTraceResult(new Vec3d(hitX, hitY, hitZ), facing, pos))) {
            TileEntity t = worldIn.getTileEntity(pos);
            if (t != null) {
                ((TileAdvQuarry) t).G_reinit();
            }
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (!worldIn.isRemote) {
            EnumFacing facing = placer.getHorizontalFacing().getOpposite();
            worldIn.setBlockState(pos, state.withProperty(FACING, facing), 2);
            TileAdvQuarry quarry = (TileAdvQuarry) worldIn.getTileEntity(pos);
            assert quarry != null;
            quarry.requestTicket();
            EnchantmentHelper.init(quarry, stack.getEnchantmentTagList());
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, ACTING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        boolean powered = state.getValue(ACTING);
        EnumFacing facing = state.getValue(FACING);
        return facing.getIndex() | (powered ? 8 : 0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta & 7)).withProperty(ACTING, (meta & 8) == 8);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileAdvQuarry();
    }
}