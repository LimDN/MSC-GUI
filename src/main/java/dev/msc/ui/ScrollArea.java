package dev.msc.ui;

/**
 * 竖向滚动条 + 滚动位置。界面只交代「内容框在哪、一共几行、一行多高」，
 * 拇指尺寸、点槽翻页、拖拽、滚轮增量、上下 clamp 全在这里算 ——
 * 原来各界面自己写一遍 clamp，写漏一处就能滚进空白。
 *
 * 槽占满内容框的整个高度（不是一小截），拇指按「可见行 / 总行」的比例给长度：
 * 条目多的时候一眼能看出还剩多少没看。内容一屏放得下时不画槽，免得凭空多一根柱子。
 */
public final class ScrollArea {
    /** 槽宽（逻辑像素）：再细就点不中，再粗就抢了行文字的位。 */
    private static final int TRACK_WIDTH = 9;
    /** 拇指最短一截：再短就成了一条线，抓不住。 */
    private static final int THUMB_MIN_HEIGHT = 24;
    /** 滚轮一格滚几行。 */
    private static final int WHEEL_ROWS = 3;
    /** 拇指左右各留 2 像素槽底，看着像嵌在槽里。 */
    private static final int THUMB_INSET = 2;
    /** 拇指三档面：常态 / 指上去 / 按住。都比按钮面暗一档，免得这根柱子比行文字还抢眼。 */
    private static final int THUMB_FACE = 0xFF6E6E6E;
    private static final int THUMB_HOVERED = 0xFF8C8C8C;
    private static final int THUMB_GRABBED = 0xFF9D9D9D;

    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int rowHeight = 10;
    private int rows;
    private int first;
    /** 按下时鼠标在拇指内的偏移：没有它，一按拇指就跳到鼠标正中。 */
    private int grabOffset;
    private boolean grabbed;

    /** 内容框（不含槽）：槽画在这个框的右内侧，所以框宽要留出槽位。 */
    public void layout(int x, int y, int width, int height, int rowHeight) {
        this.contentX = x;
        this.contentY = y;
        this.contentWidth = width;
        this.contentHeight = height;
        this.rowHeight = Math.max(1, rowHeight);
        clamp();
    }

    public void setRows(int rows) {
        this.rows = Math.max(0, rows);
        clamp();
    }

    /** 最上面那行的行号，界面按它算每行的 y。 */
    public int first() {
        return first;
    }

    /** 内容框的高度：界面画剪刀和底板要照同一个值，别自己再算一遍。 */
    public int height() {
        return contentHeight;
    }

    public int visibleRows() {
        return Math.max(1, contentHeight / rowHeight);
    }

    public boolean needsScroll() {
        return rows > visibleRows();
    }

    /** 选中行要跟着滚动才看得见时用它（列表点选、键盘切行都靠这个保持可见）。 */
    public void reveal(int row) {
        int visible = visibleRows();
        if (row < first) {
            first = row;
        } else if (row >= first + visible) {
            first = row - visible + 1;
        }
        clamp();
    }

    /** 滚轮：MC 的惯例是向上为正，一格三行。 */
    public void scroll(double amount) {
        first -= (int) Math.signum(amount) * WHEEL_ROWS;
        clamp();
    }

    /**
     * 按在槽上：命中拇指就开始拖，否则朝点的那一侧翻一屏。
     * 返回 true 表示这一下被滚动条吃掉了，界面不该再拿去选中行。
     */
    public boolean press(int mouseX, int mouseY) {
        if (!needsScroll()) {
            return false;
        }
        int trackX = trackX();
        if (mouseX < trackX - THUMB_INSET || mouseX > trackX + TRACK_WIDTH + THUMB_INSET) {
            return false;
        }
        if (mouseY < contentY || mouseY >= contentY + trackHeight()) {
            return false;
        }
        int top = thumbTop();
        int height = thumbHeight();
        if (mouseY >= top && mouseY < top + height) {
            grabbed = true;
            grabOffset = mouseY - top;
        } else {
            first += mouseY < top ? -visibleRows() : visibleRows();
            clamp();
        }
        return true;
    }

    public void drag(int mouseY) {
        if (!grabbed) {
            return;
        }
        int range = trackHeight() - thumbHeight();
        if (range <= 0) {
            return;
        }
        int position = Math.max(0, Math.min(range, mouseY - grabOffset));
        first = (int) Math.round((double) position / range * (rows - visibleRows()));
        clamp();
    }

    public void release() {
        grabbed = false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!needsScroll()) {
            return;
        }
        int trackX = trackX();
        int height = trackHeight();
        graphics.fill(trackX, contentY, TRACK_WIDTH, height, 0xFF101010);
        graphics.outline(trackX, contentY, TRACK_WIDTH, height, UiColors.EDGE);
        int top = thumbTop();
        int thumbHeight = thumbHeight();
        boolean hovered = mouseX >= trackX && mouseX < trackX + TRACK_WIDTH
                && mouseY >= top && mouseY < top + thumbHeight;
        int face = grabbed ? THUMB_GRABBED : hovered ? THUMB_HOVERED : THUMB_FACE;
        graphics.fill(trackX + THUMB_INSET, top, TRACK_WIDTH - THUMB_INSET * 2, thumbHeight, face);
        graphics.fill(trackX + THUMB_INSET, top, TRACK_WIDTH - THUMB_INSET * 2, 1, 0xFFFFFFFF);
        graphics.fill(trackX + THUMB_INSET, top + thumbHeight - 1, TRACK_WIDTH - THUMB_INSET * 2, 1, 0x66000000);
    }

    /** 界面要把内容让开这么宽，别让行文字压在槽底下。 */
    public int reservedWidth() {
        return TRACK_WIDTH;
    }

    private int trackX() {
        return contentX + contentWidth - TRACK_WIDTH;
    }

    /** 槽贴着内容框上下沿，高度就是内容框高：滚动条要「长一些」，不是一小块。 */
    private int trackHeight() {
        return contentHeight;
    }

    private int thumbHeight() {
        if (rows <= 0) {
            return trackHeight();
        }
        double share = (double) Math.min(rows, visibleRows()) / rows;
        return Math.max(THUMB_MIN_HEIGHT, (int) Math.round(trackHeight() * share));
    }

    private int thumbTop() {
        int range = trackHeight() - thumbHeight();
        int spread = rows - visibleRows();
        if (spread <= 0 || range <= 0) {
            return contentY;
        }
        return contentY + (int) Math.round((double) first / spread * range);
    }

    private void clamp() {
        int last = Math.max(0, rows - visibleRows());
        first = Math.max(0, Math.min(last, first));
    }
}
