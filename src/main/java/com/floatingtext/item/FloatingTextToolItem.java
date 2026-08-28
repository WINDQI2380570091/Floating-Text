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

/**
 * 悬浮文字工具：用来在世界中放置文字。
 * <ul>
 *   <li>右键方块面 → 文字贴在方块表面上（贴墙/贴地/贴天花板）</li>
 *   <li>右键空气 → 文字悬浮在玩家面前 3 格处</li>
 * </ul>
 * 文字实体只由服务端生成，客户端不做任何生成逻辑，保证多人模式下数据一致。
 */
public class FloatingTextToolItem extends Item {

    public FloatingTextToolItem() {
        // 每格只能放 1 个（工具可重复使用，不会被消耗）
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 右键方块面：在点击的位置生成文字实体，并沿所点面的法线方向外移。
     * <p>
     * 外移量说明：文字中心放在面中心再沿法线外移 0.01 格，几乎贴着表面；
     * 渲染器使用 POLYGON_OFFSET 深度偏移，文字紧贴表面也不会被方块遮挡。
     * （之前用 0.05 格外移且无深度偏移，文字被深度测试判成在方块后面，
     *  看起来嵌进方块里、颜色变暗，已由 POLYGON_OFFSET 解决。）
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            // 记录"刚放置过"的时刻：新文字实体加入客户端世界后，会自动弹出编辑界面
            FloatingTextMod.lastClientPlaceTime = System.currentTimeMillis();
            return InteractionResult.SUCCESS; // 生成实体只由服务端执行
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS; // 没有玩家（比如被发射器使用）就不处理
        }
        // 文字固定在"被点击方块面的中心"，紧贴方块表面：
        // 面中心 = 方块中心(坐标+0.5) + 法线×0.5，再沿法线外移 0.01 格（几乎贴面）。
        // 渲染器使用 POLYGON_OFFSET 模式（深度偏移），文字紧贴表面也不会被方块遮挡，
        // 也不会闪烁（之前的"嵌进方块"问题由它解决）。
        Direction face = context.getClickedFace();
        BlockPos blockPos = context.getClickedPos();
        double x = blockPos.getX() + 0.5 + face.getStepX() * 0.51;
        double y = blockPos.getY() + 0.5 + face.getStepY() * 0.51;
        double z = blockPos.getZ() + 0.5 + face.getStepZ() * 0.51;
        // 顶面：文字躺平朝上；底面：文字躺平朝下；侧面：文字竖直
        float pitch = 0.0F;
        if (face == Direction.UP) {
            pitch = -90.0F;
        } else if (face == Direction.DOWN) {
            pitch = 90.0F;
        }
        spawnText(level, player, x, y, z, pitch);
        return InteractionResult.SUCCESS;
    }

    /**
     * 右键空气：在玩家面前 3 格处生成悬浮文字。
     * 目标位置如果被方块挡住（比如面前紧贴墙壁），沿视线向外找最近的空气位置，
     * 避免文字生成在方块内部看不见也点不到。
     * （注意：1.20.1 的 Item.use 返回 InteractionResultHolder<ItemStack>，不是 InteractionResult。）
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 记录"刚放置过"的时刻：新文字实体加入客户端世界后，会自动弹出编辑界面
            FloatingTextMod.lastClientPlaceTime = System.currentTimeMillis();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(3.0));
        if (!level.getBlockState(BlockPos.containing(pos)).isAir()) {
            // 目标位置在方块里：沿视线方向逐步外移，找第一个空气位置
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

    /** 生成文字实体（只在服务端调用）。 */
    private void spawnText(Level level, Player player, double x, double y, double z, float pitch) {
        // 朝向 = 玩家水平朝向吸附到最近的 90° 倍数（0/90/180/270），
        // 保证贴墙放置时文字方方正正。需要更精细的倾斜角时在编辑界面里调"旋转"。
        float snappedYaw = Math.round(player.getYRot() / 90.0F) * 90.0F;
        FloatingTextEntity entity = new FloatingTextEntity(level, x, y, z, snappedYaw, player.getUUID());
        entity.setXRot(pitch); // 顶面/底面时让文字躺平（xRot 是实体基础字段，自动保存和同步）
        level.addFreshEntity(entity);
    }
}
