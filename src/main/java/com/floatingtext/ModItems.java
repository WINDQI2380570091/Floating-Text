package com.floatingtext;

import com.floatingtext.item.FloatingTextToolItem;
import com.floatingtext.item.T72AVItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 物品注册都放这里
public class ModItems {

    // 物品注册表
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FloatingTextMod.MOD_ID);

    // 创造模式标签页注册表
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FloatingTextMod.MOD_ID);

    // 悬浮文字工具, 右键方块面或空气放文字
    public static final RegistryObject<Item> FLOATING_TEXT_TOOL =
            ITEMS.register("floating_text_tool", FloatingTextToolItem::new);

    // T-72AV 彩蛋物品, 阿拉伯语环境下右键会爆炸
    public static final RegistryObject<Item> T72AV =
            ITEMS.register("t72av", T72AVItem::new);

    // 模组自己的创造模式标签页
    public static final RegistryObject<CreativeModeTab> FLOATING_TEXT_TAB = TABS.register("floating_text_tab",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0) // 1.20.1 必须指定行和列
                    .icon(() -> new ItemStack(ModItems.FLOATING_TEXT_TOOL.get()))
                    .title(Component.translatable("itemGroup.floatingtext"))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.FLOATING_TEXT_TOOL.get()));
                        output.accept(new ItemStack(ModItems.T72AV.get()));
                    })
                    .build());

    // 挂到模组事件总线上
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
