package com.floatingtext.network;

import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// 服务端发客户端的全量同步包
// 实体的 SynchedEntityData 只有新值不等于默认值才同步, 偏移改回 0 颜色改回白这种改动
// 客户端收不到 所以保存后单独发这个包把完整数据发给所有人
public class SyncFloatingTextPacket {

    private final int entityId;
    private final CompoundTag data;

    public SyncFloatingTextPacket(int entityId, CompoundTag data) {
        this.entityId = entityId;
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(SyncFloatingTextPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeNbt(msg.data);
    }

    public static SyncFloatingTextPacket decode(FriendlyByteBuf buf) {
        return new SyncFloatingTextPacket(buf.readInt(), buf.readAnySizeNbt());
    }

    public static void handle(SyncFloatingTextPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientApply(msg)));
        context.setPacketHandled(true);
    }

    // 客户端线程里把数据写进本地实体 服务端不会走到这
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
