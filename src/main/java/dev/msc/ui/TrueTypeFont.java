package dev.msc.ui;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

/**
 * stb_truetype 烘一张字形图集：拉丁用 MC 自己的像素字体 minecraft-seven，
 * 位图字库没覆盖到的码位再退回系统中文字体兜底。
 *
 * 图集是 GL_ALPHA 单通道，颜色由 glColor 调制，所以文字想染成淡黄、红色都行。
 * 基线固定落在图集第 {@value #BASELINE} 像素，绘制系数固定是 字号 / {@value #LINE_UNITS}，
 * 常规字号 8 在 GUI scale 2 下正好 1:1 采样，NEAREST 放大整数倍，边缘不会糊。
 */
public final class TrueTypeFont {
    private static final int ATLAS_SIZE = 1024;
    /** 图集里基线所在的像素行，位图字库按同一条线对齐，混排才不会一高一低。 */
    public static final int BASELINE = 16;    /** 逻辑字号 8 对应图集里的 16 像素。 */
    public static final int LINE_UNITS = 16;
    /**
     * 硬切墨迹的门限，取半覆盖。实测 seven 在烘 24 时一根笔画正好跨两个像素，
     * 门限再往下（试过 90）会把相邻笔画糊在一起，往上（>128）才会把笔画削断。
     */
    private static final int INK_CUTOFF = 128;

    // 3.4 起结构体不再给公开无参构造，堆上自己 malloc 一个，进程级存活
    private final STBTTFontinfo info = STBTTFontinfo.create();
    /** stb 只记指针不拷贝数据，所以字体缓冲必须活到进程结束。 */
    private final ByteBuffer fontData;
    private final float scale;
    private final int ascent;
    private final int descent;
    /**
     * true = 把 stb 的 8 位覆盖率硬切成有墨/无墨。像素字体（minecraft-seven）本来就没有灰阶，
     * 保留灰阶只会让 NEAREST 采出一圈半透明的毛边，看着就是"发虚"。带灰阶的真字体反要留，
     * 否则 16 像素的汉字会被削成马赛克。
     */
    private final boolean hardInk;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    /** GL 只接受直接内存，堆缓冲传进去会在取地址时抛。 */
    private final ByteBuffer atlasPixels = blankAtlas();
    private final Sprite atlas;
    private int packX = 1;
    private int packY = 1;
    private int rowHeight;
    private boolean dirty;

    private TrueTypeFont(ByteBuffer fontData, int faceIndex, int bakeSize, boolean hardInk) {
        this.fontData = fontData;
        this.hardInk = hardInk;
        // msyh.ttc 是字体集合，不先取面偏移直接 init 会失败
        int offset = STBTruetype.stbtt_GetFontOffsetForIndex(fontData, faceIndex);
        if (!STBTruetype.stbtt_InitFont(info, fontData, offset)) {
            throw new IllegalStateException("字体解析失败: face=" + faceIndex);
        }
        scale = STBTruetype.stbtt_ScaleForPixelHeight(info, bakeSize);
        int[] ascentBuffer = new int[1];
        int[] descentBuffer = new int[1];
        int[] lineGapBuffer = new int[1];
        STBTruetype.stbtt_GetFontVMetrics(info, ascentBuffer, descentBuffer, lineGapBuffer);
        ascent = Math.round(ascentBuffer[0] * scale);
        descent = Math.round(-descentBuffer[0] * scale);
        atlas = Sprite.upload(atlasPixels, ATLAS_SIZE, ATLAS_SIZE, Sprite.FORMAT_ALPHA);
    }

    public static TrueTypeFont fromBytes(byte[] raw, int faceIndex, int bakeSize, boolean hardInk) {
        // stb 只记指针不拷贝数据，缓冲必须一直活着
        ByteBuffer data = MemoryUtil.memAlloc(raw.length);
        data.put(raw);
        data.flip();
        return new TrueTypeFont(data, faceIndex, bakeSize, hardInk);
    }

    public static TrueTypeFont fromFile(Path file, int faceIndex, int bakeSize, boolean hardInk) {
        try {
            return fromBytes(Files.readAllBytes(file), faceIndex, bakeSize, hardInk);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("字体无法读取：" + file + " —— " + unreadable, unreadable);
        }
    }

