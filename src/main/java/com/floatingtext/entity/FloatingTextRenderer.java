package com.floatingtext.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// 文字渲染器, 用游戏自带字体画文字 类似名牌的实现方式
// 纯客户端类 服务端不会加载
public class FloatingTextRenderer extends EntityRenderer<FloatingTextEntity> {

    public FloatingTextRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // 文字是字体画的 不需要纹理
    @Override
    public ResourceLocation getTextureLocation(FloatingTextEntity entity) {
        return null;
    }

    @Override
    public void render(FloatingTextEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

        String text = entity.getText();
        if (text.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // 偏移
        poseStack.translate(entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ());

        // 朝向 先转 yRot 再转 xRot 顶面和底面就靠 xRot 躺平
        // 注意渲染器不会替实体做朝向旋转, 这里必须自己转
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

        // 1 字体像素对应 0.05 乘大小 格 文字居中画
        float scale = entity.getScale() * 0.05F;
        float x = -this.getFont().width(text) / 2.0F;
        float y = -4.5F;
        int color = entity.getColor();

        // 正面 字体是 GUI 坐标 Y 向下 世界坐标 Y 向上 所以 Y 要负缩放翻转 不然字是倒的
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
        poseStack.scale(scale, -scale, scale);
        // POLYGON_OFFSET 是告示牌画字的模式, 贴墙不会被方块遮挡也不会闪烁
        // 最后两个参数是背景透明度和光照 之前传错过导致文字全黑 光照一定要给 FULL_BRIGHT
        this.getFont().drawInBatch(text, x, y, color, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        // 背面 转 180 度再画一遍 这样从背面看也是正字 两面稍微错开点防止闪
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
        poseStack.scale(scale, -scale, scale);
        poseStack.translate(0.0F, 0.0F, 0.25F);
        this.getFont().drawInBatch(text, x, y, color, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        poseStack.popPose();
    }
}
