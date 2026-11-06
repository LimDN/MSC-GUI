package dev.msc.ui;

import java.util.List;

/**
 * 一个界面。坐标一律是逻辑像素，鼠标坐标也是 —— 屏幕层不需要知道设备分辨率。
 */
public abstract class Screen {
    protected Window window;
    protected int width;
    protected int height;
    private final Notices notices = new Notices();
    private final Tooltips tooltips = new Tooltips();

    public void show(Window window, int width, int height) {
        this.window = window;
        this.width = width;
        this.height = height;
        init();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * 逻辑尺寸变了（改 GUI 缩放、窗口改大小都会）：按新尺寸重排一次。
     * 不重排的话，界面还按旧的宽度画，鼠标坐标却已经是新逻辑像素的，按钮就点不到了。
     */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    /** 布局构建。贴图上传必须在这里做（此时 GL 上下文已在当前线程）。 */
    protected void init() {
    }

    /** 左下角喊一句小提示：简洁英文，五秒后自己淡掉。 */
    protected void notice(String text) {
        notices.show(text);
    }

    /** 警告类提示：同一排里用 §c 标红，免得「这行是拦你的」和「这行只是出处」长得一样。 */
    protected void noticeWarn(String text) {
        notices.showWarn(text);
    }

    /** 每个界面在 render 末尾调一次，提示永远画在最上面。 */
    protected void renderNotices(GuiGraphics graphics, double seconds) {
        notices.render(graphics, noticeFloor(), seconds);
    }

    /**
     * 左下角浮条的底线。默认按整窗底部，由 {@link Notices} 自己让开主界面那两行原版文字；
     * 底下摆了两排按钮的页要报得更高一些 —— 浮条压在按钮上等于把按钮咬掉一条边。
     */
    protected int noticeFloor() {
        return height;
    }

    /**
     * 画完控件后调一次：把光标正指着的那枚控件交出的气泡浮出来（没有就传 null）。
     * 页头那行常驻说明就是这么挪进气泡的 —— 想知道的人指上去就有，别人面前页面还是干净的。
     */
    protected void renderTooltips(GuiGraphics graphics, WidgetTip tip, double seconds) {
        tooltips.update(tip, seconds);
        tooltips.render(graphics, seconds);
    }

    /**
     * 刚按下按钮或拖动滑条后调一次，把这次点击坐标上的气泡交进去：
     * 正是当前显示的那枚就开始倒计时，不是就什么都不做。
     */
    protected void pokeTooltips(WidgetTip underCursor) {
        tooltips.poke(underCursor);
    }

    /** 一串控件里第一个被光标指着的气泡；后面的不会盖住前面的。 */
    protected static WidgetTip tipAmong(List<? extends RowWidget> widgets, int mouseX, int mouseY) {
        for (RowWidget widget : widgets) {
            WidgetTip tip = widget.tipAt(mouseX, mouseY);
            if (tip != null) {
                return tip;
            }
        }
        return null;
    }

    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, double seconds);

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /** 按住不放拖动画的面板（滚动条拇指、滑条）靠这个连续收到坐标。 */
    public boolean mouseDragged(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }

    /** 滚轮：正值向下。返回 true 表示这页吃掉了这次滚动。 */
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int key) {
        return false;
    }

    /**
     * ESC 按下的自定义动作。默认 false = 交给窗口走「返回上一层」。
     * 只有栈底那一面需要拦：在那儿「返回上一层」等于直接结束进程。
     */
    public boolean onEscape() {
        return false;
    }

    /** 可打印字符走这里，和按键分开：输入法与大写 shift 才不会串。 */
    public boolean keyChar(char value) {
        return false;
    }

    /** 文件被拖进窗口：坐标是逻辑像素，落在哪个面板由界面自己判。 */
    public boolean filesDropped(List<String> paths, int mouseX, int mouseY) {
        return false;
    }

    /** 鼠标被捕获时的按键（世界画面用）；返回 true 表示这一下不交给普通点击。 */
    public boolean mouseButton(int button, int action) {
        return false;
    }

    /** 从更上层的界面返回时调用；列表类界面靠它重新读盘，免得显示陈旧数据。 */
    public void onResume() {
    }

    public void tick() {
    }

    /** 原版子界面那层泥土底 + 暗遮罩；主界面的 panorama 只属于主界面。内置缺这张图时退回纯色。 */
    protected void renderDirtBackground(GuiGraphics graphics) {
        UiTextures.texture(UiTextures.DIRT).ifPresentOrElse(dirt -> graphics.tiled(dirt, 0, 0, width, height, 32, 32),
                () -> graphics.fill(0, 0, width, height, 0xFF1A140F));
        // 遮罩照原版：上 0xC0000000 渐变到下 0xD0100D10。之前铺的是平的 0xB0，
        // 所以整屏比原版亮一档 —— 原版那两层黑是叠在贴图上的，不是贴图本身。
        graphics.gradient(0, 0, width, height, 0xC0000000, 0xD0100D10);
    }

    /** 控件与板边的内边距。 */
    protected static final int PANEL_PADDING = 4;

    /**
     * 设置类界面那块中板的宽度：并列的两枚选项按钮 + 一道缝 + 左右内边距
     * （见 RowBlock 的栅格），几页之间共用同一套栅格，换页时按钮不会一宽一窄地跳。
     * 窗口窄到放不下时按窗口收（见 panelWidth）。
     */
    public static final int PANEL_WIDTH = RowBlock.OPTION_WIDTH * 2 + RowBlock.OPTION_GAP + PANEL_PADDING * 2;

    protected int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 8);
    }

    protected int panelLeft() {
        return width / 2 - panelWidth() / 2;
    }

    /** 中板：半透明黑底 + 1 像素暗边，就是服务端列表那块板的样子，让按钮从泥土底上分出来。 */
    protected void renderPanel(GuiGraphics graphics, int top, int bottom) {
        int left = panelLeft();
        int panelWidth = panelWidth();
        graphics.fill(left, top, panelWidth, bottom - top, UiColors.PANEL);
        graphics.outline(left, top, panelWidth, bottom - top, UiColors.EDGE);
    }

    /** 控件块的左边缘与宽度：交给 RowBlock，两列才始终关于窗口中线对称。 */
    protected int blockLeft() {
        return panelLeft() + PANEL_PADDING;
    }

    protected int blockWidth() {
        return panelWidth() - PANEL_PADDING * 2;
    }

    /** 设置页的页头只有标题一行：原来跟在下面的那句说明改成贴在控件上的 tips，不再常驻占一行。 */
    protected void renderSettingsHeader(GuiGraphics graphics, String title) {
        graphics.textCentered(title, width / 2, 16, 8, UiColors.WHITE, true);
    }
}
