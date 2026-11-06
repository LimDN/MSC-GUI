package dev.msc.ui;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * GLFW 窗口 + 主循环 + 界面栈。
 *
 * GUI scale 按原版自动档的思路挑：取一个整数倍，让逻辑可视区不矮于 427x240
 * （再小原版的排布就散了），同时不超过 3 倍，避免高分屏上按钮小到点不准。
 */
public final class Window implements AutoCloseable {
    private static final int MIN_LOGICAL_WIDTH = 427;
    private static final int MIN_LOGICAL_HEIGHT = 240;
    private static final int MAX_GUI_SCALE = 3;

    private final long handle;
    private final GuiGraphics graphics;
    private final Deque<Screen> screens = new ArrayDeque<>();
    private final String dumpPath;
    private final String fontDumpPath;
    private final int dumpFrame;
    private final int maxFrames;
    /** 离屏出图时钉住光标（逻辑像素）：不钉的话截图里谁被悬停取决于物理鼠标，版面没法复现比对。 */
    private int[] pinnedCursor;
    /** 自检用的合成点击（逻辑像素）：默认在出图那一帧的前一帧按下去，图里看到的就是点完之后的状态。 */
    private final int[] click;
    /** 合成点击落在第几帧；单独可调，是为了拍「点过之后隔一会儿」的样子（悬浮 tips 的淡出）。 */
    private final int clickFrame;
    /** 第二下合成点击：验「先点输入框、再点别处」这类连招，一下载不进去。 */
    private final int[] click2;
    private final int click2Frame;
    /** 第三下：验「选中条目 → 按删除 → 在警告弹窗上点继续」这类三步流程，两下还是不够用。 */
    private final int[] click3;
    private final int click3Frame;
    private final boolean hidden;
    private int cursorX = -1;
    private int cursorY = -1;
    /** 按住的按键（GLFW 编号），-1 = 没按。按住期间的位移要转成 mouseDragged 给滚动条和滑条用。 */
    private int heldButton = -1;
    /** 世界画面的光标捕获：捕获后鼠标不再点界面，位移从 takeMouseDelta() 取。 */
    private boolean cursorCaptured;
    private double capturedX;
    private double capturedY;
    private double mouseDX;
    private double mouseDY;
    private double seconds;
    private double lastFrame;

    private Window(long handle, MscFont font, boolean hidden) {
        this.handle = handle;
        this.graphics = new GuiGraphics(font);
        this.dumpPath = System.getProperty("msc.ui.dump");
        this.fontDumpPath = System.getProperty("msc.ui.fontdump");
        // 第 1 帧的缓冲在首次 swap 后读回来是全黑，所以出图从第 2 帧起；带动画的界面用这个参数挑一帧有代表性的
        this.dumpFrame = Integer.getInteger("msc.ui.dump-frame", 2);
        this.maxFrames = Integer.getInteger("msc.ui.frames", 0);
        this.hidden = hidden;
        this.pinnedCursor = parsePoint("msc.ui.cursor", System.getProperty("msc.ui.cursor"));
        this.click = parsePoint("msc.ui.click", System.getProperty("msc.ui.click"));
        this.clickFrame = Integer.getInteger("msc.ui.click-frame", Math.max(1, dumpFrame - 1));
        this.click2 = parsePoint("msc.ui.click2", System.getProperty("msc.ui.click2"));
        this.click2Frame = Integer.getInteger("msc.ui.click2-frame", Math.max(1, dumpFrame - 1));
        this.click3 = parsePoint("msc.ui.click3", System.getProperty("msc.ui.click3"));
        this.click3Frame = Integer.getInteger("msc.ui.click3-frame", Math.max(1, dumpFrame - 1));
    }

