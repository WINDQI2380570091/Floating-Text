package com.floatingtext.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 悬浮文字的渲染器：用 Minecraft 自带的字体把文字画出来（和"名牌"的实现方式一样）。
 * <p>
 * 文字正反两面都画：从任何角度看文字都可见。
 * （之前误以为双面渲染导致"文字发黑"，实际发黑是光照参数传错造成的，
 * 光照修复后双面渲染颜色正常。单面渲染有背面剔除，绕到另一侧会看不到文字。）
 * 这个类是纯客户端类，只有客户端会加载它。
 */
public class FloatingTextRenderer extends EntityRenderer<FloatingTextEntity> {

    public FloatingTextRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** 本渲染器不使用纹理（文字是字体画的）。 */
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
            return; // 没有文字就不画
        }

        poseStack.pushPose();

        // 1. 世界坐标位置微调（X/Y/Z 偏移，单位：格）
        poseStack.translate(entity.getOffsetX(), entity.getOffsetY(), entity.getOffsetZ());

        // 2. 朝向：让文字正面朝向放置时玩家的视线方向。
        //    注意：EntityRenderDispatcher 不会替实体做 yRot 旋转（生物渲染器才自己算），
        //    所以这里必须自己旋转。
        //    先按 yRot 水平转向（侧面文字竖直面对玩家），再按 xRot 俯仰
        //    （顶面文字躺平朝上、底面躺平朝下）。mulPose 依次调用 = 后面的先作用。
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

        // 通用参数：居中、字体单位缩放、颜色
        float scale = entity.getScale() * 0.05F; // 1 字体像素 = 0.05 × 大小 格
        float x = -this.getFont().width(text) / 2.0F; // 水平居中
        float y = -4.5F;                              // 垂直居中（字体高约 9 像素）
        int color = entity.getColor();

        // 3. 应用平面内旋转后画文字（单面）。
        //    关键：字体是 GUI 坐标系（Y 向下）设计的，世界坐标 Y 向上，
        //    所以 Y 必须负缩放翻转，否则文字上下颠倒（这就是之前"倒置"的真正原因）。
        //    原版名牌渲染也是用负缩放（scale(-0.025, -0.025, 0.025)）翻转的。
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
        poseStack.scale(scale, -scale, scale);
        // 参数说明（非常重要）：
        // - POLYGON_OFFSET：对文字做深度偏移，紧贴方块表面时文字依然显示在表面之上，
        //   不会被方块遮挡，也不会和方块表面重叠闪烁（原版告示牌就是用它画字的）。
        // - 最后两个 int 是 (背景透明度, 光照)，不是 (光照, overlay)！
        //   之前误传了 (FULL_BRIGHT, NO_OVERLAY=0)，导致光照=0，文字渲染成纯黑——
        //   这就是"颜色改不了/覆盖一层黑色"的真正原因。
        //   正确传法：(0, LightTexture.FULL_BRIGHT) = 无背景 + 全亮光照。
        this.getFont().drawInBatch(text, x, y, color, false, // 不画阴影
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        // 4. 背面：绕 Y 转 180° 再画一次（从背面看文字是正字）。
        //    深度方向错开一点点，防止两面完全共面导致渲染闪烁。
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
        poseStack.scale(scale, -scale, scale);
        poseStack.translate(0.0F, 0.0F, 0.25F);
        this.getFont().drawInBatch(text, x, y, color, false, // 不画阴影
                poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();

        poseStack.popPose();
    }
}
