package com.floatingtext.client;

import com.floatingtext.FloatingTextMod;
import com.floatingtext.entity.FloatingTextEntity;
import com.floatingtext.gui.FloatingTextScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 纯客户端辅助类：只会在客户端被加载（服务端永远不会用到它）。
 * 功能：
 * 1. 打开悬浮文字编辑界面；
 * 2. 放置文字后自动弹出编辑界面（延迟到数据同步完成再弹，避免输入框显示默认值）。
 */
public class FloatingTextClient {

    /**
     * 等待自动弹窗的实体 -> 允许弹窗的时刻（毫秒）。
     * 用 WeakHashMap 且 key 是实体对象（不是实体 ID）：
     * - 实体从世界移除后，key 失去强引用，条目会被 GC 自动清理，不会长期占内存；
     * - 换服务器/服务器重启后实体 ID 会从 0 重新分配，按对象区分不会误判
     *   同一个 ID 的新文字是"见过的旧文字"。
     */
    private static final Map<FloatingTextEntity, Long> PENDING_AUTO_OPEN = new WeakHashMap<>();

    /**
     * 放置后等待这么久再弹窗（毫秒）。
     * 实体刚加入客户端时，它的同步数据（SynchedEntityData）还没从服务器传过来，
     * 太早弹窗的话输入框会显示默认值（文字框为空），用户直接点保存会把文字清空。
     */
    private static final long AUTO_OPEN_DELAY_MS = 600L;

    /** 打开悬浮文字编辑界面（只在客户端调用）。 */
    public static void openScreen(FloatingTextEntity entity) {
        Minecraft.getInstance().setScreen(new FloatingTextScreen(entity));
    }

    /**
     * 实体加入客户端世界时触发（放置、加载区块、进入视野等都会触发）。
     * 如果是"玩家刚放置的文字"，登记进待弹窗列表（不立即弹窗）。
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return; // 只在客户端处理
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof FloatingTextEntity floatingText)) {
            return; // 不是本模组的文字，忽略
        }
        // 判断是否"刚放置"：3 秒内点击过工具
        long elapsed = System.currentTimeMillis() - FloatingTextMod.lastClientPlaceTime;
        if (elapsed > 3000 || elapsed < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 只对玩家身边（6 格内）新出现的文字弹窗，避免别人在远处放字时误弹
        if (floatingText.distanceToSqr(mc.player) > 36.0D) {
            return;
        }
        // 登记进待弹窗列表（putIfAbsent：同一实体只登记一次，防止重复弹窗）。
        // owner 和文字内容要等服务端同步数据到达后再校验，所以这里先不判断。
        PENDING_AUTO_OPEN.putIfAbsent(floatingText, System.currentTimeMillis() + AUTO_OPEN_DELAY_MS);
    }

    /**
     * 每帧检查待弹窗列表：到时间的实体，如果数据已同步（文字非空）、
     * 是玩家自己的文字、且当前没有打开其他界面，就弹出编辑界面。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (PENDING_AUTO_OPEN.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        // 当前开着其他界面（物品栏/聊天等）时不弹窗，等界面关闭后再弹，避免强占玩家屏幕
        if (mc.screen != null) {
            return;
        }
        Iterator<Map.Entry<FloatingTextEntity, Long>> it = PENDING_AUTO_OPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FloatingTextEntity, Long> entry = it.next();
            if (now < entry.getValue()) {
                continue; // 还没到时间
            }
            FloatingTextEntity floatingText = entry.getKey();
            // 实体已不在世界中（被服务端删除/移除），放弃弹窗（条目由弱引用自动清理）
            if (!floatingText.isAlive() || floatingText.level() != mc.level) {
                it.remove();
                continue;
            }
            // 数据已同步（文字内容非空）才弹窗，避免输入框显示默认值导致误保存
            if (floatingText.getText().isEmpty()) {
                it.remove(); // 数据一直没同步（如放置后立刻被服务端移除），放弃弹窗
                continue;
            }
            // 只给创建者自己弹窗：避免弹出别人的编辑界面
            if (!floatingText.canEdit(mc.player)) {
                it.remove();
                continue;
            }
            it.remove();
            mc.setScreen(new FloatingTextScreen(floatingText));
        }
    }
}
