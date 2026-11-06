package dev.msc.ui;

import java.util.Optional;

/**
 * MC 风按钮：常态 / 悬停 / 禁用三态各一张 200x20 贴图（用户从 gui.png 里切好打包在资源内），
 * 半宽按钮走三段拉伸，左右描边不参与缩放。贴图缺了就退回程序化画。
 */
public class GuiButton implements RowWidget {
    public static final int FULL_WIDTH = 200;
    public static final int HALF_WIDTH = 98;
    public static final int HEIGHT = 20;
    /** 贴图左右不参与拉伸的边宽：描边加高光只有两三像素。 */
    private static final int CAP = 4;

    private int x;
    private int y;
    private int width;
    private int height = HEIGHT;
    private String label;
    /** 图标按钮：贴图在 msc/lang 之外的那套 gui 纹理里，路径交给 UiTextures 记账。 */
    private String iconPath;
    /** 悬浮上去才显示的说明；null 或空就没有气泡。 */
    private String tooltip;
    /** 这句说明讲的是不是回不去的操作：气泡字色红 / 黄分档，见 {@link #setDangerTooltip}。 */
    private boolean tooltipDanger;
    /** 覆盖字色：0 表示按常态走（白 / 悬停黄 / 禁用灰），状态开关用它报红绿。 */
    private int labelColor;
    private final Runnable action;
    private boolean enabled = true;
    private boolean visible = true;
    /** 只有字、没有按钮贴图：原版语言列表那种行（选中靠一圈细边，悬停铺一层淡白）。 */
    private boolean textOnly;
    /** 列表里的选中行。 */
    private boolean selected;
    /** 互斥选项里「当前按下」的那一枚：套一圈白边，和选中态区分。 */
    private boolean pressed;
    /** 字后面跟一张小图标（例如确认后的对号）。 */
    private String suffixPath;

    public GuiButton(int x, int y, int width, String label, Runnable action) {
        this(x, y, width, HEIGHT, label, action);
    }

    /** 高度可给：图标按钮要跟贴图一样高，20 逻辑像素会把 24x24 的图标缩成糊的。 */
    public GuiButton(int x, int y, int width, int height, String label, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.action = action;
    }

    /** 设了图标就画图标、不画字；贴图缺了自动退回字，界面不会因此空一块。 */
    public GuiButton setIcon(String logicalTexturePath) {
        this.iconPath = logicalTexturePath;
        return this;
    }

