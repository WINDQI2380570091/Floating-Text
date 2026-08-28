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

// 模组主类
@Mod(FloatingTextMod.MOD_ID)
public class FloatingTextMod {

    // 模组ID 所有注册都走这个命名空间
    public static final String MOD_ID = "floatingtext";

    // 日志
    public static final Logger LOGGER = LogUtils.getLogger();

    // 最近一次用工具放置文字的时间 用来判断新实体是不是刚放的 好自动弹编辑界面
    public static long lastClientPlaceTime = -1L;

    public FloatingTextMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册物品 实体 网络
        ModItems.register(modBus);
        ModEntityTypes.register(modBus);
        ModNetwork.register();

        // 注册渲染器 这个事件只在客户端触发 服务端不会走到这里
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(ModEntityTypes.FLOATING_TEXT.get(), FloatingTextRenderer::new));

        // 右键文字打开编辑界面的事件
        MinecraftForge.EVENT_BUS.register(FloatingTextMod.class);

        // 放置后自动弹窗的逻辑只在客户端注册
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(FloatingTextClient.class));

        LOGGER.info("Floating Text 模组已加载！");
    }

    // 玩家右键文字实体时触发 客户端负责开编辑界面 服务端只取消默认交互
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 不是本模组的文字就不管
        if (!(event.getTarget() instanceof FloatingTextEntity)) {
            return;
        }
        // 取消默认交互 防止和物品使用冲突
        event.setCanceled(true);

        if (event.getSide().isClient()) {
            FloatingTextEntity entity = (FloatingTextEntity) event.getTarget();
            // 只在客户端开界面 服务端不加载客户端类
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FloatingTextClient.openScreen(entity));
        }
    }
}
