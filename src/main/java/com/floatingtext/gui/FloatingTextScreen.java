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

/**
 * 悬浮文字编辑界面（纯客户端，服务端不会加载这个类）。
 * <p>
 * 打开方式：右键文字实体，或放置文字后自动弹出。
 * 编辑内容先存在本地，点"保存"才通过数据包发给服务端；
 * 服务端校验通过后应用修改，再自动同步给所有人。
 * 大小/偏移/旋转可以直接在输入框里输入数值，也可以用旁边的 [-]/[+] 按钮微调。
 * 布局会按屏幕高度自适应，小屏幕自动使用更紧凑的间距。
 */
public class FloatingTextScreen extends Screen {

    /** 可选颜色：{实际颜色(ARGB), 按钮文字显示颜色}。黑色文字在深色按钮上看不清，所以单独指定。 */
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

    /** 颜色按钮的语言键（对应 lang 文件里的翻译）。 */
    private static final String[] COLOR_KEYS = {
            "floatingtext.color.white", "floatingtext.color.black", "floatingtext.color.red",
            "floatingtext.color.yellow", "floatingtext.color.green", "floatingtext.color.blue",
            "floatingtext.color.purple", "floatingtext.color.orange"
    };

    // ===== 编辑目标 =====
    private final FloatingTextEntity entity;
    private int color;
    private final boolean canEdit; // 只有创建者能编辑

    // ===== 界面控件 =====
    private EditBox textBox;
    private EditBox scaleBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditBox offsetZBox;
    private EditBox rotationBox;
    private Button saveButton;
    private Button deleteButton;

    /** 五行数值的 Y 坐标（大小/X偏移/Y偏移/Z偏移/旋转），按屏幕高度自适应。 */
    private final int[] rowYs = new int[5];
    /** 颜色行和操作按钮行的 Y 坐标。 */
    private int colorsY;

    public FloatingTextScreen(FloatingTextEntity entity) {
        super(Component.translatable("gui.floatingtext.title"));
        this.entity = entity;
        this.color = entity.getColor();
        // 权限：创建者才能编辑；无主的旧文字任何人都能编辑（和服务端校验同一套逻辑）
        Minecraft mc = Minecraft.getInstance();
        this.canEdit = entity.canEdit(mc.player);
    }

    @Override
    protected void init() {
        // 清掉旧控件：窗口大小变化时 init 会被再次调用，
        // 不清的话新旧控件会叠在一起，导致点击和输入错乱
        this.clearWidgets();

        int centerX = this.width / 2;

        // 屏幕较小时使用紧凑间距，保证所有控件都在屏幕内
        boolean compact = this.height < 250;
        int spacing = compact ? 20 : 24;
        this.rowYs[0] = 58;
        this.rowYs[1] = this.rowYs[0] + spacing;
        this.rowYs[2] = this.rowYs[1] + spacing;
        this.rowYs[3] = this.rowYs[2] + spacing;
        this.rowYs[4] = this.rowYs[3] + spacing;
        this.colorsY = this.rowYs[4] + spacing + 2;
        int buttonsY = this.colorsY + 26;

        // ---- 文字输入框 ----
        this.textBox = new EditBox(this.font, centerX - 100, 30, 200, 18,
                Component.translatable("gui.floatingtext.text"));
        this.textBox.setMaxLength(FloatingTextEntity.MAX_TEXT_LENGTH);
        this.textBox.setValue(entity.getText());
        // 注意：必须用 addRenderableWidget 才会被绘制（addWidget 只处理事件、不渲染）
        this.addRenderableWidget(this.textBox);
        // 初始焦点放在文字框上：打开界面直接可以打字
        // （setFocused 让 Screen 把按键路由到它；EditBox 自己的 setFocused 让光标显示）
        this.textBox.setFocused(true);
        this.setFocused(this.textBox);

        // ---- 数值输入框（大小 / X偏移 / Y偏移 / Z偏移 / 旋转） ----
        this.scaleBox = makeNumberBox(centerX, this.rowYs[0], entity.getScale());
        this.offsetXBox = makeNumberBox(centerX, this.rowYs[1], entity.getOffsetX());
        this.offsetYBox = makeNumberBox(centerX, this.rowYs[2], entity.getOffsetY());
        this.offsetZBox = makeNumberBox(centerX, this.rowYs[3], entity.getOffsetZ());
        this.rotationBox = makeNumberBox(centerX, this.rowYs[4], entity.getRotation());

        // ---- 每行的 [-] [+] 微调按钮（输入框和按钮联动，点按钮数字会变） ----
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

        // ---- 颜色：8 个颜色按钮（左侧是当前颜色预览块；选中的按钮带 [✓] 标记） ----
        for (int i = 0; i < COLORS.length; i++) {
            final int[] entry = COLORS[i];
            final String key = COLOR_KEYS[i];
            // 当前选中的颜色在按钮文字前加 ✓，一眼就能看出选了哪个
            Component label = Component.translatable(key)
                    .withStyle(style -> style.withColor(0xFF000000 | entry[1]));
            if (this.color == entry[0]) {
                label = Component.literal("✓ ").withStyle(style -> style.withColor(0xFF55FF55)).append(label);
            }
            this.addRenderableWidget(Button.builder(label, b -> this.color = entry[0])
                    .bounds(centerX - 100 + i * 26, this.colorsY, 26, 20)
                    .build());
        }

        // ---- 操作按钮：保存 / 删除 / 取消 ----
        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.save"), b -> save())
                .bounds(centerX - 95, buttonsY, 58, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.delete"), b -> delete())
                .bounds(centerX - 29, buttonsY, 58, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.floatingtext.cancel"), b -> onClose())
                .bounds(centerX + 37, buttonsY, 58, 20).build());

        // 不是创建者：禁止保存和删除
        this.saveButton.active = this.canEdit;
        this.deleteButton.active = this.canEdit;
    }

    /** 创建一个数值输入框（放在标签和微调按钮之间），并加入输入框列表。 */
    private EditBox makeNumberBox(int centerX, int y, float value) {
        EditBox box = new EditBox(this.font, centerX + 55, y, 40, 18,
                Component.literal("0"));
        box.setMaxLength(8);
        box.setValue(format(value));
        this.addRenderableWidget(box);
        return box;
    }

    /** 创建一行 [-] [+] 按钮（按行 Y 坐标摆放）。 */
    private void addStepButtons(int centerX, int y, Button.OnPress onMinus, Button.OnPress onPlus) {
        this.addRenderableWidget(Button.builder(Component.literal("-"), onMinus)
                .bounds(centerX + 30, y, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), onPlus)
                .bounds(centerX + 100, y, 20, 18).build());
    }

