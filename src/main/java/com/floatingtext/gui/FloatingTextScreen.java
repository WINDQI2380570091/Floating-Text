package com.floatingtext.gui;

import com.floatingtext.ModNetwork;
import com.floatingtext.entity.FloatingTextEntity;
import com.floatingtext.network.UpdateFloatingTextPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

// 悬浮文字编辑界面 纯客户端 服务端不会加载
// 右键文字或放置后自动弹出 点保存才把数据发给服务端
public class FloatingTextScreen extends Screen {

    // 可选颜色 前一个是实际颜色 后一个是按钮文字颜色 黑字在深色按钮上看不清所以单独给
    private static final int[][] COLORS = {
            {0xFFFFFFFF, 0xFFFFFF}, // 白
            {0xFF000000, 0xAAAAAA}, // 黑
            {0xFFFF5555, 0xFF5555}, // 红
            {0xFFFFFF55, 0xFFFF55}, // 黄
            {0xFF55FF55, 0x55FF55}, // 绿
            {0xFF5555FF, 0x5555FF}, // 蓝
            {0xFFAA00AA, 0xAA00AA}, // 紫
            {0xFFFFA500, 0xFFA500}, // 橙
    };

    private static final String[] COLOR_KEYS = {
            "floatingtext.color.white", "floatingtext.color.black", "floatingtext.color.red",
            "floatingtext.color.yellow", "floatingtext.color.green", "floatingtext.color.blue",
            "floatingtext.color.purple", "floatingtext.color.orange"
    };

    private final FloatingTextEntity entity;
    private int color;
    // 只有创建者能编辑 和服务端校验走同一套逻辑
    private final boolean canEdit;

    private EditBox textBox;
    private EditBox scaleBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditBox offsetZBox;
    private EditBox rotationBox;
    private Button saveButton;
    private Button deleteButton;

    // 五行数值的 Y 坐标 按屏幕高度自适应 小屏幕用紧凑间距
    private final int[] rowYs = new int[5];
    private int colorsY;

    public FloatingTextScreen(FloatingTextEntity entity) {
        super(Component.translatable("gui.floatingtext.title"));
        this.entity = entity;
        this.color = entity.getColor();
        Minecraft mc = Minecraft.getInstance();
        this.canEdit = entity.canEdit(mc.player);
    }

    @Override
    protected void init() {
        // 窗口大小变化会重新走 init 先清掉旧控件 不然会叠在一起点错
        this.clearWidgets();

        int centerX = this.width / 2;

        boolean compact = this.height < 250;
        int spacing = compact ? 20 : 24;
        this.rowYs[0] = 58;
        this.rowYs[1] = this.rowYs[0] + spacing;
        this.rowYs[2] = this.rowYs[1] + spacing;
        this.rowYs[3] = this.rowYs[2] + spacing;
        this.rowYs[4] = this.rowYs[3] + spacing;
        this.colorsY = this.rowYs[4] + spacing + 2;
        int buttonsY = this.colorsY + 26;

        // 文字输入框
        this.textBox = new EditBox(this.font, centerX - 100, 30, 200, 18,
                Component.translatable("gui.floatingtext.text"));
        this.textBox.setMaxLength(FloatingTextEntity.MAX_TEXT_LENGTH);
        this.textBox.setValue(entity.getText());
        // 必须用 addRenderableWidget 才会绘制 只 addWidget 的话不渲染
        this.addRenderableWidget(this.textBox);
        // 初始焦点给文字框 打开就能打字
        this.textBox.setFocused(true);
        this.setFocused(this.textBox);

        // 数值输入框 大小 偏移 旋转
        this.scaleBox = makeNumberBox(centerX, this.rowYs[0], entity.getScale());
        this.offsetXBox = makeNumberBox(centerX, this.rowYs[1], entity.getOffsetX());
        this.offsetYBox = makeNumberBox(centerX, this.rowYs[2], entity.getOffsetY());
        this.offsetZBox = makeNumberBox(centerX, this.rowYs[3], entity.getOffsetZ());
        this.rotationBox = makeNumberBox(centerX, this.rowYs[4], entity.getRotation());

        // 每行的加减按钮 和输入框联动
        addStepButtons(centerX, this.rowYs[0],
                b -> changeNumber(scaleBox, -0.25F, 0.15F, 10.0F, entity.getScale()),
                b -> changeNumber(scaleBox, 0.25F, 0.15F, 10.0F, entity.getScale()));
        addStepButtons(centerX, this.rowYs[1],
                b -> changeNumber(offsetXBox, -offsetStep(), -1.0F, 1.0F, entity.getOffsetX()),
                b -> changeNumber(offsetXBox, offsetStep(), -1.0F, 1.0F, entity.getOffsetX()));
        addStepButtons(centerX, this.rowYs[2],
                b -> changeNumber(offsetYBox, -offsetStep(), -1.0F, 1.0F, entity.getOffsetY()),
                b -> changeNumber(offsetYBox, offsetStep(), -1.0F, 1.0F, entity.getOffsetY()));
        addStepButtons(centerX, this.rowYs[3],
                b -> changeNumber(offsetZBox, -offsetStep(), -1.0F, 1.0F, entity.getOffsetZ()),
                b -> changeNumber(offsetZBox, offsetStep(), -1.0F, 1.0F, entity.getOffsetZ()));
        addStepButtons(centerX, this.rowYs[4],
                b -> changeRotation(-5.0F),
                b -> changeRotation(5.0F));

        // 颜色按钮 选中的在前面加个勾方便看清
        for (int i = 0; i < COLORS.length; i++) {
            final int[] entry = COLORS[i];
            final String key = COLOR_KEYS[i];
            Component label = Component.translatable(key)
                    .withStyle(style -> style.withColor(0xFF000000 | entry[1]));
            if (this.color == entry[0]) {
                label = Component.literal("✓ ").withStyle(style -> style.withColor(0xFF55FF55)).append(label);
            }
            this.addRenderableWidget(Button.builder(label, b -> this.color = entry[0])
                    .bounds(centerX - 100 + i * 26, this.colorsY, 26, 20)
                    .build());
        }

        // 保存 删除 取消
        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.save"), b -> save())
                .bounds(centerX - 95, buttonsY, 58, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.delete"), b -> delete())
                .bounds(centerX - 29, buttonsY, 58, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.cancel"), b -> onClose())
                .bounds(centerX + 37, buttonsY, 58, 20).build());

        // 不是创建者就禁用保存删除
        this.saveButton.active = this.canEdit;
        this.deleteButton.active = this.canEdit;
    }

    private EditBox makeNumberBox(int centerX, int y, float value) {
        EditBox box = new EditBox(this.font, centerX + 55, y, 40, 18,
                Component.literal("0"));
        box.setMaxLength(8);
        box.setValue(format(value));
        this.addRenderableWidget(box);
        return box;
    }

    private void addStepButtons(int centerX, int y, Button.OnPress onMinus, Button.OnPress onPlus) {
        this.addRenderableWidget(Button.builder(Component.literal("-"), onMinus)
                .bounds(centerX + 30, y, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), onPlus)
                .bounds(centerX + 100, y, 20, 18).build());
    }

