package com.floatingtext;

import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 实体类型注册
public class ModEntityTypes {

    // 实体注册表
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FloatingTextMod.MOD_ID);

    // 悬浮文字实体 碰撞箱和追踪距离先设个初始值 后面会自动按文字大小调整
    public static final RegistryObject<EntityType<FloatingTextEntity>> FLOATING_TEXT = ENTITY_TYPES.register("floating_text",
            () -> EntityType.Builder.<FloatingTextEntity>of(FloatingTextEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .build("floatingtext:floating_text"));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