    private static int[] parsePoint(String property, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            System.err.println("[MSC] " + property + " 要写成 x,y（逻辑像素），收到 \"" + value + "\"，忽略");
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException notNumbers) {
            System.err.println("[MSC] " + property + " 不是两个整数: " + value + "，忽略");
            return null;
        }
    }

    public static Window open(int requestedWidth, int requestedHeight) {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW 初始化失败: " + GLFW.glfwGetVersionString());
        }
        GLFW.glfwDefaultWindowHints();
        boolean hidden = Boolean.getBoolean("msc.ui.hidden");
        if (hidden) {
            // 离屏出图用：不出窗口也能拿到上下文，版面可以自己核对完再给人看
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        }
        long handle = GLFW.glfwCreateWindow(requestedWidth, requestedHeight, "MSC", 0L, 0L);
        if (handle == MemoryUtil.NULL) {
            throw new IllegalStateException("创建窗口失败（本机无法取得 OpenGL 上下文）");
        }
        GLFW.glfwMakeContextCurrent(handle);
        // 不激活能力表就调 GL，LWJGL 会直接在 native 层 abort 整个 JVM
        org.lwjgl.opengl.GL.createCapabilities();
        GLFW.glfwSwapInterval(1);
        System.out.println("[MSC] GL = " + GL11.glGetString(GL11.GL_VERSION)
                + " / " + GL11.glGetString(GL11.GL_RENDERER));

        MscFont font = MscFont.create(MinecraftAssets.shared());
        System.out.println("[MSC] 字体 = " + font.describe());
        return new Window(handle, font, hidden);
    }

    /** 逻辑尺寸 = 设备像素 ÷ GUI scale，界面层只见逻辑像素。 */
    public int logicalWidth() {
        return deviceWidth() / guiScale();
    }

    public int logicalHeight() {
        return deviceHeight() / guiScale();
    }

    public int deviceWidth() {
        return framebuffer(0);
    }

    public int deviceHeight() {
        return framebuffer(1);
    }

    /**
     * 宿主钉死了倍数就用那个数（视频设置界面改完立刻生效）；
     * auto 时两个方向都要满足最小可视区，所以取两者允许的最大倍数里更小的那个。
     */
    public int guiScale() {
        String pinned = UiPreferences.get().guiScaleOverride();
        if (pinned != null && !pinned.equals("auto")) {
            try {
                int value = Integer.parseInt(pinned.trim());
                if (value >= 1 && value <= MAX_GUI_SCALE) {
                    return value;
                }
            } catch (NumberFormatException malformed) {
                // 设置文件被人手改坏了就退回自动档，别让整个界面按 0 缩放
                System.err.println("[MSC] guiScale 不在 1..3 内：" + pinned + "，按 auto 处理");
            }
        }
        int byWidth = Math.min(MAX_GUI_SCALE, Math.max(1, deviceWidth() / MIN_LOGICAL_WIDTH));
        int byHeight = Math.min(MAX_GUI_SCALE, Math.max(1, deviceHeight() / MIN_LOGICAL_HEIGHT));
        return Math.min(byWidth, byHeight);
    }

    private int framebuffer(int dimension) {
        // 两个出参必须是各自的缓冲：LWJGL 按地址写，同一缓冲传两次会覆盖掉一个值
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(handle, width, height);
        return dimension == 0 ? width[0] : height[0];
    }

    public void push(Screen screen) {
        screen.show(this, logicalWidth(), logicalHeight());
        screens.push(screen);
    }

    /** 返回上一级；栈底是主界面，不再往外弹。 */
    public void pop() {
        if (screens.size() > 1) {
            screens.pop();
            Screen revealed = screens.peek();
            if (revealed != null) {
                revealed.onResume();
            }
        } else {
            GLFW.glfwSetWindowShouldClose(handle, true);
        }
    }

    /** 退出游戏：走和点窗口 × 一样的路 —— 主循环结束，由 MscClient 收尾停服再结束进程。 */
    public void exit() {
        GLFW.glfwSetWindowShouldClose(handle, true);
    }

    /** 离屏出图时不抓鼠标：没有真窗口，抓了也读不到位移。 */
    public boolean hidden() {
        return hidden;
    }

    /**
     * 世界画面用：捕获光标后鼠标只报位移、不点界面；退出画面时必须放回。
     * 捕获位置一起记下来，第一帧的位移才不会是一个从 0 到当前坐标的跳跃。
     */
    public void setCursorCaptured(boolean captured) {
        if (captured == cursorCaptured) {
            return;
        }
        cursorCaptured = captured;
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR,
                captured ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(handle, x, y);
        capturedX = x[0];
        capturedY = y[0];
        mouseDX = 0;
        mouseDY = 0;
    }

    public boolean cursorCaptured() {
        return cursorCaptured;
    }

    /** 当前按着的键（GLFW 编号）：世界画面每帧轮询，不走回调。 */
    public boolean keyDown(int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    /** 捕获期间这一帧的鼠标位移（设备像素）；取完清零。 */
    public double[] takeMouseDelta() {
        double[] delta = {mouseDX, mouseDY};
        mouseDX = 0;
        mouseDY = 0;
        return delta;
    }

    public void run() {
        installCallbacks();
        int frames = 0;
        while (!GLFW.glfwWindowShouldClose(handle)) {
            double now = GLFW.glfwGetTime();
            if (lastFrame != 0) {
                seconds += now - lastFrame;
            }
            lastFrame = now;
            GLFW.glfwPollEvents();
            refreshCursor();

            Screen screen = screens.peek();
            // 合成点击默认排在出图前一帧：拍到的就是点完之后的样子（点了翻页也拍得到新页）。
            // 必须提前一整帧而不是同帧 —— swap 之后读回来的像素是上一帧画的，同帧点完再拍只会拍到点之前。
            screen = clickAt(screen, click, clickFrame, frames);
            screen = clickAt(screen, click2, click2Frame, frames);
            screen = clickAt(screen, click3, click3Frame, frames);
            // 按住不放的那一段位移：GLFW 只在动鼠标时报坐标，不转成 mouseDragged 界面就只会一跳一跳
            if (screen != null && heldButton >= 0) {
                screen.mouseDragged(cursorX, cursorY, heldButton);
            }
            if (screen != null) {
                screen.tick();
                int logicalWidth = logicalWidth();
                int logicalHeight = logicalHeight();
                // GUI 缩放一改，逻辑像素就换一套：不重排的话界面按旧宽度画，鼠标却按新坐标报
                if (screen.width() != logicalWidth || screen.height() != logicalHeight) {
                    screen.resize(logicalWidth, logicalHeight);
                }
                graphics.begin(deviceWidth(), deviceHeight(), logicalWidth, logicalHeight, guiScale());
                screen.render(graphics, cursorX, cursorY, seconds);
            }
            GLFW.glfwSwapBuffers(handle);
            frames++;

            // 截图和图集导出同一帧做，两边对得上才说明纹理没滞后
            if (frames == dumpFrame && (dumpPath != null || fontDumpPath != null)) {
                if (dumpPath != null) {
                    savePng(dumpPath);
                }
                if (fontDumpPath != null) {
                    dumpFont();
                }
                if (maxFrames == 0) {
                    GLFW.glfwSetWindowShouldClose(handle, true);
                }
            }
            if (maxFrames > 0 && frames >= maxFrames) {
                break;
            }
        }
    }

    /**
     * 到点的那一下合成点击：光标一起挪过去（点哪儿就是哪儿），按下再松手，
     * 返回点完之后栈顶的那面界面 —— 点完跳页的话，拍到的就是新页。
     */
    private Screen clickAt(Screen screen, int[] point, int frame, int frames) {
        if (point == null || frames + 1 != frame || screen == null) {
            return screen;
        }
        pinnedCursor = new int[] {point[0], point[1]};
        cursorX = point[0];
        cursorY = point[1];
        clickThrough(screen, cursorX, cursorY, 0);
        screen.mouseReleased(cursorX, cursorY, 0);
        return screens.peek();
    }

    /**
     * 一次按下：先让没被点中的输入框掉焦点（点按钮、拖滑块、按滚动条、点空白都算点了别处），
     * 再交给界面。放在这里是因为焦点只归 {@link GuiTextField} 一家管，界面各自清一遍迟早会有人忘。
     */
    private static void clickThrough(Screen screen, int mouseX, int mouseY, int button) {
        GuiTextField.releaseFocusOutside(mouseX, mouseY);
        screen.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 刷新逻辑鼠标坐标。隐藏窗口读不到真实鼠标，物理光标一飘进按钮，截图就会随机出现悬停态，
     * 所以离屏出图默认钉在屏幕外；要核对悬停样式就用 `-Dmsc.ui.cursor=x,y`。
     */
    private void refreshCursor() {
        if (pinnedCursor != null) {
            cursorX = pinnedCursor[0];
            cursorY = pinnedCursor[1];
            return;
        }
        if (hidden) {
            cursorX = -1;
            cursorY = -1;
            return;
        }
        if (cursorCaptured) {
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(handle, x, y);
            mouseDX += x[0] - capturedX;
            mouseDY += y[0] - capturedY;
            capturedX = x[0];
            capturedY = y[0];
            cursorX = -1;
            cursorY = -1;
            return;
        }
        // 两个出参必须是各自的缓冲：同一缓冲传两次会覆盖掉一个值
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(handle, x, y);
        cursorX = (int) (x[0] / guiScale());
        cursorY = (int) (y[0] / guiScale());
    }

    private void installCallbacks() {
        GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            Screen screen = screens.peek();
            if (screen == null) {
                return;
            }
            if (action == GLFW.GLFW_RELEASE) {
                // 松手要先告诉界面：拇指/滑块得知道拖到此结束了，不然一直接管鼠标
                if (heldButton == button) {
                    heldButton = -1;
                    refreshCursor();
                    screen.mouseReleased(cursorX, cursorY, button);
                }
                return;
            }
            if (action != GLFW.GLFW_PRESS) {
                return;
            }
            heldButton = button;
            refreshCursor();
            if (cursorCaptured && screen.mouseButton(button, action)) {
                return;
            }
            clickThrough(screen, cursorX, cursorY, button);
        });
        GLFW.glfwSetScrollCallback(handle, (window, xOffset, yOffset) -> {
            Screen screen = screens.peek();
            if (screen != null) {
                refreshCursor();
                screen.mouseScrolled(cursorX, cursorY, yOffset);
            }
        });
        GLFW.glfwSetDropCallback(handle, (window, count, names) -> {
            if (screens.isEmpty()) {
                return;
            }
            List<String> paths = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                paths.add(GLFWDropCallback.getName(names, index));
            }
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(handle, x, y);
            int scale = Math.max(1, graphics.guiScale());
            screens.peek().filesDropped(paths, (int) (x[0] / scale), (int) (y[0] / scale));
        });
        GLFW.glfwSetKeyCallback(handle, (window, key, scanCode, action, mods) -> {
            if (action != GLFW.GLFW_PRESS) {
                return;
            }
            // 输入框的剪贴板快捷键：Ctrl+C/V/X。剪切没有选区，按「全选剪切」处理。
            if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                switch (key) {
                    case GLFW.GLFW_KEY_C -> {
                        String text = GuiTextField.focusedSelectedText();
                        if (text.isEmpty()) {
                            text = GuiTextField.focusedText();
                        }
                        if (text != null && !text.isEmpty()) {
                            GLFW.glfwSetClipboardString(handle, text);
                        }
                        return;
                    }
                    case GLFW.GLFW_KEY_V -> {
                        String clip = GLFW.glfwGetClipboardString(handle);
                        if (clip != null) {
                            GuiTextField.insertIntoFocused(clip);
                        }
                        return;
                    }
                    case GLFW.GLFW_KEY_X -> {
                        String text = GuiTextField.focusedSelectedText();
                        if (text.isEmpty()) {
                            text = GuiTextField.focusedText();
                            if (text != null && !text.isEmpty()) {
                                GLFW.glfwSetClipboardString(handle, text);
                                GuiTextField.clearFocused();
                            }
                            return;
                        }
                        GLFW.glfwSetClipboardString(handle, text);
                        GuiTextField.deleteFocusedSelection();
                        return;
                    }
                    case GLFW.GLFW_KEY_A -> {
                        GuiTextField.selectAllFocused();
                        return;
                    }
                    default -> {
                        // 其它 Ctrl 组合照旧交给界面
                    }
                }
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // 栈底那一面（主界面）先自己拦一手：在那里 pop() 等于不问一声就结束进程
                Screen top = screens.peek();
                if (top == null || !top.onEscape()) {
                    pop();
                }
                return;
            }
            Screen screen = screens.peek();
            if (screen != null) {
                screen.keyPressed(key);
            }
        });
        GLFW.glfwSetCharCallback(handle, (window, codepoint) -> {
            Screen screen = screens.peek();
            if (screen != null) {
                screen.keyChar((char) codepoint);
            }
        });
    }

    /** 把帧缓冲存成 PNG：没有肉眼盯着的场合，版面也要能自己核对。 */
    public void savePng(String path) {
        int width = deviceWidth();
        int height = deviceHeight();
        // 一帧的像素量级是几 MB，走 MemoryStack 会爆栈缓冲，直接分配
        ByteBuffer captured = Sprite.allocate(width * height * 3);
        ByteBuffer flipped = Sprite.allocate(width * height * 3);
        try {
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, captured);
            for (int row = 0; row < height; row++) {
                int source = (height - 1 - row) * width * 3;
                for (int column = 0; column < width * 3; column++) {
                    flipped.put(row * width * 3 + column, captured.get(source + column));
                }
            }
            boolean written = org.lwjgl.stb.STBImageWrite.stbi_write_png(
                    Path.of(path).toAbsolutePath().toString(), width, height, 3, flipped, width * 3);
            // 出图时刻的界面时钟一起打出来：淡出、闪烁这类按秒走的效果，只看第几帧判断不了过了多久
            System.out.println("[MSC] 界面截图 " + (written ? "已写 " : "写入失败 ") + path
                    + " (" + width + "x" + height + ", t="
                    + String.format(java.util.Locale.ROOT, "%.2fs", seconds) + ")");
        } finally {
            MemoryUtil.memFree(captured);
            MemoryUtil.memFree(flipped);
        }
    }

    /** 图集原始像素 + 逐码位定位：缺字到底是没烘进去、还是纹理里没这张字，看这两样就能分开。 */
    private void dumpFont() {
        TrueTypeFont font = graphics.font().latinFont();
        try {
            font.writeAtlasPng(fontDumpPath);
            System.out.println("[MSC] 字形图集已写 " + fontDumpPath + " (" + font.atlasSize() + "x"
                    + font.atlasSize() + ")");
        } catch (java.io.IOException failed) {
            System.err.println("[MSC] 图集导出失败: " + failed);
        }
        System.out.print("[MSC] 字形定位表（按图集打包顺序）\n" + font.glyphTable());
    }

    @Override
    public void close() {
        GLFW.glfwDestroyWindow(handle);
        GLFW.glfwTerminate();
    }
}
