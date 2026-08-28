package com.floatingtext;

import com.floatingtext.network.SyncFloatingTextPacket;
import com.floatingtext.network.T72AVBoomPacket;
import com.floatingtext.network.UpdateFloatingTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// 网络通道, 客户端和服务端传数据包用
public class ModNetwork {

    // 协议版本, 改过数据包格式就升版本 老客户端会被拒绝连接
    private static final String PROTOCOL_VERSION = "2";

    // 专属频道 floatingtext:main
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FloatingTextMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    // 注册数据包 编号从 0 开始
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, UpdateFloatingTextPacket.class,
                UpdateFloatingTextPacket::encode,
                UpdateFloatingTextPacket::decode,
                UpdateFloatingTextPacket::handle);
        CHANNEL.registerMessage(id++, SyncFloatingTextPacket.class,
                SyncFloatingTextPacket::encode,
                SyncFloatingTextPacket::decode,
                SyncFloatingTextPacket::handle);
        CHANNEL.registerMessage(id++, T72AVBoomPacket.class,
                T72AVBoomPacket::encode,
                T72AVBoomPacket::decode,
                T72AVBoomPacket::handle);
    }
}
