package com.floatingtext.network;

import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 数据包：服务端 → 客户端（一条悬浮文字的完整数据，全量同步）。
 * <p>
 * 为什么要单独发这个包：
 * 实体的 SynchedEntityData 只在"新值 != 默认值"时才会标记脏并同步。
 * 如果玩家把偏移改回 0.00（等于默认值）、或颜色改回白色（等于默认值），
 * 服务端数据变了但客户端收不到，导致"面板数字复原、改了没反应"。
 * 这个包绕过脏标记机制，保存后立即把完整数据发给所有玩家，保证客户端永远和服务端一致。
 * <p>
 * 数据同样用 CompoundTag 按键名读写，不依赖字段顺序。
 */
public class SyncFloatingTextPacket {

    private final int entityId;
    private final CompoundTag data;

    public SyncFloatingTextPacket(int entityId, CompoundTag data) {
        this.entityId = entityId;
        this.data = data == null ? new CompoundTag() : data;
    }

    /** 发送方（服务端）：把数据写进网络流。 */
    public static void encode(SyncFloatingTextPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeNbt(msg.data);
    }

    /** 接收方（客户端）：从网络流读出数据。 */
    public static SyncFloatingTextPacket decode(FriendlyByteBuf buf) {
        return new SyncFloatingTextPacket(buf.readInt(), buf.readAnySizeNbt());
    }

    /**
     * 客户端处理：把收到的数据写进本地实体。
     * 用 DistExecutor 包一层，保证服务端永远不会加载 Minecraft 客户端类。
     */
    public static void handle(SyncFloatingTextPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientApply(msg)));
        context.setPacketHandled(true);
    }

    /** 客户端线程执行：更新本地实体数据（服务端不会调用这个方法）。 */
    private static void clientApply(SyncFloatingTextPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.level.getEntity(msg.entityId) instanceof FloatingTextEntity entity) {
            entity.applySyncTag(msg.data);
        }
    }
}
