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

/**
 * 悬浮文字实体：一段悬浮在空气中的文字（由渲染器画出，不是方块）。
 * <p>
 * 三个重要能力全部由 Minecraft 原生机制提供，不写一行额外同步代码：
 * 1. 保存/加载：saveAdditional / readAdditional 读写 NBT，退出重进游戏文字仍在；
 * 2. 多人同步：所有字段放在 SynchedEntityData 里，修改后游戏自动发给所有客户端；
 * 3. 性能：实体不写任何每 tick 逻辑，游戏会自动剔除屏幕外的文字。
 */
public class FloatingTextEntity extends Entity {

    // ===== 要同步的数据字段（游戏自动处理同步，改一次同步一次） =====

    /** 文字内容。 */
    private static final EntityDataAccessor<String> DATA_TEXT =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.STRING);
    /** 文字颜色（ARGB 格式，如 0xFFFFFFFF 白色）。 */
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.INT);
    /** 文字大小（0.25 ~ 3）。 */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    /** 位置微调 X（格，-1 ~ 1）。 */
    private static final EntityDataAccessor<Float> DATA_OFFSET_X =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    /** 位置微调 Y（格，-1 ~ 1）。 */
    private static final EntityDataAccessor<Float> DATA_OFFSET_Y =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    /** 位置微调 Z（格，-1 ~ 1）。 */
    private static final EntityDataAccessor<Float> DATA_OFFSET_Z =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    /** 文字在自己平面内的旋转角度（度，0 ~ 360）。 */
    private static final EntityDataAccessor<Float> DATA_ROTATION =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.FLOAT);
    /** 创建者的 UUID（字符串），用来做编辑权限校验。 */
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(FloatingTextEntity.class, EntityDataSerializers.STRING);

    /** 新文字默认显示的占位内容（放置后马上可见，右键即可修改）。 */
    public static final String DEFAULT_TEXT = "文字";

    /** 文字最大长度（Unicode 字符数）：UI 输入框限制 100，服务端同样截断，防止恶意客户端发超长文字拖慢渲染。 */
    public static final int MAX_TEXT_LENGTH = 100;

    public FloatingTextEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * 放置文字时使用的构造函数。
     *
     * @param x    世界坐标 X（格）
     * @param y    世界坐标 Y（格）
     * @param z    世界坐标 Z（格）
     * @param yRot 水平朝向（度）：文字正面会朝向放置时玩家面对的方向
     * @param owner 创建者 UUID
     */
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

    // ===== 数据同步定义（游戏要求的抽象方法） =====

    /**
     * 让实体可以被鼠标点击到（拾取）。
     * 关键：Entity 默认 isPickable() 返回 false，不覆写的话右键点不到文字，
     * 编辑面板就永远打不开。
     */
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

    /**
     * 数据变化时（包括网络同步过来时）自动调用。
     * 文字内容/大小/偏移变了，就重新计算碰撞箱，让点击和渲染剔除更准确。
     * （1.20.1 的该方法在 Entity 中是 public，不能用 protected。）
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_TEXT) || key.equals(DATA_SCALE)
                || key.equals(DATA_OFFSET_X) || key.equals(DATA_OFFSET_Y) || key.equals(DATA_OFFSET_Z)) {
            refreshDimensions();
        }
    }

    /**
     * 动态计算碰撞箱大小（游戏会自动调用这个方法）。
     * 文字宽度 ≈ 按字符类型估算（全角 12 像素、半角 6 像素）× 0.05 × 大小；
     * 高度 ≈ 9 像素 × 0.05 × 大小。
     * 加上偏移量留一点点击余量，并做上下限保护。
     * （1.20.1 的 Entity 没有 setSize 方法且 dimensions 字段是私有的，
     *  正确做法是覆写 getDimensions(Pose)，游戏会在需要时自动调用。）
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = getScale();
        // 全角字符（中文）实际渲染宽度约是半角的两倍，按字符类型分别估算，
        // 避免长中文文字的实际碰撞箱比文字窄、点不到
        float width = Math.max(0.6F, Math.min(8.0F,
                estimateTextWidth(getText()) * 0.05F * scale + Math.abs(getOffsetX()) * 2.0F + 0.5F));
        float height = Math.max(0.4F, Math.min(4.0F,
                9 * 0.05F * scale + Math.abs(getOffsetY()) * 2.0F + 0.5F));
        return new EntityDimensions(width, height, false);
    }

    /** 粗略估算文字渲染宽度（字体像素）：全角字符按 12 像素、半角按 6 像素。 */
    private static float estimateTextWidth(String text) {
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            width += isFullWidth(text.charAt(i)) ? 12.0F : 6.0F;
        }
        return width;
    }

    /** 全角字符范围（中文/日文/全角标点等），只用于碰撞箱估算。 */
    private static boolean isFullWidth(char c) {
        return (c >= '\u2E80' && c <= '\u9FFF')   // CJK 部首/汉字
                || (c >= '\uF900' && c <= '\uFAFF') // CJK 兼容表意文字
                || (c >= '\uFF00' && c <= '\uFF60'); // 全角标点/数字/字母
    }

    // ===== 存档读写（游戏要求的抽象方法） =====

    /** 保存到存档（服务端把实体写进区块数据时调用）。 */
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

    /** 从存档读取（服务端加载区块时调用）。 */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 数值全部经过范围限制：存档数据被外部修改/损坏时也不会出现超大或非法值
        setText(truncate(tag.getString("Text"), MAX_TEXT_LENGTH));
        setColor(tag.getInt("Color"));
        setScale(safeClamp(tag.getFloat("Scale"), 0.15F, 10.0F, 1.0F));
        setOffsetX(safeClamp(tag.getFloat("OffsetX"), -1.0F, 1.0F, 0.0F));
        setOffsetY(safeClamp(tag.getFloat("OffsetY"), -1.0F, 1.0F, 0.0F));
        setOffsetZ(safeClamp(tag.getFloat("OffsetZ"), -1.0F, 1.0F, 0.0F));
        setRotation(normalizeRotation(tag.getFloat("Rotation")));
        setOwnerString(tag.getString("Owner"));
    }

    // ===== 网络同步（服务端和客户端共用，按键名读写，不依赖字段顺序） =====

    /** 把当前文字数据打包成一个 tag（发给客户端同步用）。 */
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

    /** 从 tag 里读出数据并应用（服务端处理保存包、客户端处理同步包都用它）。 */
    public void applySyncTag(CompoundTag tag) {
        String text = truncate(tag.getString("text"), MAX_TEXT_LENGTH);
        // 空文字保护：面板数据未同步时可能发来空文字，此时保留原文，避免误清空文字
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

    /** 把文字截断到最多 maxChars 个 Unicode 字符（按代码点截断，不会切断 emoji 等代理对）。 */
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

    /** 数值限制在 [min, max]（非法值 NaN/Infinity 用 fallback 代替）。 */
    private static float safeClamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 旋转角度归一化到 0 ~ 360（非法值归零）。 */
    private static float normalizeRotation(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return ((value % 360.0F) + 360.0F) % 360.0F;
    }

    // ===== 字段读写（getter / setter） =====

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

    /** 创建者 UUID（无主文字返回 null）。 */
    public UUID getOwner() {
        String value = getOwnerString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null; // 存档数据损坏时按无主处理
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

    /**
     * 玩家是否有权编辑这条文字：创建者本人可以；无主的旧文字任何人可以。
     * 客户端弹窗和编辑界面、服务端权限校验都统一走这个方法。
     */
    public boolean canEdit(Player player) {
        UUID owner = getOwner();
        return owner == null || (player != null && owner.equals(player.getUUID()));
    }

    /**
     * 文字是装饰实体，不允许被玩家/生物推动（和盔甲架一致），保证位置精确不漂移。
     */
    @Override
    public boolean isPushable() {
        return false;
    }
}