    /** 挂一行悬浮说明：光标停上来才浮出黑框气泡，不在界面上常驻占位。 */
    public GuiButton setTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipDanger = false;
        return this;
    }

    /** 同上，但这枚按钮做的是回不去的操作（删、停、丢弃）：气泡用 §c 红字。 */
    public GuiButton setDangerTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipDanger = true;
        return this;
    }

    /** 覆盖字色（0 = 交回常态）：状态开关要一眼看出红 / 绿时用这个。 */
    public GuiButton setLabelColor(int color) {
        this.labelColor = color;
        return this;
    }

    @Override
    public WidgetTip tipAt(int mouseX, int mouseY) {
        return tooltip == null || tooltip.isEmpty() || !contains(mouseX, mouseY)
                ? null
                : new WidgetTip(this, tooltip, mouseX, mouseY, tooltipDanger);
    }

    public boolean contains(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** 命中且可用才吃掉点击，返回是否消费。按键声和动作一起走，禁用的按钮不响。 */
    public boolean click(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (enabled && action != null) {
            UiPreferences.get().playClickSound();
            action.run();
        }
        return true;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean hovered = enabled && contains(mouseX, mouseY);
        if (textOnly) {
            renderTextOnly(graphics, hovered);
            return;
        }
        Optional<Sprite> face = UiTextures.texture(hovered ? UiTextures.BUTTON_HOVER
                : enabled ? UiTextures.BUTTON_LIGHT : UiTextures.BUTTON_DARK);
        if (pressed && enabled) {
            // 按下的方式按钮：仍用 light 那张纹理，压暗到 light 与 dark 之间，再套白边
            face = UiTextures.texture(UiTextures.BUTTON_LIGHT);
        }
        if (face.isPresent()) {
            if (pressed && enabled) {
                graphics.sliced(face.get(), x, y, width, height, CAP, 0xFF8A8A8A);
            } else {
                graphics.sliced(face.get(), x, y, width, height, CAP);
            }
        } else {
            // 贴图没带上的机器照样要能跑，退回程序化画一张灰按钮
            int shade = enabled ? (hovered ? 0xFFD0D0D0 : 0xFFC6C6C6) : 0xFF585858;
            graphics.fill(x, y, width, height, 0xFF101010);
            graphics.fill(x + 1, y + 1, width - 2, height - 2, shade);
            // 左上亮、右下暗，做出凸起的那层斜面
            graphics.fill(x + 1, y + 1, width - 2, 1, enabled ? 0xFFFFFFFF : 0xFF808080);
            graphics.fill(x + 1, y + 1, 1, height - 2, enabled ? 0xFFFFFFFF : 0xFF808080);
            graphics.fill(x + 1, y + height - 2, width - 2, 1, 0x66000000);
            graphics.fill(x + width - 2, y + 1, 1, height - 2, 0x66000000);
            if (hovered) {
                graphics.fill(x + 1, y + 1, width - 2, height - 2, 0x28FFFFFF);
            }
        }
        if (pressed) {
            graphics.outline(x, y, width, height, 0xFFFFFFFF);
        }
        Optional<Sprite> icon = iconPath == null ? Optional.empty() : UiTextures.texture(iconPath);
        if (icon.isPresent()) {
            // 装得下就原尺寸画（24x24 的图标在 2 倍 GUI scale 下是整数倍采样，不糊）；装不下才等比缩
            Sprite sprite = icon.get();
            int size = Math.min(height - 2, Math.min(sprite.width(), sprite.height()));
            graphics.sprite(sprite, x + (width - size) / 2, y + (height - size) / 2, size, size,
                    0, 0, sprite.width(), sprite.height());
            return;
        }
        if (iconPath != null && label.isEmpty()) {
            // 图标贴图没了又没字：画个方框占位，让人看得见这里本该有个按钮
            graphics.outline(x + 4, y + 4, width - 8, height - 8, enabled ? 0xFF404040 : 0xFF202020);
            return;
        }
        int textColor = !enabled ? UiColors.DIM : labelColor != 0 ? labelColor
                : hovered ? UiColors.HOVER_TEXT : UiColors.WHITE;
        // 字比面宽就截断：半宽按钮挤下长词条时宁可少字，不能爬出边框
        String shown = graphics.fit(label, width - 8);
        graphics.textShadow(shown, x + width / 2 - graphics.textWidth(shown) / 2,
                y + height / 2 - 4, textColor);
        if (suffixPath != null) {
            UiTextures.texture(suffixPath).ifPresent(sprite -> {
                int textWidth = graphics.textWidth(shown);
                int suffixX = x + width / 2 + textWidth / 2 + 2;
                graphics.sprite(sprite, suffixX, y + (height - sprite.height()) / 2,
                        sprite.width(), sprite.height());
            });
        }
    }

    /**
     * 原版语言列表那一行：没有按钮面，只有居中的字。悬停铺一层很淡的白，
     * 选中的那一行套一圈 §7 细边 —— 和原版列表的选中框一个意思，不额外画底板。
     */
    private void renderTextOnly(GuiGraphics graphics, boolean hovered) {
        if (selected) {
            graphics.fill(x, y, width, height, 0x40000000);
            graphics.outline(x, y, width, height, UiColors.DIM);
        } else if (hovered) {
            graphics.fill(x, y, width, height, 0x33FFFFFF);
        }
        int textColor = enabled ? UiColors.WHITE : UiColors.DIM;
        String shown = graphics.fit(label, width - 8);
        graphics.textCentered(shown, x + width / 2, y + height / 2 - 4, 8, textColor, false);
    }

    public int height() {
        return height;
    }

    /** 滚动块里的按钮：行位置与行宽由块发下来，命中测试才和看到的一致。 */
    @Override
    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** 切成「只有字」的行：语言列表那种没有贴图的按钮。 */
    public GuiButton setTextOnly(boolean textOnly) {
        this.textOnly = textOnly;
        return this;
    }

    /** 列表选中态：只有字时才画得出来，普通按钮忽略它。 */
    public GuiButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    /** 互斥选项的按下态：普通按钮画一圈白边。 */
    public GuiButton setPressed(boolean pressed) {
        this.pressed = pressed;
        return this;
    }

    /** 字后面紧跟一张贴图（对号之类），贴图缺了就不画。 */
    public GuiButton setSuffix(String logicalTexturePath) {
        this.suffixPath = logicalTexturePath;
        return this;
    }

    public String label() {
        return label;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }
}
