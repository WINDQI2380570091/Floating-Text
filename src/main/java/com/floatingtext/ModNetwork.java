package com.floatingtext;

import com.floatingtext.network.SyncFloatingTextPacket;
import com.floatingtext.network.T72AVBoomPacket;
import com.floatingtext.network.UpdateFloatingTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道：负责客户端和服务端之间传数据包。
 * <p>
 * 本模组只有一个数据包（编辑/删除文字），使用 Forge 1.20.1 自带的 SimpleChannel，
 * 不会影响其他模组的网络，也不依赖任何第三方库。
 */
public class ModNetwork {

    /** 协议版本：数据包格式改成了 CompoundTag 按键名读写（v2），和旧版不兼容。 */
    private static final String PROTOCOL_VERSION = "2";

    /** 本模组专属的频道：floatingtext:main。 */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FloatingTextMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    /** 在模组加载时调用：注册数据包（编号从 0 开始）。 */
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
