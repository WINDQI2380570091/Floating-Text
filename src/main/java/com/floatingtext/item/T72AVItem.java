package com.floatingtext.item;

import com.floatingtext.ModNetwork;
import com.floatingtext.network.T72AVBoomPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * T-72AV：彩蛋物品。
 * <p>
 * 游戏语言是阿拉伯语时，右键它会触发一次 TNT 爆炸（和 TNT 爆炸范围一致）。
 * 语言检测只在客户端做（语言是客户端设置），客户端检测通过后发爆炸包给服务端，
 * 由服务端执行爆炸（服务端才能安全地破坏方块、对实体造成伤害）。
 */
public class T72AVItem extends Item {

    public T72AVItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 客户端：检测游戏语言（languageCode 如 "ar_sa" 以 "ar" 开头即阿拉伯语）
            String lang = net.minecraft.client.Minecraft.getInstance().options.languageCode;
            if (lang != null && lang.toLowerCase().startsWith("ar")) {
                // 阿拉伯语：发爆炸请求给服务端，服务端执行 TNT 爆炸
                ModNetwork.CHANNEL.sendToServer(new T72AVBoomPacket());
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // 服务端：什么都不做，爆炸由 T72AVBoomPacket 触发
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
