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

// 纯客户端辅助类, 服务端永远不会加载
// 负责打开编辑界面和放置后自动弹窗
public class FloatingTextClient {

    // 待弹窗的实体和允许弹窗的时间
    // 用 WeakHashMap 键是实体对象, 实体移除后条目自动被回收 不会占内存
    // 不用实体 ID 当键是因为换服务器后 ID 会重新分配 容易误判成见过的
    private static final Map<FloatingTextEntity, Long> PENDING_AUTO_OPEN = new WeakHashMap<>();

    // 放置后等 600 毫秒再弹窗, 太早的话同步数据还没到 输入框显示默认值 点保存会清空文字
    private static final long AUTO_OPEN_DELAY_MS = 600L;

    // 打开编辑界面
    public static void openScreen(FloatingTextEntity entity) {
        Minecraft.getInstance().setScreen(new FloatingTextScreen(entity));
    }

    // 实体进客户端世界时触发 刚放的文字先登记进待弹窗列表 不立刻弹
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof FloatingTextEntity floatingText)) {
            return;
        }
        // 3 秒内点过工具才算刚放置
        long elapsed = System.currentTimeMillis() - FloatingTextMod.lastClientPlaceTime;
        if (elapsed > 3000 || elapsed < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 只弹自己身边 6 格内的 别人在远处放字不会误弹
        if (floatingText.distanceToSqr(mc.player) > 36.0D) {
            return;
        }
        PENDING_AUTO_OPEN.putIfAbsent(floatingText, System.currentTimeMillis() + AUTO_OPEN_DELAY_MS);
    }

    // 每帧检查待弹窗列表 到时间且数据同步好了就弹
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
        // 开着别的界面就不弹 等关掉再说 不抢屏幕
        if (mc.screen != null) {
            return;
        }
        Iterator<Map.Entry<FloatingTextEntity, Long>> it = PENDING_AUTO_OPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FloatingTextEntity, Long> entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            FloatingTextEntity floatingText = entry.getKey();
            // 实体已经不在世界了 放弃弹窗
            if (!floatingText.isAlive() || floatingText.level() != mc.level) {
                it.remove();
                continue;
            }
            // 文字内容还是空说明数据没同步 放弃
            if (floatingText.getText().isEmpty()) {
                it.remove();
                continue;
            }
            // 只给创建者自己弹窗
            if (!floatingText.canEdit(mc.player)) {
                it.remove();
                continue;
            }
            it.remove();
            mc.setScreen(new FloatingTextScreen(floatingText));
        }
    }
}
