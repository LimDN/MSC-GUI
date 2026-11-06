package dev.msc.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页那一竖排行，带自己的滚动条。
 *
 * 五种行宽（和原版 Options / 「音乐和声音」那两页的栅格一致）：
 * <ul>
 *   <li>{@link #pair} —— 两枚 {@link #OPTION_WIDTH} 宽的控件并列，中间一道 {@link #OPTION_GAP} 缝，
 *       整行关于窗口中线对称；</li>
 *   <li>{@link #wide} —— 单枚 {@link #WIDE_WIDTH} 宽居中，是并列控件的 125%，滑条与「完成」用它；</li>
 *   <li>{@link #half} —— 单枚占左半格，右边空着（原版音乐页最后那条「声音/语音」就是这么排的）；</li>
 *   <li>{@link #centered} —— 单枚 {@link #OPTION_WIDTH} 宽居中（原版「显示字幕」那一格）；</li>
 *   <li>{@link #full} —— 铺满块宽，给说明文字用（字要横向居中，不需要边框）。</li>
 * </ul>
 * 行数超过板子能显示的高度时右边长出槽，窗口再矮也点得到最后一行。
 */
public final class RowBlock {
    /** 行距：20 高的控件之间留 2 像素缝，和原版按钮排布一致。 */
    public static final int PITCH = 22;
    /** 并列一行的控件宽（原版 Options 那两列实测 150~160）。 */
    public static final int OPTION_WIDTH = 160;
    /** 并列两枚之间的缝。 */
    public static final int OPTION_GAP = 8;
    /** 单枚按钮、滑条、「完成」的宽：并列控件的 125%。 */
    public static final int WIDE_WIDTH = OPTION_WIDTH * 5 / 4;

    /** 一行怎么摆：一到两枚并列，或单枚按哪种宽度放。 */
    private enum Mode {
        PAIR, WIDE, FULL, HALF, CENTERED
    }

    private static final class Row {
        private final RowWidget left;
        private final RowWidget right;
        private final Mode mode;

        Row(RowWidget left, RowWidget right, Mode mode) {
            this.left = left;
            this.right = right;
            this.mode = mode;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final ScrollArea scroll = new ScrollArea();
    private int x;
    private int y;
    private int width;
    private int height;
    /** 上一次 layout 实际归位的那段行号 [from, to)；还没排过时 to 为负，先全量参与命中。 */
    private int laidOutFrom;
    private int laidOutTo = -1;

    /** 并列一行：两枚选项控件。 */
    public RowBlock pair(RowWidget left, RowWidget right) {
        rows.add(new Row(left, right, Mode.PAIR));
        return this;
    }

    /** 单枚居中（滑条、落单的按钮、「完成」同宽）。 */
    public RowBlock wide(RowWidget widget) {
        rows.add(new Row(widget, null, Mode.WIDE));
        return this;
    }

    /** 单枚占左半格，右边留空（原版音乐页「声音/语音」那条）。 */
    public RowBlock half(RowWidget widget) {
        rows.add(new Row(widget, null, Mode.HALF));
        return this;
    }

    /** 单枚按选项宽度居中（原版「显示字幕」那一格）。 */
    public RowBlock centered(RowWidget widget) {
        rows.add(new Row(widget, null, Mode.CENTERED));
        return this;
    }

    /** 铺满块宽的一行，说明文字用。 */
    public RowBlock full(RowWidget widget) {
        rows.add(new Row(widget, null, Mode.FULL));
        return this;
    }

    /** 重排（改 GUI 缩放、窗口改大小都会）：旧控件全部丢掉，由界面重新建一批。 */
    public void clear() {
        rows.clear();
        laidOutTo = -1;
    }

    public int size() {
        return rows.size();
    }

    /**
     * 内容全铺开要占多高：给界面算那块板的高度用。
     * 必须是 PITCH 的整倍 —— 最后一行底下那 2 像素缝也算进去，
     * 否则算出来的可视行数少一行，全都放得下也会长出一根没用的滚动条。
     */
    public int contentHeight() {
        return rows.size() * PITCH;
    }

    /**
     * 每帧排版一次：坐标既决定画在哪儿，也决定点哪儿，两边不可能对不上。
     * 出槽时右边让出滚动条的宽度，字不会被柱子压住。
     */
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = Math.max(PITCH, height);
        scroll.layout(x, y, width, this.height, PITCH);
        scroll.setRows(rows.size());
        int usable = width - (scroll.needsScroll() ? scroll.reservedWidth() : 0);
        int option = Math.min(OPTION_WIDTH, (usable - OPTION_GAP) / 2);
        int pairWidth = option * 2 + OPTION_GAP;
        int wide = Math.min(WIDE_WIDTH, usable);
        int first = scroll.first();
        laidOutFrom = first;
        laidOutTo = first;
        while (laidOutTo < rows.size() && (laidOutTo - first) * PITCH < this.height) {
            laidOutTo++;
        }
        // 全量归位（含滑出框外的行）：画与点仍只认下面 laidOutFrom..laidOutTo 那几行，
        // 但输入框掉不掉焦点读的是坐标，留在框外的行若还挂着上一次的陈位置，别处的一下点击会被误认成点它
        for (int index = 0; index < rows.size(); index++) {
            int rowTop = y + (index - first) * PITCH;
            int pairLeft = x + (usable - pairWidth) / 2;
            Row row = rows.get(index);
            switch (row.mode) {
                case PAIR -> {
                    row.left.setBounds(pairLeft, rowTop, option);
                    row.right.setBounds(pairLeft + option + OPTION_GAP, rowTop, option);
                }
                case HALF -> row.left.setBounds(pairLeft, rowTop, option);
                case CENTERED -> row.left.setBounds(x + (usable - option) / 2, rowTop, option);
                case FULL -> row.left.setBounds(x, rowTop, usable);
                default -> row.left.setBounds(x + (usable - wide) / 2, rowTop, wide);
            }
        }
    }

    /** 点槽优先：按在滚动条上就不该顺手改了一个开关。 */
    public boolean click(int mouseX, int mouseY, int button) {
        if (scroll.press(mouseX, mouseY)) {
            return true;
        }
        // 按下标走而不是迭代视图：动作里可能当场重排（换语言就 rebuild），视图会立刻失效
        for (int index = visibleFrom(); index < visibleTo(); index++) {
            Row row = rows.get(index);
            if (row.left.click(mouseX, mouseY) || row.right != null && row.right.click(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean drag(int mouseX, int mouseY, int button) {
        for (int index = visibleFrom(); index < visibleTo(); index++) {
            Row row = rows.get(index);
            if (row.left.drag(mouseX, mouseY, button)
                    || row.right != null && row.right.drag(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public void release(int button) {
        for (Row row : rows) {
            row.left.release(button);
            if (row.right != null) {
                row.right.release(button);
            }
        }
        scroll.release();
    }

    /** 滚轮只在内容框里生效；槽还没长出来时不吃掉滚动。 */
    public boolean wheel(int mouseY, double amount) {
        if (!scroll.needsScroll() || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll.scroll(amount);
        return true;
    }

    /** 光标指着的那枚控件的悬浮说明（只看框里看得见的那几行，坐标是陈的行不该冒泡）。 */
    public WidgetTip tipAt(int mouseX, int mouseY) {
        for (int index = visibleFrom(); index < visibleTo(); index++) {
            Row row = rows.get(index);
            WidgetTip tip = row.left.tipAt(mouseX, mouseY);
            if (tip != null) {
                return tip;
            }
            if (row.right != null && (tip = row.right.tipAt(mouseX, mouseY)) != null) {
                return tip;
            }
        }
        return null;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = visibleFrom(); index < visibleTo(); index++) {
            Row row = rows.get(index);
            row.left.render(graphics, mouseX, mouseY);
            if (row.right != null) {
                row.right.render(graphics, mouseX, mouseY);
            }
        }
        scroll.render(graphics, mouseX, mouseY);
    }

    /** 看得见的那段行号：滑出框外的行没被 layout 归位，坐标是陈的，既不该画也不该点。 */
    private int visibleFrom() {
        return laidOutTo < 0 ? 0 : laidOutFrom;
    }

    private int visibleTo() {
        return laidOutTo < 0 ? rows.size() : laidOutTo;
    }
}