    /**
     * memAlloc 不保证清零，没烘到的区域是脏数据。缺字要是画成残影，比画成空白更难查，
     * 所以图集一开始就铺成全透明。
     */
    private static ByteBuffer blankAtlas() {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_SIZE * ATLAS_SIZE);
        for (int index = 0; index < pixels.capacity(); index++) {
            pixels.put(index, (byte) 0);
        }
        return pixels;
    }

    public int ascent() {
        return ascent;
    }

    public int descent() {
        return descent;
    }

    public Glyph glyph(int codepoint) {
        return glyphs.computeIfAbsent(codepoint, this::bake);
    }

    /** 图集内像素单位下的文本宽度，调用方负责换算成逻辑单位。 */
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

    /** 新烘的字形靠整张贴图重传，几十个字的量，不值得做脏矩形。 */
    public void flush() {
        if (!dirty) {
            return;
        }
        // 重传前必须让 GL 从 0 开始读，位置一旦停在尾部就是传了个空
        atlasPixels.position(0);
        atlas.bind();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, Sprite.FORMAT_ALPHA, ATLAS_SIZE, ATLAS_SIZE, 0,
                Sprite.FORMAT_ALPHA, GL11.GL_UNSIGNED_BYTE, atlasPixels);
        dirty = false;
    }

    private Glyph bake(int codepoint) {
        int[] advance = new int[1];
        int[] leftSideBearing = new int[1];
        STBTruetype.stbtt_GetCodepointHMetrics(info, codepoint, advance, leftSideBearing);
        // 前进量是字体单位，要乘 scale；位图偏移 stb 已经按像素给过了，再乘一次会被抹成 0
        int advancePixels = Math.round(advance[0] * scale);

        int[] width = new int[1];
        int[] height = new int[1];
        int[] xOffset = new int[1];
        int[] yOffset = new int[1];
        ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmapSubpixel(
                info, scale, scale, 0f, 0f, codepoint, width, height, xOffset, yOffset);
        if (bitmap == null || width[0] == 0 || height[0] == 0) {
            if (bitmap != null) {
                STBTruetype.stbtt_FreeBitmap(bitmap);
            }
            // 空格和不可见字符：没有位图但仍有前进量，必须保住排版
            return new Glyph(advancePixels, 0, BASELINE, 0, 0, 0, 0, null);
        }
        int glyphWidth = width[0];
        int glyphHeight = height[0];
        int bearingX = xOffset[0];
        // yOffset 是位图顶边相对基线的位置，基线以上为负，所以加号才是"往上抬"
        int top = BASELINE + yOffset[0];

        if (packX + glyphWidth + 1 > ATLAS_SIZE) {
            packX = 1;
            packY += rowHeight + 1;
            rowHeight = 0;
        }
        if (packY + glyphHeight + 1 > ATLAS_SIZE) {
            throw new IllegalStateException("字形图集溢出（" + ATLAS_SIZE + " 像素），需要分页或降低烘焙字号");
        }
        int slotX = packX;
        int slotY = packY;
        for (int row = 0; row < glyphHeight; row++) {
            for (int column = 0; column < glyphWidth; column++) {
                int coverage = bitmap.get(row * glyphWidth + column) & 0xFF;
                // 半覆盖的边行不是"淡一点的墨"，在小字号 1:1 采样下就是一圈毛边；要么留满要么削掉
                int ink = hardInk ? (coverage >= INK_CUTOFF ? 255 : 0) : coverage;
                atlasPixels.put((slotY + row) * ATLAS_SIZE + (slotX + column), (byte) ink);
            }
        }
        STBTruetype.stbtt_FreeBitmap(bitmap);
        packX += glyphWidth + 1;
        rowHeight = Math.max(rowHeight, glyphHeight);
        dirty = true;
        return new Glyph(advancePixels, bearingX, top, glyphWidth, glyphHeight, slotX, slotY, atlas);
    }

    public Sprite atlas() {
        return atlas;
    }

    public int atlasSize() {
        return ATLAS_SIZE;
    }

    /** 把图集原始像素存成灰度 PNG，缺字到底是没烘进去还是纹理没更新，看一眼就分得清。 */
    public void writeAtlasPng(String path) throws java.io.IOException {
        atlasPixels.position(0);
        if (!STBImageWrite.stbi_write_png(Path.of(path).toAbsolutePath().toString(), ATLAS_SIZE, ATLAS_SIZE,
                1, atlasPixels, ATLAS_SIZE)) {
            throw new java.io.IOException("图集 PNG 写入失败: " + path);
        }
    }

    /** 逐个码位打印图集定位，按打包顺序排，方便直接对图集截图上的位置。 */
    public String glyphTable() {
        StringBuilder table = new StringBuilder();
        glyphs.entrySet().stream()
                .sorted(java.util.Comparator.comparingInt(entry -> entry.getValue().atlasY() * ATLAS_SIZE
                        + entry.getValue().atlasX()))
                .forEach(entry -> table.append(String.format(
                        "U+%04X '%s' advance=%d bearingX=%d top=%d w=%d h=%d atlas=(%d,%d)%n",
                        entry.getKey(), new String(Character.toChars(entry.getKey())), entry.getValue().advance(),
                        entry.getValue().bearingX(), entry.getValue().top(), entry.getValue().width(),
                        entry.getValue().height(), entry.getValue().atlasX(), entry.getValue().atlasY())));
        return table.toString();
    }
}
