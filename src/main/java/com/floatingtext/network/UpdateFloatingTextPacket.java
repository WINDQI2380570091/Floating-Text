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

/**
 * 数据包：客户端 → 服务端（编辑或删除一条悬浮文字）。
 * <p>
 * 流程：
 * 客户端在编辑界面点"保存/删除"时发送此包；
 * 服务端收到后先校验发送者是否为文字的主人，再应用修改；
 * 修改后的数据通过全量同步包广播给所有客户端。
 * <p>
 * 重要：文字数据用 CompoundTag 按键名读写（text/color/scale/offsetX/...），
 * 不依赖字段的读写顺序——即使网络流出现任何顺序问题也不会错位。
 */
public class UpdateFloatingTextPacket {

    private final int entityId;       // 要修改的文字实体的 ID
    private final boolean delete;     // true = 删除这条文字，false = 更新内容
    private final CompoundTag data;   // 文字数据（text/color/scale/offsetX/offsetY/offsetZ/rotation）

    public UpdateFloatingTextPacket(int entityId, boolean delete, CompoundTag data) {
        this.entityId = entityId;
        this.delete = delete;
        this.data = data == null ? new CompoundTag() : data;
    }

    /** 发送方：把数据包内容写进网络流。 */
    public static void encode(UpdateFloatingTextPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.delete);
        buf.writeNbt(msg.data);
    }

    /** 接收方：从网络流里读出数据包。 */
    public static UpdateFloatingTextPacket decode(FriendlyByteBuf buf) {
        return new UpdateFloatingTextPacket(buf.readInt(), buf.readBoolean(), buf.readAnySizeNbt());
    }

    /** 服务端处理（enqueueWork 保证在服务端主线程执行）。 */
    public static void handle(UpdateFloatingTextPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return; // 不是玩家发来的，忽略
            }
            // 用官方推荐的方法拿服务端世界（1.20.1 的 Entity.level 字段是私有的，不能直接访问）
            ServerLevel serverLevel = sender.serverLevel();
            if (!(serverLevel.getEntity(msg.entityId) instanceof FloatingTextEntity entity)) {
                return; // 实体不存在（可能已被删除）
            }
            // 实体必须和发送者在同一维度（getEntity 按服务器全局查，能查到其他维度的实体）
            if (entity.level() != serverLevel) {
                return;
            }
            // 距离校验：只能在文字附近（8 格内）修改，防止恶意客户端远程任意改字
            if (sender.distanceToSqr(entity) > 64.0D) {
                return;
            }
            // 权限校验：只有创建者可以修改；无主的旧文字任何人都能改
            if (!entity.canEdit(sender)) {
                return; // 不是主人，拒绝修改
            }
            if (msg.delete) {
                entity.discard(); // 删除：所有客户端会自动同步移除
                return;
            }
            // 应用修改：按键名从 tag 读取（不依赖字段顺序）
            entity.applySyncTag(msg.data);

            // 日志：确认服务端收到的数据（排查问题时看 latest.log）
            FloatingTextMod.LOGGER.info(String.format(
                    "[FloatingText] 更新文字 id=%d text=\"%s\" color=%08X scale=%.3f offset=(%.3f,%.3f,%.3f) rotation=%.2f",
                    entity.getId(), entity.getText(), entity.getColor(), entity.getScale(),
                    entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ(), entity.getRotation()));

            // 保存后立即把完整数据广播给所有玩家：
            // 实体的 SynchedEntityData 只在"新值 != 默认值"时同步，
            // 单独发这个全量包可以保证客户端永远和服务端一致（改回默认值也能同步）。
            SyncFloatingTextPacket sync = new SyncFloatingTextPacket(entity.getId(), entity.toSyncTag());
            for (ServerPlayer player : serverLevel.players()) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), sync);
            }
        });
        context.setPacketHandled(true);
    }
}
