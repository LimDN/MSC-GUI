package dev.msc.ui;

import org.lwjgl.glfw.GLFW;

/** 单行输入框：深色内槽 + 焦点描边 + 光标闪烁，和原版 GuiTextField 的观感一致。 */
public final class GuiTextField implements RowWidget {
    private static final int HEIGHT = 20;
    private static final int MAX_LENGTH = 96;
    /** 全局唯一吃键盘的那一栏：点了别处就得由它松手，也容不下两栏同时在闪。 */
    private static GuiTextField focusedField;

    private int x;
    private int y;
    private int width;
    private String text;
    private int cursor;
    /** 选区锚点：anchor..cursor 之间是选中段；相等就是没选。 */
    private int anchor;
    private boolean focused;
    private Runnable onSubmit;
    private String tooltip;
    /** 这句说明讲的是不是回不去的操作：气泡字色分黄 / 红两档。 */
    private boolean tooltipDanger;
    /** 只收数字的栏位（端口、视距这些），见 {@link #onlyDigits()}。 */
    private boolean digits;
    /** 一个全角标点都不收的栏位（主机名、端口），见 {@link #rejectsFullWidth()}。 */
    private boolean fullWidth;
    /** 丢掉的全角字符数：界面拿它对增量喊一句，不数一下就喊。 */
    private int fullWidthDrops;
    /** 上一次绘制用的画布：点选光标位置要靠它量字宽。 */
    private GuiGraphics lastGraphics;
    private boolean dragging;
    private boolean chrome = true;
    private boolean password;

