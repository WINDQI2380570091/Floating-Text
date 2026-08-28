package com.floatingtext.entity;

import com.floatingtext.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

// 悬浮文字实体, 文字本身是个实体而不是方块 这样能悬空也能贴墙
public class FloatingTextEntity extends Entity {

    // 所有要同步的字段都放 SynchedEntityData 里, 改动会自动发给所有客户端
    private static final EntityDataAccessor<String> DATA_TEXT =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.STRING);
    // 颜色 ARGB 格式
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_X =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Y =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OFFSET_Z =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    // 平面内的旋转角度
    private static final EntityDataAccessor<Float> DATA_ROTATION =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    // 创建者UUID, 用来做编辑权限校验
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.STRING);

    // 新文字默认内容, 放置后马上能看到 右键就能改
    public static final String DEFAULT_TEXT = "文字";

    // 文字长度上限, 输入框和存档都按这个截断 防止超长文字拖慢渲染
    public static final int MAX_TEXT_LENGTH = 100;

    public FloatingTextEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    // 放置文字时用的构造
    public FloatingTextEntity(Level level, double x, double y, double z, float yRot, UUID owner) {
        this(ModEntityTypes.FLOATING_TEXT.get(), level);
        setPos(x, y, z);
        setYRot(yRot);
        setOwner(owner);
        setText(DEFAULT_TEXT);
        setColor(0xFFFFFFFF);
        setScale(1.0F);
        setOffsetX(0.0F);
        setOffsetY(0.0F);
        setOffsetZ(0.0F);
        setRotation(0.0F);
    }

    // 必须返回 true 不然鼠标点不到文字 编辑面板就开不了
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TEXT, "");
        entityData.define(DATA_COLOR, 0xFFFFFFFF);
        entityData.define(DATA_SCALE, 1.0F);
        entityData.define(DATA_OFFSET_X, 0.0F);
        entityData.define(DATA_OFFSET_Y, 0.0F);
        entityData.define(DATA_OFFSET_Z, 0.0F);
        entityData.define(DATA_ROTATION, 0.0F);
        entityData.define(DATA_OWNER, "");
    }

    // 数据变了就重新算碰撞箱 保证点得到文字
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_TEXT) || key.equals(DATA_SCALE)
                || key.equals(DATA_OFFSET_X) || key.equals(DATA_OFFSET_Y) || key.equals(DATA_OFFSET_Z)) {
            refreshDimensions();
        }
    }

    // 动态算碰撞箱, 游戏会自动调用 不用手动设置
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = getScale();
        // 全角字符渲染宽度差不多是半角的两倍, 分开算避免长中文点不到
        float width = Math.max(0.6F, Math.min(8.0F,
                estimateTextWidth(getText()) * 0.05F * scale + Math.abs(getOffsetX()) * 2.0F + 0.5F));
        float height = Math.max(0.4F, Math.min(4.0F,
                9 * 0.05F * scale + Math.abs(getOffsetY()) * 2.0F + 0.5F));
        return new EntityDimensions(width, height, false);
    }

    // 粗略估算文字宽度 全角算 12 像素 半角算 6 像素
    private static float estimateTextWidth(String text) {
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            width += isFullWidth(text.charAt(i)) ? 12.0F : 6.0F;
        }
        return width;
    }

    // 全角字符范围 中文字符和全角标点都算
    private static boolean isFullWidth(char c) {
        return (c >= '\u2E80' && c <= '\u9FFF')
                || (c >= '\uF900' && c <= '\uFAFF')
                || (c >= '\uFF00' && c <= '\uFF60');
    }

    // 存档写入
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Text", getText());
        tag.putInt("Color", getColor());
        tag.putFloat("Scale", getScale());
        tag.putFloat("OffsetX", getOffsetX());
        tag.putFloat("OffsetY", getOffsetY());
        tag.putFloat("OffsetZ", getOffsetZ());
        tag.putFloat("Rotation", getRotation());
        tag.putString("Owner", getOwnerString());
    }

    // 读取存档 数值都要做范围限制 存档被改坏也不会出巨型文字
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setText(truncate(tag.getString("Text"), MAX_TEXT_LENGTH));
        setColor(tag.getInt("Color"));
        setScale(safeClamp(tag.getFloat("Scale"), 0.15F, 10.0F, 1.0F));
        setOffsetX(safeClamp(tag.getFloat("OffsetX"), -1.0F, 1.0F, 0.0F));
        setOffsetY(safeClamp(tag.getFloat("OffsetY"), -1.0F, 1.0F, 0.0F));
        setOffsetZ(safeClamp(tag.getFloat("OffsetZ"), -1.0F, 1.0F, 0.0F));
        setRotation(normalizeRotation(tag.getFloat("Rotation")));
        setOwnerString(tag.getString("Owner"));
    }

    // 网络同步用的 tag 按键名读写 不依赖字段顺序 服务端客户端共用
    public CompoundTag toSyncTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", getText());
        tag.putInt("color", getColor());
        tag.putFloat("scale", getScale());
        tag.putFloat("offsetX", getOffsetX());
        tag.putFloat("offsetY", getOffsetY());
        tag.putFloat("offsetZ", getOffsetZ());
        tag.putFloat("rotation", getRotation());
        return tag;
    }

    // 应用网络同步来的数据 空文字不覆盖 防止面板数据没同步时误清空
    public void applySyncTag(CompoundTag tag) {
        String text = truncate(tag.getString("text"), MAX_TEXT_LENGTH);
        if (!text.isEmpty()) {
            setText(text);
        }
        setColor(tag.getInt("color"));
        setScale(safeClamp(tag.getFloat("scale"), 0.15F, 10.0F, 1.0F));
        setOffsetX(safeClamp(tag.getFloat("offsetX"), -1.0F, 1.0F, 0.0F));
        setOffsetY(safeClamp(tag.getFloat("offsetY"), -1.0F, 1.0F, 0.0F));
        setOffsetZ(safeClamp(tag.getFloat("offsetZ"), -1.0F, 1.0F, 0.0F));
        setRotation(normalizeRotation(tag.getFloat("rotation")));
    }

    // 按 Unicode 字符截断 不会切坏 emoji 这种代理对
    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.codePoints().limit(maxChars)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    // 数值限制 非法值用默认值兜底
    private static float safeClamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    // 旋转归一化到 0 到 360
    private static float normalizeRotation(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return ((value % 360.0F) + 360.0F) % 360.0F;
    }

    public String getText() {
        return entityData.get(DATA_TEXT);
    }

    public void setText(String value) {
        entityData.set(DATA_TEXT, value == null ? "" : value);
    }

    public int getColor() {
        return entityData.get(DATA_COLOR);
    }

    public void setColor(int value) {
        entityData.set(DATA_COLOR, value);
    }

    public float getScale() {
        return entityData.get(DATA_SCALE);
    }

    public void setScale(float value) {
        entityData.set(DATA_SCALE, value);
    }

    public float getOffsetX() {
        return entityData.get(DATA_OFFSET_X);
    }

    public void setOffsetX(float value) {
        entityData.set(DATA_OFFSET_X, value);
    }

    public float getOffsetY() {
        return entityData.get(DATA_OFFSET_Y);
    }

    public void setOffsetY(float value) {
        entityData.set(DATA_OFFSET_Y, value);
    }

    public float getOffsetZ() {
        return entityData.get(DATA_OFFSET_Z);
    }

    public void setOffsetZ(float value) {
        entityData.set(DATA_OFFSET_Z, value);
    }

    public float getRotation() {
        return entityData.get(DATA_ROTATION);
    }

    public void setRotation(float value) {
        entityData.set(DATA_ROTATION, value);
    }

    // 创建者UUID 无主的返回 null
    public UUID getOwner() {
        String value = getOwnerString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null; // UUID 格式坏了就当无主处理
        }
    }

    public void setOwner(UUID uuid) {
        setOwnerString(uuid == null ? "" : uuid.toString());
    }

    public String getOwnerString() {
        return entityData.get(DATA_OWNER);
    }

    public void setOwnerString(String value) {
        entityData.set(DATA_OWNER, value == null ? "" : value);
    }

    // 能不能编辑这条文字 创建者本人可以 无主的旧文字谁都能改 客户端服务端统一走这里
    public boolean canEdit(Player player) {
        UUID owner = getOwner();
        return owner == null || (player != null && owner.equals(player.getUUID()));
    }

    // 不让玩家和生物推动 和盔甲架一样 免得位置漂移
    @Override
    public boolean isPushable() {
        return false;
    }
}
