package dev.msc.ui;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

/**
 * MC 自带的位图汉字库：glyph_XX.png 一张 256x256，按码位高字节分页，每页 256 格，
 * 一格 16x16，格序号就是码位低字节（一行 16 格，行优先）。
 *
 * 页图里 RGB 有黑有白（不同区块来源不一致），只有 alpha 是可信的字模，
 * 所以取 alpha 通道传 GL_ALPHA，颜色照样由 glColor 调制。
 * 基线和 TrueType 图集对齐在第 16 像素：一格正好 16 像素高，底边即基线。
 */
public final class UnifontFont {
    private static final int CELL = 16;
    private static final int CELLS_PER_ROW = 16;
    /** 低于这个 alpha 的像素当空白，抗锯齿的边缘不算墨迹。 */
    private static final int INK_THRESHOLD = 40;

    private final MinecraftAssets assets;
    private final Map<Integer, Optional<Plane>> planes = new HashMap<>();
    private final Map<Integer, Optional<Glyph>> glyphs = new HashMap<>();

    /** 一页字库：上传好的 alpha 纹理 + 留在堆上的 alpha，量每格墨迹范围用。 */
    private record Plane(Sprite sprite, byte[] alpha, int width, int height) {
    }

    public UnifontFont(MinecraftAssets assets) {
        this.assets = assets;
    }

    /** 这一页没打包、或者这一格是空的，都返回空，让调用方换别的字体。 */
    public Optional<Glyph> glyph(int codepoint) {
        return glyphs.computeIfAbsent(codepoint, this::resolve);
    }

    private Optional<Glyph> resolve(int codepoint) {
        Optional<Plane> plane = plane(codepoint >> 8);
        if (plane.isEmpty()) {
            return Optional.empty();
        }
        Plane found = plane.get();
        int cell = codepoint & 0xFF;
        int baseX = (cell % CELLS_PER_ROW) * CELL;
        int baseY = (cell / CELLS_PER_ROW) * CELL;
        if (baseX + CELL > found.width() || baseY + CELL > found.height()) {
            return Optional.empty();
        }
        int left = CELL;
        int right = -1;
        int top = CELL;
        int bottom = -1;
        for (int row = 0; row < CELL; row++) {
            for (int column = 0; column < CELL; column++) {
                if ((found.alpha()[(baseY + row) * found.width() + baseX + column] & 0xFF) >= INK_THRESHOLD) {
                    left = Math.min(left, column);
                    right = Math.max(right, column);
                    top = Math.min(top, row);
                    bottom = Math.max(bottom, row);
                }
            }
        }
        if (right < 0) {
            return Optional.empty();
        }
        // 半宽还是全宽按 Unicode 东亚宽度那套区间判，别拿墨迹宽度猜：「、」只占左边几列但它是全宽
        int advance = wide(codepoint) ? CELL : CELL / 2;
        return Optional.of(new Glyph(advance, left, top, right - left + 1, bottom - top + 1,
                baseX + left, baseY + top, found.sprite()));
    }

    private Optional<Plane> plane(int high) {
        Optional<Plane> known = planes.get(high);
        if (known != null) {
            return known;
        }
        Optional<MinecraftAssets.Blob> blob = assets.bundled(
                String.format("font/unifont/glyph_%02X.png", high));
        if (blob.isEmpty()) {
            planes.put(high, Optional.empty());
            return Optional.empty();
        }
        Plane loaded = upload(blob.get());
        planes.put(high, Optional.ofNullable(loaded));
        return Optional.ofNullable(loaded);
    }

    private Plane upload(MinecraftAssets.Blob blob) {
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];
        ByteBuffer encoded = MemoryUtil.memAlloc(blob.bytes().length);
        encoded.put(blob.bytes());
        encoded.flip();
        ByteBuffer rgba = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
        MemoryUtil.memFree(encoded);
        if (rgba == null) {
            assets.noteFailure("unreadable PNG: " + blob.origin());
            return null;
        }
        int pixels = width[0] * height[0];
        byte[] alpha = new byte[pixels];
        ByteBuffer atlas = MemoryUtil.memAlloc(pixels);
        for (int index = 0; index < pixels; index++) {
            byte coverage = rgba.get(index * 4 + 3);
            alpha[index] = coverage;
            atlas.put(index, coverage);
        }
        STBImage.stbi_image_free(rgba);
        atlas.position(0);
        Sprite sprite = Sprite.upload(atlas, width[0], height[0], Sprite.FORMAT_ALPHA);
        MemoryUtil.memFree(atlas);
        assets.noteHit("font/unifont", blob.origin());
        return new Plane(sprite, alpha, width[0], height[0]);
    }

    /** Unicode East Asian Width 的 Wide / Fullwidth 区间，够界面用就行。 */
    private static boolean wide(int codepoint) {
        return codepoint >= 0x1100
                && (codepoint <= 0x115F
                || codepoint >= 0x2E80 && codepoint <= 0xA4CF && codepoint != 0x303F
                || codepoint >= 0xAC00 && codepoint <= 0xD7A3
                || codepoint >= 0xF900 && codepoint <= 0xFAFF
                || codepoint >= 0xFE10 && codepoint <= 0xFE19
                || codepoint >= 0xFE30 && codepoint <= 0xFE6F
                || codepoint >= 0xFF00 && codepoint <= 0xFF60
                || codepoint >= 0xFFE0 && codepoint <= 0xFFE6);
    }
}
