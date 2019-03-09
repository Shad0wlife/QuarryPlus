package com.yogpc.qp.packet.quarry;

import com.yogpc.qp.Config;
import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.packet.IMessage;
import com.yogpc.qp.packet.PacketHandler;
import com.yogpc.qp.tile.TileQuarry;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * To client only.
 */
public class MoveHead implements IMessage {
    int dim;
    BlockPos pos;
    double headPosX;
    double headPosY;
    double headPosZ;

    public static MoveHead create(TileQuarry quarry) {
        MoveHead message = new MoveHead();
        message.dim = quarry.getWorld().provider.getDimension();
        message.pos = quarry.getPos();
        message.headPosX = quarry.headPosX;
        message.headPosY = quarry.headPosY;
        message.headPosZ = quarry.headPosZ;
        return message;
    }

    public static void send(TileQuarry quarry) {
        if (!Config.content().disableRendering()) {
            PacketHandler.sendToAround(create(quarry), quarry.getWorld(), quarry.getPos());
        }
    }

    @Override
    public void fromBytes(PacketBuffer buffer) {
        pos = buffer.readBlockPos();
        dim = buffer.readInt();
        headPosX = buffer.readDouble();
        headPosY = buffer.readDouble();
        headPosZ = buffer.readDouble();
    }

    @Override
    public void toBytes(PacketBuffer buffer) {
        buffer.writeBlockPos(pos).writeInt(dim);
        buffer.writeDouble(headPosX).writeDouble(headPosY).writeDouble(headPosZ);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onReceive(IMessage message, MessageContext ctx) {
        World world = QuarryPlus.proxy.getPacketWorld(ctx.netHandler);
        if (world.provider.getDimension() == dim) {
            TileEntity entity = world.getTileEntity(pos);
            if (entity instanceof TileQuarry) {
                TileQuarry quarry = (TileQuarry) entity;
                quarry.headPosX = headPosX;
                quarry.headPosY = headPosY;
                quarry.headPosZ = headPosZ;
            }
        }
        return null;
    }
}