    /** 微调偏移：普通点击 0.01，按住 Shift 点击 0.1（省得点很多次）。 */
    private float offsetStep() {
        return Screen.hasShiftDown() ? 0.1F : 0.01F;
    }

    /** 大小/偏移微调：读输入框里的值，加减 step，限制在 [min, max]，写回输入框。 */
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

    /** 旋转微调：一次 5 度，0 ~ 360 循环。 */
    private void changeRotation(float step) {
        float value = parseNumber(rotationBox, entity.getRotation());
        value = ((value + step) % 360.0F + 360.0F) % 360.0F;
        rotationBox.setValue(format(value));
    }

    /** 解析输入框里的数字；填的不是数字或 NaN/Infinity 时用 fallback（实体当前值）。 */
    private float parseNumber(EditBox box, float fallback) {
        try {
            float value = Float.parseFloat(box.getValue().trim());
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 数值显示保留两位小数。 */
    private String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** 保存：把当前所有输入框里的值打包成 tag 发给服务端（按键名读写，不会错位）。 */
    private void save() {
        // 空文字保护：服务端会忽略空文字（防误清空），这里提前阻止并提示，
        // 避免玩家以为保存成功但文字根本没变
        if (textBox.getValue().trim().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("message.floatingtext.textEmpty"), false);
            }
            return; // 不关闭界面，让玩家重新输入
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

    /** 删除：通知服务端移除这条文字。 */
    private void delete() {
        ModNetwork.CHANNEL.sendToServer(new UpdateFloatingTextPacket(entity.getId(), true, null));
        onClose();
    }

    // ===== 输入处理说明 =====
    // 键盘和鼠标事件不在这里手动转发——原版 Screen 会自动把它们路由到
    // "当前聚焦的输入框"（鼠标点击输入框时原版也会自动切换聚焦）。
    // 手写转发循环反而容易搞乱焦点，所以全部交给原版处理。

    @Override
    public boolean isPauseScreen() {
        return false; // 打开编辑界面时游戏不暂停
    }

    // ===== 绘制 =====

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        int centerX = this.width / 2;

        // 标题
        gui.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);

        // 文字输入框标签
        gui.drawString(this.font, Component.translatable("gui.floatingtext.text"),
                centerX - 100, 21, 0xAAAAAA);

        // 行标签（数值显示在输入框里）
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

        // 颜色行标签 + 当前颜色预览块（实时显示选中的颜色）
        gui.drawString(this.font, Component.translatable("gui.floatingtext.color"),
                centerX - 140, this.colorsY + 4, 0xFFFFFF);
        gui.fill(centerX - 118, this.colorsY + 1, centerX - 102, this.colorsY + 19, this.color);

        // 不是创建者：显示提示
        if (!this.canEdit) {
            gui.drawCenteredString(this.font,
                    Component.translatable("gui.floatingtext.ownerOnly"),
                    centerX, this.height - 14, 0xFF5555);
        }

        // 所有输入框和按钮（super 会绘制所有 addRenderableWidget 添加的控件）
        super.render(gui, mouseX, mouseY, partialTick);
    }
}
