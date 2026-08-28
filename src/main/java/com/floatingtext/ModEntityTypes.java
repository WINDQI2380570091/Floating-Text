package com.floatingtext;

import com.floatingtext.entity.FloatingTextEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组所有实体类型的注册都集中在这里。
 */
public class ModEntityTypes {

    /** 实体类型注册表（namespace 是 floatingtext）。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FloatingTextMod.MOD_ID);

    /**
     * 悬浮文字实体：一段可编辑的文字（由渲染器画出，不是方块）。
     * - sized(0.5, 0.5)：初始碰撞箱，放置后会自动按文字大小调整（见 FloatingTextEntity.updateDimensions）
     * - clientTrackingRange(10)：10 格内同步给客户端
     * - MobCategory.MISC：普通杂项实体，不会被当作生物处理，也没有生成规则
     */
    public static final RegistryObject<EntityType<FloatingTextEntity>> FLOATING_TEXT = ENTITY_TYPES.register("floating_text",
            () -> EntityType.Builder.<FloatingTextEntity>of(FloatingTextEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .build("floatingtext:floating_text"));

    /** 在模组加载时调用，把上面的注册挂到模组事件总线上。 */
    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