    // 偏移步长 普通 0.01 按 Shift 是 0.1 省得点很多次
    private float offsetStep() {
        return Screen.hasShiftDown() ? 0.1F : 0.01F;
    }

    // 数值加减 限制范围后写回输入框
    private void changeNumber(EditBox box, float step, float min, float max, float fallback) {
        float value = parseNumber(box, fallback);
        value += step;
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        box.setValue(format(value));
    }

    // 旋转一次 5 度 0 到 360 循环
    private void changeRotation(float step) {
        float value = parseNumber(rotationBox, entity.getRotation());
        value = ((value + step) % 360.0F + 360.0F) % 360.0F;
        rotationBox.setValue(format(value));
    }

    // 解析输入框的数字 不是数字或者 NaN 就用实体当前值兜底
    private float parseNumber(EditBox box, float fallback) {
        try {
            float value = Float.parseFloat(box.getValue().trim());
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // 保存 打包成 tag 发给服务端
    private void save() {
        // 空文字不让存 提示一下留在界面
        if (textBox.getValue().trim().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.floatingtext.textEmpty"), false);
            }
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putString("text", textBox.getValue());
        data.putInt("color", color);
        data.putFloat("scale", clamp(parseNumber(scaleBox, entity.getScale()), 0.15F, 10.0F));
        data.putFloat("offsetX", clamp(parseNumber(offsetXBox, entity.getOffsetX()), -1.0F, 1.0F));
        data.putFloat("offsetY", clamp(parseNumber(offsetYBox, entity.getOffsetY()), -1.0F, 1.0F));
        data.putFloat("offsetZ", clamp(parseNumber(offsetZBox, entity.getOffsetZ()), -1.0F, 1.0F));
        data.putFloat("rotation", ((parseNumber(rotationBox, entity.getRotation()) % 360.0F) + 360.0F) % 360.0F);
        ModNetwork.CHANNEL.sendToServer(new UpdateFloatingTextPacket(entity.getId(), false, data));
        onClose();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // 删除
    private void delete() {
        ModNetwork.CHANNEL.sendToServer(new UpdateFloatingTextPacket(entity.getId(), true, null));
        onClose();
    }

    // 键盘鼠标事件不用手动转发 原版 Screen 会自动路由给当前聚焦的输入框
    @Override
    public boolean isPauseScreen() {
        return false; // 开着编辑界面游戏不暂停
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        int centerX = this.width / 2;

        gui.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);

        gui.drawString(this.font, Component.translatable("gui.floatingtext.text"),
                centerX - 100, 21, 0xAAAAAA);

        gui.drawString(this.font, Component.translatable("gui.floatingtext.scale"),
                centerX - 100, this.rowYs[0], 0xFFFFFF);
        gui.drawString(this.font, Component.translatable("gui.floatingtext.offsetX"),
                centerX - 100, this.rowYs[1], 0xFFFFFF);
        gui.drawString(this.font, Component.translatable("gui.floatingtext.offsetY"),
                centerX - 100, this.rowYs[2], 0xFFFFFF);
        gui.drawString(this.font, Component.translatable("gui.floatingtext.offsetZ"),
                centerX - 100, this.rowYs[3], 0xFFFFFF);
        gui.drawString(this.font, Component.translatable("gui.floatingtext.rotation"),
                centerX - 100, this.rowYs[4], 0xFFFFFF);

        // 颜色标签加当前颜色的预览块
        gui.drawString(this.font, Component.translatable("gui.floatingtext.color"),
                centerX - 140, this.colorsY + 4, 0xFFFFFF);
        gui.fill(centerX - 118, this.colorsY + 1, centerX - 102, this.colorsY + 19, this.color);

        // 不是创建者的提示
        if (!this.canEdit) {
            gui.drawCenteredString(this.font,
                    Component.translatable("gui.floatingtext.ownerOnly"),
                    centerX, this.height - 14, 0xFF5555);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }
}
