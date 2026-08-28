package com.floatingtext.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// T-72AV 彩蛋的爆炸请求 客户端发 服务端执行爆炸
public class T72AVBoomPacket {

    public T72AVBoomPacket() {
    }

    public static void encode(T72AVBoomPacket msg, FriendlyByteBuf buf) {
    }

    public static T72AVBoomPacket decode(FriendlyByteBuf buf) {
        return new T72AVBoomPacket();
    }

    // 在玩家位置炸一下 半径 4 和 TNT 一样
    public static void handle(T72AVBoomPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            sender.level().explode(sender,
                    sender.getX(), sender.getY(), sender.getZ(),
                    4.0F, Level.ExplosionInteraction.TNT);
        });
        context.setPacketHandled(true);
    }
}
