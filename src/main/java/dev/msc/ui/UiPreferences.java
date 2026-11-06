package dev.msc.ui;

/**
 * 控件层向宿主借的那六样：强制位图字库、悬浮提示开关、两个资源包路径、界面倍数、按钮声。
 *
 * <p>控件层不认识宿主的设置类，也不该认识 —— 它只按这个口子问话，宿主在启动时把一个
 * {@link Source} 递进来（MSC 那边是 {@code GlobalSettings} + {@code Sounds} 的薄适配器）。
 * 没递就用默认值：气泡开着、不强写字库、倍数走自动档、不读资源包、点击不出声。
 * 界面自检与离屏导出这些不起宿主的路径因此照样能画，不会因为"设置类没接"而空屏。</p>
 */
public final class UiPreferences {

    /** 宿主的回答。取值要现问现答：设置页里改了气泡开关，下一帧就得跟着变，不能启动时抄一份。 */
    public interface Source {
        boolean forceUnicodeFont();

        boolean tooltipsEnabled();

        /** 分号分隔的包目录或 .zip 路径，界面贴图按这个顺序覆盖。 */
        String uiResourcePacks();

        /** 同上，给游戏内材质预留。 */
        String gameResourcePacks();

        /** 钉死的界面倍数；空串或 {@code auto} 都表示让控件层自己按窗口尺寸算。 */
        String guiScaleOverride();

        void playClickSound();
    }

    /** 宿主没接进来时的回答：不强制字库、气泡开着、倍数自动、没有资源包、点击不出声。 */
    private static final Source DEFAULTS = new Source() {
        @Override
        public boolean forceUnicodeFont() {
            return false;
        }

        @Override
        public boolean tooltipsEnabled() {
            return true;
        }

        @Override
        public String uiResourcePacks() {
            return "";
        }

        @Override
        public String gameResourcePacks() {
            return "";
        }

        @Override
        public String guiScaleOverride() {
            return "auto";
        }

        @Override
        public void playClickSound() {
        }
    };

    private static volatile Source source = DEFAULTS;

    private UiPreferences() {
    }

    /** 宿主在打开第一个界面之前调用；传 null 等于收回，回到默认回答。 */
    public static void source(Source next) {
        source = next == null ? DEFAULTS : next;
    }

    public static Source get() {
        return source;
    }
}
