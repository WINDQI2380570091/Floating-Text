package com.floatingtext.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 数据包：客户端 → 服务端（T-72AV 彩蛋的爆炸请求）。
 * <p>
 * 客户端在"游戏语言是阿拉伯语"时右键 T-72AV 发送此包；
 * 服务端收到后在发送者位置生成一次 TNT 爆炸（半径 4 格，与 TNT 一致，
 * 破坏方块、伤害实体）。语言检测在客户端做，服务端只负责执行爆炸。
 */
public class T72AVBoomPacket {

    public T72AVBoomPacket() {
    }

    /** 发送方：无数据可写。 */
    public static void encode(T72AVBoomPacket msg, FriendlyByteBuf buf) {
    }

    /** 接收方：无数据可读。 */
    public static T72AVBoomPacket decode(FriendlyByteBuf buf) {
        return new T72AVBoomPacket();
    }

    /** 服务端处理：在玩家位置生成 TNT 爆炸。 */
    public static void handle(T72AVBoomPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return; // 不是玩家发来的，忽略
            }
            // TNT 爆炸：半径 4.0F，与 TNT 完全一致
            sender.level().explode(sender,
                    sender.getX(), sender.getY(), sender.getZ(),
                    4.0F, Level.ExplosionInteraction.TNT);
        });
        context.setPacketHandled(true);
    }
}
