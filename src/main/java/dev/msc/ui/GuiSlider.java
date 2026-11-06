package dev.msc.ui;

import java.util.Optional;
import java.util.function.DoubleConsumer;

/**
 * 数值滑条：一条横到底的槽 + 一个窄滑块，字贴在槽上居中。
 *
 * 贴图全用按钮那三张 —— 槽是 button_dark，滑块是 button_light，按住或指上就是 button_hover，
 * 不再要 slide.png 那种第四张图。滑块和槽一样高、只在横向三段拉伸（两端各留 3 像素原样），
 * 所以它读起来是「槽上的一枚窄块」而不是一颗圆点，也不会矮半截像浮在槽外。
 */
public final class GuiSlider implements RowWidget {
    /** 槽左右保留的贴图边宽。 */
    private static final int TRACK_CAP = 4;
    /** 滑块宽：两端各留 3 像素原样，中间只剩 1 像素 —— 再宽就不像一枚能捏住的滑块了。 */
    private static final int KNOB_WIDTH = 7;
    private static final int KNOB_CAP = 3;

    /** 值 → 显示文字，例如 6.0 → "6.0 °/s"。 */
    public interface Format {
        String text(double value);
    }

    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final Format format;
    private final DoubleConsumer onChange;

    private int x;
    private int y;
    private int width = 200;
    private int height = RowWidget.HEIGHT;
    private double value;
    private boolean grabbed;
    /** 悬浮上去才显示的说明（滑条拉之前先看清这一档管的是什么）。 */
    private String tooltip;
    /** 这句说明讲的是不是回不去的操作。 */
    private boolean tooltipDanger;

    /**
     * @param step 落格宽度；0 或负数就是连续值，只按 max-min 的比例走
     */
    public GuiSlider(String label, double min, double max, double step, double initial,
                     Format format, DoubleConsumer onChange) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.onChange = onChange;
        this.value = clampValue(initial);
    }

    @Override
    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = RowWidget.HEIGHT;
    }

    @Override
    public int height() {
        return height;
    }

    public double value() {
        return value;
    }

    /** 挂一行悬浮说明：光标停上来才浮出黑框气泡，不在界面上常驻占位。 */
    public GuiSlider setTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipDanger = false;
        return this;
    }

    /** 同上，但这枚滑条一动就改的是回不去的东西：气泡用 §c 红字。 */
    public GuiSlider setDangerTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipDanger = true;
        return this;
    }

    @Override
    public WidgetTip tipAt(int mouseX, int mouseY) {
        return tooltip == null || tooltip.isEmpty() || !contains(mouseX, mouseY)
                ? null
                : new WidgetTip(this, tooltip, mouseX, mouseY, tooltipDanger);
    }

    /** 外部改值（例如从设置里读回来）时不让它跑出范围。 */
    public void setValue(double wanted) {
        value = clampValue(wanted);
    }

    /** 滑块中心能走的那段区间：左右各让出半个滑块，值到端点时滑块不出槽。 */
    private int travel() {
        return Math.max(1, width - KNOB_WIDTH);
    }

    private int knobLeft() {
        return x + (int) Math.round(fraction() * travel());
    }

    private double fraction() {
        return max <= min ? 0 : (value - min) / (max - min);
    }

    /** 按像素位置算值，落格步长在这里生效；步长非正数就只夹范围。 */
    private double valueAt(double pixelX) {
        double ratio = Math.max(0, Math.min(1, (pixelX - x - KNOB_WIDTH / 2.0) / travel()));
        double raw = min + ratio * (max - min);
        if (step <= 0) {
            return clampValue(raw);
        }
        // 落格后再抹一次浮点尾巴：6 个 0.05 在 double 里是 0.30000000000000004，设置文件里不该长这样
        return clampValue(Math.round(Math.round(raw / step) * step * 1e6) / 1e6);
    }

    private double clampValue(double wanted) {
        return Math.max(min, Math.min(max, wanted));
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        // 点哪走哪：按在滑块上就开始拖，按在槽的别处就一步跳过去并交还回调
        grabbed = true;
        apply(valueAt(mouseX));
        return true;
    }

    @Override
    public boolean drag(int mouseX, int mouseY, int button) {
        if (!grabbed) {
            return false;
        }
        apply(valueAt(mouseX));
        return true;
    }

    @Override
    public void release(int button) {
        grabbed = false;
    }

    /** 外部把值定下来，并走同一条 onChange（「恢复默认」靠它，不用两处各写一遍落盘）。 */
    public void applyValue(double wanted) {
        apply(wanted);
    }

    private void apply(double wanted) {
        if (wanted == value) {
            return;
        }
        value = wanted;
        onChange.accept(value);
    }

    private boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<Sprite> track = UiTextures.texture(UiTextures.BUTTON_DARK);
        if (track.isPresent()) {
            graphics.sliced(track.get(), x, y, width, height, TRACK_CAP);
        } else {
            graphics.fill(x, y, width, height, 0xFF1F1F1F);
            graphics.outline(x, y, width, height, UiColors.EDGE);
        }
        int knobX = knobLeft();
        boolean hovered = contains(mouseX, mouseY);
        Optional<Sprite> face = UiTextures.texture(
                grabbed || hovered ? UiTextures.BUTTON_HOVER : UiTextures.BUTTON_LIGHT);
        if (face.isPresent()) {
            graphics.sliced(face.get(), knobX, y, KNOB_WIDTH, height, KNOB_CAP);
        } else {
            graphics.fill(knobX, y, KNOB_WIDTH, height, hovered ? 0xFFD0D0D0 : 0xFFC6C6C6);
            graphics.outline(knobX, y, KNOB_WIDTH, height, 0xFF000000);
        }
        String shown = graphics.fit(label + ": " + format.text(value), width - 8);
        graphics.textCentered(shown, x + width / 2, y + height / 2 - 4, 8,
                hovered ? UiColors.HOVER_TEXT : UiColors.WHITE, true);
    }
}
