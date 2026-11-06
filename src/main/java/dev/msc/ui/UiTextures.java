package dev.msc.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 界面共用的贴图：GL 上下文就绪后按需上传一次，取不到就交空让调用方回落到程序化绘制。 */
public final class UiTextures {
    /** 原版菜单那层泥土底图（1.12 的 options_background.png，16x16 一块）。 */
    public static final String DIRT = "textures/gui/options_background.png";
    /** 按钮三态，用户从 gui.png 里切好的整张 200x20。 */
    public static final String BUTTON_LIGHT = "textures/gui/button_light.png";
    public static final String BUTTON_HOVER = "textures/gui/button_hover.png";
    public static final String BUTTON_DARK = "textures/gui/button_dark.png";
    /**
     * 进度条：给的那张 182x10 其实是上下两条 —— 上半 5 行是空槽的底，下半 5 行才是实心条。
     * 之前把整张当填充铺了两层，所以看着像叠重了。
     */
    public static final String PROGRESS_BACK = "textures/gui/progress_bar_back.png";
    public static final String PROGRESS_FILL = "textures/gui/progress_bar_fill.png";
    /** 运行台右上角那个图标（用户给的 24x24）：切「焦点视角」和「服务端控制台」两面。 */
    public static final String FOCUS_VIEW = "textures/gui/focus_view.png";
    /** 主界面那颗语言方块上的图标（15x15）：原版就是一个地球加两行字，按钮只留图标位。 */
    public static final String LANGUAGE = "textures/gui/language.png";
    /**
     * 延迟格：原版给的五档加一个「不知道」，每张 10x8。档位号就是条数，ping_5 最快。
     * {@code pinging.png} 是一张 10x39 的竖排动画：五帧，每帧占 8 行（前 7 行是阶梯，第 8 行是帧间那
     * 一条近透明的分隔），最后一帧只剩 7 行 —— 取的时候按纹理高度夹住，别读到纹理外面去。
     */
    public static final String[] PING_LEVELS = {
            "textures/gui/ping_1.png", "textures/gui/ping_2.png", "textures/gui/ping_3.png",
            "textures/gui/ping_4.png", "textures/gui/ping_5.png"};
    public static final String PING_UNKNOWN = "textures/gui/ping_unknown.png";
    public static final String PINGING = "textures/gui/pinging.png";
    /** 延迟格与 pinging 每一帧的格子尺寸（逻辑像素）。 */
    public static final int PING_WIDTH = 10;
    public static final int PING_HEIGHT = 8;
    /** pinging 那五帧的帧距：8 行一格，格与格之间那 1 行是分隔。 */
    public static final int PINGING_FRAME_PITCH = 8;
    public static final int PINGING_FRAMES = 5;

    private static final Map<String, Optional<Sprite>> LOADED = new HashMap<>();

    private UiTextures() {
    }

    public static Optional<Sprite> texture(String logicalPath) {
        return LOADED.computeIfAbsent(logicalPath,
                path -> TextureLoader.fromBundle(MinecraftAssets.shared(), path));
    }

    /** 资源包换过之后清缓存：下一次取图会重新按新包解析（GL 侧旧纹理随进程回收）。 */
    public static void invalidate() {
        LOADED.clear();
    }
}
