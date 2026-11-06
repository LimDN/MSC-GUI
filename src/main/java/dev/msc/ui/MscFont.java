package dev.msc.ui;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 界面用的合成字体，分工和 MC 自己一致：
 * 拉丁与常用标点用 MC 自带的 minecraft-seven 像素字体，汉字用 MC 自带的位图字库，
 * 字库缺页（本机 glyph_60 / glyph_CE 在盘上读不出来）才退回系统中文字体兜底。
 *
 * 两套来源都按「基线在第 16 图集像素」对齐，所以中英混排是一条线上去的，不是各排各的。
 */
public final class MscFont {
    /** 这段之内的码位 seven 有字模，之外的先找位图字库。 */
    private static final int LATIN_LIMIT = 0x100;
    /**
     * seven 的设计网格是 12 行 / 8 列一个 em：烘 24 正好一格 = 2x3 图集像素，笔画齐平，
     * GUI scale 1/2/3 下都落在整数上。22、23 试过，网格被切成半格，数字直接散架。
     */
    private static final int LATIN_BAKE = 24;
    /** 兜底字体没有像素网格可抄，按汉字墨迹（0.75 em）正好 16 像素反推 em。 */
    private static final int FALLBACK_BAKE = 20;

    private final TrueTypeFont latin;
    private final UnifontFont bitmap;
    private final TrueTypeFont fallback;
    private final String latinOrigin;
    private final String fallbackOrigin;

    private MscFont(TrueTypeFont latin, String latinOrigin, UnifontFont bitmap, TrueTypeFont fallback,
                    String fallbackOrigin) {
        this.latin = latin;
        this.latinOrigin = latinOrigin;
        this.bitmap = bitmap;
        this.fallback = fallback;
        this.fallbackOrigin = fallbackOrigin;
    }

    public static MscFont create(MinecraftAssets assets) {
        Optional<MinecraftAssets.Blob> bundled = assets.bundled("font/minecraft-seven.ttf");
        TrueTypeFont latin;
        String latinOrigin;
        if (bundled.isPresent()) {
            latin = TrueTypeFont.fromBytes(bundled.get().bytes(), 0, LATIN_BAKE, true);
            latinOrigin = bundled.get().origin();
        } else {
            // 连 MC 的拉丁字体都没带上时，退到系统字体里挑一个能显示拉丁的
            Path system = MinecraftAssets.discoverCjkFont().orElseThrow(() -> new IllegalStateException(
                    "内置 minecraft-seven.ttf 与系统字体均未找到，界面文字无法绘制"));
            latin = TrueTypeFont.fromFile(system, 0, FALLBACK_BAKE, false);
            latinOrigin = system.toString();
        }
        TrueTypeFont fallback = null;
        String fallbackOrigin = "none";
        Optional<Path> cjk = MinecraftAssets.discoverCjkFont();
        if (cjk.isPresent()) {
            try {
                // 兜底的是系统中文字体，本来就有灰阶，硬切只会把 16 像素的笔画削断
                fallback = TrueTypeFont.fromFile(cjk.get(), 0, FALLBACK_BAKE, false);
                fallbackOrigin = cjk.get().getFileName().toString();
            } catch (RuntimeException broken) {
                assets.noteFailure("fallback font unusable: " + cjk.get() + " (" + broken.getMessage() + ")");
            }
        }
        return new MscFont(latin, latinOrigin, new UnifontFont(assets), fallback, fallbackOrigin);
    }

    /** 每个码位现取现烘，所以取字和画字必须成对：取完要 flush，纹理里才有这一批字。 */
    public Glyph glyph(int codepoint) {
        if (codepoint < LATIN_LIMIT) {
            // 「强制使用Unicode字体」= 连拉丁也走 MC 位图字库（原版那个开关就是这个意思）。
            // 位图字库里没有的格（空白、非 ASCII 标点）再退回 seven，界面不会因此缺字。
            if (UiPreferences.get().forceUnicodeFont()) {
                Optional<Glyph> unicodeGlyph = bitmap.glyph(codepoint);
                if (unicodeGlyph.isPresent()) {
                    return unicodeGlyph.get();
                }
            }
            return latin.glyph(codepoint);
        }
        Optional<Glyph> bitmapGlyph = bitmap.glyph(codepoint);
        if (bitmapGlyph.isPresent()) {
            return bitmapGlyph.get();
        }
        return fallback != null ? fallback.glyph(codepoint) : latin.glyph(codepoint);
    }

    /** 图集像素单位下的文本宽度，调用方负责换算成逻辑单位。 */
    public int bakedWidth(String text) {
        int total = 0;
        for (int index = 0; index < text.length(); index++) {
            int codepoint = text.codePointAt(index);
            if (Character.isHighSurrogate(text.charAt(index))) {
                index++;
            }
            total += glyph(codepoint).advance();
        }
        return total;
    }

    /** 把这一趟新烘的字形推进纹理；位图字库在载入时就上传了，不用管。 */
    public void flush() {
        latin.flush();
        if (fallback != null) {
            fallback.flush();
        }
    }

    public String describe() {
        return "拉丁 = " + latinOrigin + "（烘 " + LATIN_BAKE + "px）/ 汉字 = MC 位图字库 / 兜底 = "
                + fallbackOrigin;
    }

    /** 版面核对用的原始读数：图集 PNG + 逐码位定位。 */
    public TrueTypeFont latinFont() {
        return latin;
    }
}