    public GuiTextField(int x, int y, int width, String initial) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.text = initial;
        this.cursor = initial.length();
    }

    /** 表单要跟着滚动条走：坐标不是建的时候钉死的。绘制与命中都读这一套，不会错位。 */
    @Override
    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    /** 挂一行悬浮说明（这一栏到底填什么，指上去才说）。 */
    /** 聊天栏等外部自绘背景的输入框：只留文字、选区和光标，不画黑框。 */
    public GuiTextField password() {
        password = true;
        return this;
    }

    public GuiTextField transparentChrome() {
        chrome = false;
        return this;
    }

    public GuiTextField setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** 同上，但这一栏填错会带走东西（例如把世界目录指偏）：气泡用 §c 红字。 */
    public GuiTextField setDangerTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipDanger = true;
        return this;
    }

    /** 只收数字：端口、视距这类栏位不该让人填出一句话来。 */
    public GuiTextField onlyDigits() {
        this.digits = true;
        return this;
    }

    /**
     * 全角标点一个不吃：{@code ：}、{@code （}、{@code １} 在屏幕上和半角几乎一样，
     * 服务端与 DNS 却读不出，填错了人还以为是地址本身不通。宁可当场丢掉，并数下来让界面喊一句。
     */
    public GuiTextField rejectsFullWidth() {
        this.fullWidth = true;
        return this;
    }

    /** 这一栏累计丢过几个全角字符；界面只对增量说话。 */
    public int fullWidthDrops() {
        return fullWidthDrops;
    }

    /** 全角标点、全角字母数字（U+FF01..U+FF5E）、全角符号（U+FFE0..U+FFE5）与全角空格。 */
    private static boolean isFullWidth(char value) {
        return value >= 0xFF01 && value <= 0xFF5E || value >= 0xFFE0 && value <= 0xFFE5 || value == 0x3000;
    }

    @Override
    public WidgetTip tipAt(int mouseX, int mouseY) {
        return tooltip == null || tooltip.isEmpty() || !contains(mouseX, mouseY)
                ? null
                : new WidgetTip(this, tooltip, mouseX, mouseY, tooltipDanger);
    }

    /** 栅格里的行：RowBlock 只交光标，闪烁自己读单调钟，不让每一层界面再传一遍 seconds。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        render(graphics, System.nanoTime() / 1_000_000_000.0);
    }

    public void render(GuiGraphics graphics, double seconds) {
        lastGraphics = graphics;
        if (chrome) {
            graphics.fill(x, y, width, HEIGHT, focused ? 0xFFFFFFA0 : 0xFFA0A0A0);
            graphics.fill(x + 1, y + 1, width - 2, HEIGHT - 2, 0xFF000000);
        }
        graphics.scissor(x + 3, y + 2, width - 6, HEIGHT - 4);
        int start = Math.min(anchor, cursor);
        int end = Math.max(anchor, cursor);
        String shown = password ? "*".repeat(text.length()) : text;
        if (focused && start < end) {
            int left = x + 4 + graphics.textWidth(shown.substring(0, start));
            int selectedWidth = graphics.textWidth(shown.substring(start, end));
            graphics.fill(left, y + 4, selectedWidth, HEIGHT - 8, 0xFFFFFFFF);
        }
        graphics.text(shown, x + 4, y + 5, 0xFFE0E0E0);
        if (focused && start < end) {
            int left = x + 4 + graphics.textWidth(shown.substring(0, start));
            graphics.text(shown.substring(start, end), left, y + 5, 0xFF000080);
        }
        if (focused && (long) (seconds * 2.0) % 2 == 0) {
            graphics.fill(x + 4 + graphics.textWidth(shown.substring(0, cursor)), y + 4, 1, HEIGHT - 8, 0xFFE0E0E0);
        }
        graphics.endScissor();
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
    }

    /** 栅格里的行：RowBlock 交进来的就是这次点击的坐标，指到框上即抢焦点。 */
    @Override
    public boolean click(int mouseX, int mouseY) {
        return mouseClicked(mouseX, mouseY);
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        boolean hit = contains(mouseX, mouseY);
        setFocused(hit);
        if (hit) {
            cursor = indexAt(mouseX);
            anchor = cursor;
            dragging = true;
        }
        return hit;
    }

    /** 按住拖到哪儿选到哪儿；没按住就什么都不做。 */
    @Override
    public boolean drag(int mouseX, int mouseY, int button) {
        if (!focused || !dragging) {
            return false;
        }
        cursor = indexAt(mouseX);
        return true;
    }

    @Override
    public void release(int button) {
        dragging = false;
    }

    /** 把鼠标 x 换成最近的字符边界；还没画过就落到行尾。 */
    private int indexAt(int mouseX) {
        if (lastGraphics == null) {
            return text.length();
        }
        int local = mouseX - (x + 4);
        if (local <= 0) {
            return 0;
        }
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index <= text.length(); index++) {
            int width = lastGraphics.textWidth(text.substring(0, index));
            int distance = Math.abs(width - local);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    /**
     * 点在任何非输入框的地方 —— 按钮、滑块、滚动条、空白 —— 都要让输入框松手。
     * 焦点记在这一类上而不是各个界面里，所以点击入口调一次就全覆盖，不会有哪页忘了清。
     * 被点中的那一栏随后自己再抢回去。
     */
    public static void releaseFocusOutside(int mouseX, int mouseY) {
        if (focusedField != null && !focusedField.contains(mouseX, mouseY)) {
            focusedField.setFocused(false);
        }
    }

    public void charTyped(char value) {
        if (!focused || value < 0x20 || value == 0x7F || text.length() >= MAX_LENGTH) {
            return;
        }
        if (digits && (value < '0' || value > '9')) {
            return;
        }
        if (fullWidth && isFullWidth(value)) {
            fullWidthDrops++;
            return;
        }
        if (hasSelection()) {
            deleteSelection();
        }
        text = text.substring(0, cursor) + value + text.substring(cursor);
        cursor++;
        anchor = cursor;
    }

    public boolean keyChar(char value) {
        if (!focused) {
            return false;
        }
        charTyped(value);
        return true;
    }

    public boolean keyPressed(int key) {
        if (!focused) {
            return false;
        }
        if ((key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) && hasSelection()) {
            deleteSelection();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    anchor = cursor;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                text = text.substring(0, cursor) + text.substring(Math.min(cursor + 1, text.length()));
                anchor = cursor;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                anchor = cursor;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
                anchor = cursor;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                anchor = cursor;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                anchor = cursor;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit != null) {
                    onSubmit.run();
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public String value() {
        return text;
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

    public GuiTextField setValue(String value) {
        text = value;
        cursor = value.length();
        anchor = cursor;
        return this;
    }

    public void setFocused(boolean focused) {
        // 全局只容一栏吃键盘：抢焦点时把上一栏的描边和闪烁一起收掉，别留两栏同时在闪
        if (focused && focusedField != null && focusedField != this) {
            focusedField.focused = false;
        }
        this.focused = focused;
        if (focused) {
            focusedField = this;
        } else if (focusedField == this) {
            focusedField = null;
        }
    }

    public boolean focused() {
        return focused;
    }

    // 剪贴板：窗口只认「当前吃键盘的那一栏」，这些静态口子把焦点栏的文本交出去/塞回来。

    public static String focusedText() {
        return focusedField == null ? null : focusedField.text;
    }

    public static void insertIntoFocused(String value) {
        if (focusedField != null) {
            focusedField.insert(value);
        }
    }

    public static void selectAllFocused() {
        if (focusedField != null) {
            focusedField.anchor = 0;
            focusedField.cursor = focusedField.text.length();
        }
    }

    public static String focusedSelectedText() {
        return focusedField == null ? "" : focusedField.selectedText();
    }

    public static void deleteFocusedSelection() {
        if (focusedField != null) {
            focusedField.deleteSelection();
        }
    }

    private boolean hasSelection() {
        return anchor != cursor;
    }

    private String selectedText() {
        int start = Math.min(anchor, cursor);
        int end = Math.max(anchor, cursor);
        return text.substring(start, end);
    }

    private void deleteSelection() {
        int start = Math.min(anchor, cursor);
        int end = Math.max(anchor, cursor);
        if (start == end) {
            return;
        }
        text = text.substring(0, start) + text.substring(end);
        cursor = start;
        anchor = start;
    }

    public static void clearFocused() {
        if (focusedField != null) {
            focusedField.text = "";
            focusedField.cursor = 0;
        }
    }

    /** 粘贴进来的内容去掉换行、按剩余长度截断；数字栏只留数字。 */
    private void insert(String value) {
        if (!focused || value == null) {
            return;
        }
        if (hasSelection()) {
            deleteSelection();
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        if (digits) {
            cleaned = cleaned.replaceAll("[^0-9]", "");
        }
        if (fullWidth) {
            StringBuilder kept = new StringBuilder(cleaned.length());
            for (char c : cleaned.toCharArray()) {
                if (isFullWidth(c)) {
                    fullWidthDrops++;
                } else {
                    kept.append(c);
                }
            }
            cleaned = kept.toString();
        }
        int room = MAX_LENGTH - text.length();
        if (room <= 0) {
            return;
        }
        if (cleaned.length() > room) {
            cleaned = cleaned.substring(0, room);
        }
        if (cleaned.isEmpty()) {
            return;
        }
        text = text.substring(0, cursor) + cleaned + text.substring(cursor);
        cursor += cleaned.length();
        anchor = cursor;
    }

    public void onSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
    }
}
