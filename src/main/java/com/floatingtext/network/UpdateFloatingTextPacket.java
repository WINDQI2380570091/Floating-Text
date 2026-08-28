package com.floatingtext.network;

import com.floatingtext.FloatingTextMod;
import com.floatingtext.ModNetwork;
import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// 客户端发服务端的编辑或删除请求
// 文字数据放 CompoundTag 里按键名读写 不会因为字段顺序错位
public class UpdateFloatingTextPacket {

    private final int entityId;
    private final boolean delete;
    private final CompoundTag data;

    public UpdateFloatingTextPacket(int entityId, boolean delete, CompoundTag data) {
        this.entityId = entityId;
        this.delete = delete;
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(UpdateFloatingTextPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.delete);
        buf.writeNbt(msg.data);
    }

    public static UpdateFloatingTextPacket decode(FriendlyByteBuf buf) {
        return new UpdateFloatingTextPacket(buf.readInt(), buf.readBoolean(), buf.readAnySizeNbt());
    }

    public static void handle(UpdateFloatingTextPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ServerLevel serverLevel = sender.serverLevel();
            if (!(serverLevel.getEntity(msg.entityId) instanceof FloatingTextEntity entity)) {
                return; // 实体没了 可能已经被删
            }
            // 得在同一个维度 而且离文字 8 格内 防止恶意客户端远程乱改
            if (entity.level() != serverLevel) {
                return;
            }
            if (sender.distanceToSqr(entity) > 64.0D) {
                return;
            }
            // 只有创建者能改
            if (!entity.canEdit(sender)) {
                return;
            }
            if (msg.delete) {
                entity.discard();
                return;
            }
            entity.applySyncTag(msg.data);

            // 打印一下收到的数据 方便排查问题
            FloatingTextMod.LOGGER.info(String.format(
                    "[FloatingText] 更新文字 id=%d text=\"%s\" color=%08X scale=%.3f offset=(%.3f,%.3f,%.3f) rotation=%.2f",
                    entity.getId(), entity.getText(), entity.getColor(), entity.getScale(),
                    entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ(), entity.getRotation()));

            // 改完广播全量数据 不然偏移改回默认值之类的改动客户端收不到
            SyncFloatingTextPacket sync = new SyncFloatingTextPacket(entity.getId(), entity.toSyncTag());
            for (ServerPlayer player : serverLevel.players()) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), sync);
            }
        });
        context.setPacketHandled(true);
    }
}
