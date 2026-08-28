package com.floatingtext.item;

import com.floatingtext.FloatingTextMod;
import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 悬浮文字工具, 右键方块面贴墙放 右键空气悬浮放
// 生成实体的逻辑只在服务端跑 保证多人模式下数据一致
public class FloatingTextToolItem extends Item {

    public FloatingTextToolItem() {
        // 只能拿一个 工具不会被消耗
        super(new Item.Properties().stacksTo(1));
    }

    // 右键方块面 文字放在面中心 沿法线外移一点贴住表面
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            // 记一下放置时间 新实体进客户端世界后好自动弹编辑界面
            FloatingTextMod.lastClientPlaceTime = System.currentTimeMillis();
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // 面中心是方块中心加 0.5 再沿法线外移 0.01 格, 配合 POLYGON_OFFSET 不会嵌进方块
        Direction face = context.getClickedFace();
        BlockPos blockPos = context.getClickedPos();
        double x = blockPos.getX() + 0.5 + face.getStepX() * 0.51;
        double y = blockPos.getY() + 0.5 + face.getStepY() * 0.51;
        double z = blockPos.getZ() + 0.5 + face.getStepZ() * 0.51;
        // 顶面躺平朝上 底面躺平朝下 侧面竖着
        float pitch = 0.0F;
        if (face == Direction.UP) {
            pitch = -90.0F;
        } else if (face == Direction.DOWN) {
            pitch = 90.0F;
        }
        spawnText(level, player, x, y, z, pitch);
        return InteractionResult.SUCCESS;
    }

    // 右键空气, 面前 3 格放字 位置被方块挡住就沿视线往外找空气位置
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            FloatingTextMod.lastClientPlaceTime = System.currentTimeMillis();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(3.0));
        if (!level.getBlockState(BlockPos.containing(pos)).isAir()) {
            // 目标在方块里 往外找第一个空气位置
            boolean found = false;
            for (int i = 1; i <= 10; i++) {
                Vec3 candidate = player.getEyePosition().add(player.getLookAngle().scale(3.0 + i * 0.5));
                if (level.getBlockState(BlockPos.containing(candidate)).isAir()) {
                    pos = candidate;
                    found = true;
                    break;
                }
            }
            if (!found) {
                player.displayClientMessage(Component.translatable("message.floatingtext.blocked"), false);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
        }
        spawnText(level, player, pos.x, pos.y, pos.z, 0.0F);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // 生成文字实体 朝向吸附到最近的 90 度 要更精细的角度在编辑界面里调旋转
    private void spawnText(Level level, Player player, double x, double y, double z, float pitch) {
        float snappedYaw = Math.round(player.getYRot() / 90.0F) * 90.0F;
        FloatingTextEntity entity = new FloatingTextEntity(level, x, y, z, snappedYaw, player.getUUID());
        entity.setXRot(pitch); // 顶面底面用 xRot 躺平 这个字段会自动保存同步
        level.addFreshEntity(entity);
    }
}
