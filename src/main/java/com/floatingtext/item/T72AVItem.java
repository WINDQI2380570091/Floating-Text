package com.floatingtext.item;

import com.floatingtext.ModNetwork;
import com.floatingtext.network.T72AVBoomPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// T-72AV 彩蛋物品, 游戏语言是阿拉伯语时右键会炸
// 语言是客户端设置 所以在客户端判断 爆炸由服务端执行
public class T72AVItem extends Item {

    public T72AVItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 语言代码是 ar 开头的都算阿拉伯语
            String lang = net.minecraft.client.Minecraft.getInstance().options.languageCode;
            if (lang != null && lang.toLowerCase().startsWith("ar")) {
                // 发爆炸请求给服务端
                ModNetwork.CHANNEL.sendToServer(new T72AVBoomPacket());
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // 服务端不做任何事 爆炸在数据包里处理
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
