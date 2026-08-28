package com.floatingtext;

import com.floatingtext.client.FloatingTextClient;
import com.floatingtext.entity.FloatingTextEntity;
import com.floatingtext.entity.FloatingTextRenderer;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 模组主类：所有注册的入口。
 * <p>
 * 作用：
 * 1. 注册本模组的物品、实体类型、网络数据包；
 * 2. 注册渲染器（只在客户端生效）；
 * 3. 监听通用事件（右键文字实体 → 打开编辑界面）。
 */
@Mod(FloatingTextMod.MOD_ID)
public class FloatingTextMod {

    /** 模组 ID，整个模组所有注册都用这个命名空间，避免和其他模组冲突。 */
    public static final String MOD_ID = "floatingtext";

    /** 日志输出（游戏崩溃时会在日志里看到本模组的消息）。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 客户端最近一次用工具放置文字的时刻（毫秒）。
     * 客户端放置时记录，新文字实体加入客户端世界后会自动弹出编辑界面。
     * （放在公共类里是为了让物品类（两端都会加载）也能访问，不引用任何客户端类。）
     */
    public static long lastClientPlaceTime = -1L;

    public FloatingTextMod() {
        // 模组事件总线（负责物品/实体等注册）
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册物品、实体类型、网络通道
        ModItems.register(modBus);
        ModEntityTypes.register(modBus);
        ModNetwork.register();

        // 注册渲染器：RegisterRenderers 事件只在客户端触发，服务端运行时会自动跳过，不会报错
        // （1.20.1 的 IEventBus 没有 addListener(Class, Consumer) 形式，让编译器根据 lambda 参数自动推断事件类型）
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(ModEntityTypes.FLOATING_TEXT.get(), FloatingTextRenderer::new));

        // 注册通用事件（右键打开编辑界面）
        MinecraftForge.EVENT_BUS.register(FloatingTextMod.class);

        // 客户端专属事件（放置后自动弹出编辑界面）：只有客户端会注册，服务端不加载客户端类
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(FloatingTextClient.class));

        LOGGER.info("Floating Text 模组已加载！");
    }

    /**
     * 玩家右键点击悬浮文字实体时触发（客户端/服务端都会触发这个事件）。
     * 客户端负责打开编辑界面；服务端只负责取消默认交互，不做其他事情。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 只有点击本模组的文字实体才处理
        if (!(event.getTarget() instanceof FloatingTextEntity)) {
            return;
        }
        // 取消默认交互，避免和物品的使用行为冲突
        event.setCanceled(true);

        // 只在客户端打开编辑界面
        if (event.getSide().isClient()) {
            FloatingTextEntity entity = (FloatingTextEntity) event.getTarget();
            // DistExecutor 保证"打开界面"这段代码只会在客户端执行，服务端不会加载任何客户端类
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FloatingTextClient.openScreen(entity));
        }
    }
}
