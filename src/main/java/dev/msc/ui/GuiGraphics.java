package dev.msc.ui;

import java.util.Optional;

import org.lwjgl.opengl.GL11;

/**
 * 立即模式画布：MC 风 UI 的全部原语。
 *
 * 坐标系是**逻辑像素**（设备像素 ÷ GUI scale），y 轴朝下，和原版 Gui 一致。
 * 字号一律用 8（常规）与 16（标题）：图集里 16 像素对应逻辑字号 8，
 * 在 GUI scale 2 下正好 1:1 采样，放大只走整数倍，NEAREST 边缘才不会糊。
 */
public final class GuiGraphics {
    /** 图集像素 → 逻辑像素的换算基准：16 图集像素 = 逻辑字号 8。 */
    private static final float LINE_UNITS = TrueTypeFont.LINE_UNITS;

    private final MscFont font;
    private int width;
    private int height;
    private int guiScale;

    public GuiGraphics(MscFont font) {
        this.font = font;
    }

    public MscFont font() {
        return font;
    }

    public void begin(int deviceWidth, int deviceHeight, int logicalWidth, int logicalHeight, int scale) {
        this.width = logicalWidth;
        this.height = logicalHeight;
        this.guiScale = scale;
        GL11.glViewport(0, 0, deviceWidth, deviceHeight);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        // bottom=height、top=0 才是 y 轴朝下；近裁剪面取负，否则 z 非零的顶点会被切掉
        GL11.glOrtho(0, logicalWidth, logicalHeight, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glClearColor(0.06f, 0.08f, 0.1f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int guiScale() {
        return guiScale;
    }

    public static void color(int argb) {
        GL11.glColor4f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24)) / 255f);
    }

    public void fill(int x, int y, int w, int h, int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public void gradient(int x, int y, int w, int h, int topColor, int bottomColor) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        color(topColor);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        color(bottomColor);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public void outline(int x, int y, int w, int h, int argb) {
        fill(x, y, w, 1, argb);
        fill(x, y + h - 1, w, 1, argb);
        fill(x, y + 1, 1, h - 2, argb);
        fill(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** 整张贴图拉成一块矩形。 */
    public void sprite(Sprite sprite, int x, int y, int w, int h) {
        sprite(sprite, x, y, w, h, 0, 0, sprite.width(), sprite.height());
    }

    /** 取贴图像素矩形 (u,v,regionW,regionH) 画到 (x,y,w,h)，等价原版的 blit。 */
    public void sprite(Sprite sprite, int x, int y, int w, int h, int u, int v, int regionW, int regionH) {
        sprite(sprite, x, y, w, h, u, v, regionW, regionH, 0xFFFFFFFF);
    }

    /** 带色调的版本：alpha<255 就是半透明，panorama 交叉淡出靠它。 */
    public void sprite(Sprite sprite, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                       int argb) {
        sprite.bind();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        color(argb);
        float u0 = (float) u / sprite.width();
        float v0 = (float) v / sprite.height();
        float u1 = (float) (u + regionW) / sprite.width();
        float v1 = (float) (v + regionH) / sprite.height();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    /**
     * 横向三段拉伸：左右各留 cap 像素原样贴，只拉伸中间那段。
     * 整张按钮图直接拉宽会把 1 像素的描边挤糊，半宽按钮上尤其难看。
     */
    public void sliced(Sprite sprite, int x, int y, int w, int h, int cap) {
        sliced(sprite, x, y, w, h, cap, 0xFFFFFFFF);
    }

    /** 带色调的版本：进度条就是拿同一张条图铺两份，压暗的那份当槽、原色那份当填充。 */
    public void sliced(Sprite sprite, int x, int y, int w, int h, int cap, int argb) {
        if (w < cap * 2 + 1) {
            sprite(sprite, x, y, w, h, 0, 0, sprite.width(), sprite.height(), argb);
            return;
        }
        sprite(sprite, x, y, cap, h, 0, 0, cap, sprite.height(), argb);
        sprite(sprite, x + cap, y, w - cap * 2, h, cap, 0, sprite.width() - cap * 2, sprite.height(), argb);
        sprite(sprite, x + w - cap, y, cap, h, sprite.width() - cap, 0, cap, sprite.height(), argb);
    }

    /** 平铺（原版菜单那层泥土底就是这么铺的）：tileWidth/tileHeight 决定一块占多少逻辑像素。 */
    public void tiled(Sprite sprite, int x, int y, int w, int h, int tileWidth, int tileHeight) {
        sprite.bind();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        color(0xFFFFFFFF);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        float tw = (float) w / tileWidth;
        float th = (float) h / tileHeight;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(tw, 0);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(tw, th);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0, th);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
    }

    /**
     * 进度条：底槽铺那条暗的、填充铺那条亮的，两张都是 182x5 —— 条高就用 5，拉高会把渐变摊开。
     * 左右各 2 像素是描边，不参与横向拉伸。
     * 贴图缺了才退回程序化的老版绿条：1 像素黑框 + 暗槽 + 亮绿填充，顶边提亮、底边压暗。
     * fraction 超出 0..1 一律夹住。
     */
    public void progressBar(int x, int y, int barWidth, int barHeight, double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        Optional<Sprite> back = UiTextures.texture(UiTextures.PROGRESS_BACK);
        Optional<Sprite> fill = UiTextures.texture(UiTextures.PROGRESS_FILL);
        if (back.isPresent() && fill.isPresent()) {
            sliced(back.get(), x, y, barWidth, barHeight, 2);
            int filled = (int) Math.round(barWidth * clamped);
            if (filled > 0) {
                sliced(fill.get(), x, y, filled, barHeight, 2);
            }
            return;
        }
        fill(x, y, barWidth, barHeight, 0xFF000000);
        fill(x + 1, y + 1, barWidth - 2, barHeight - 2, 0xFF2A2A2A);
        int filled = (int) Math.round((barWidth - 2) * clamped);
        if (filled > 0) {
            fill(x + 1, y + 1, filled, barHeight - 2, 0xFF00AF00);
            fill(x + 1, y + 1, filled, 1, 0xFF5FFF5F);
            fill(x + 1, y + barHeight - 2, filled, 1, 0xFF005F00);
        }
    }

    public int textWidth(String text) {
        return textWidth(text, 8);
    }

    public int textWidth(String text, int size) {
        return Math.round(font.bakedWidth(plain(text)) * size / LINE_UNITS);
    }

    /** 去掉 §x 格式码后的纯文本：量宽度/截断都按可见字算。 */
    private static String plain(String text) {
        if (text == null || text.indexOf('\u00A7') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\u00A7' && index + 1 < text.length()) {
                index++;
                continue;
            }
            out.append(value);
        }
        return out.toString();
    }

    /**
     * 截到 maxWidth（字号 8 的宽度）以内，超出就以「...」收尾。
     * 按钮面和面板宽度是死的，日志、路径这种不定长的字宁可少看几个字，也不能溢出盖住右边的东西。
     */
    public String fit(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (textWidth(text) <= maxWidth) {
            return text;
        }
        String value = text;
        while (value.length() > 4 && textWidth(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 4);
        }
        return value + "...";
    }

    public void text(String text, int x, int y, int argb) {
        text(text, x, y, 8, argb, false);
    }

    public void textShadow(String text, int x, int y, int argb) {
        text(text, x, y, 8, argb, true);
    }

    public void textCentered(String text, int centerX, int y, int size, int argb, boolean shadow) {
        text(text, centerX - textWidth(text, size) / 2, y, size, argb, shadow);
    }

    /**
     * 画一行字，支持原版 § 格式码：§0..§f 颜色、§k 乱码、§l 粗体、§m 删除线、§n 下划线、
     * §o 斜体、§r 重置。y 是**行框顶边**，基线在顶边往下 8 逻辑像素（字号 8 时）。
     * shadow 就是原版那种投影：偏移只有半格（原版是 1 设备像素），偏一整格看着像重影而不像影子。
     */
    public void text(String text, int x, int y, int size, int argb, boolean shadow) {
        java.util.List<Styled> styled = parse(text, argb);
        long seed = System.nanoTime() / 250_000_000L;
        java.util.Random random = new java.util.Random(seed);
        int[] codepoints = new int[styled.size()];
        for (int index = 0; index < styled.size(); index++) {
            Styled value = styled.get(index);
            codepoints[index] = (value.style() & OBFUSCATE) != 0
                    ? "ABCDEFGHJKLMNOPQRSTUVWXYZ0123456789".charAt(
                            random.nextInt(34))
                    : value.codepoint();
        }
        float offset = size / 16f;
        if (shadow) {
            drawChars(styled, codepoints, x + offset, y + offset, size, true);
        }
        drawChars(styled, codepoints, x, y, size, false);
    }

    private void drawChars(java.util.List<Styled> styled, int[] codepoints, float x, float y,
                           int size, boolean shadow) {
        float factor = size / LINE_UNITS;
        Glyph[] glyphs = new Glyph[styled.size()];
        for (int index = 0; index < styled.size(); index++) {
            glyphs[index] = font.glyph(codepoints[index]);
        }
        font.flush();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        float penX = x;
        for (int index = 0; index < styled.size(); index++) {
            Styled value = styled.get(index);
            Glyph glyph = glyphs[index];
            int color = shadow ? darken(value.color()) : value.color();
            float shear = (value.style() & ITALIC) != 0 ? 0.35f : 0f;
            if (!glyph.blank()) {
                drawGlyph(glyph, penX, y, factor, color, shear);
                if ((value.style() & BOLD) != 0) {
                    drawGlyph(glyph, penX + factor * 0.5f, y, factor, color, shear);
                }
            }
            float advance = glyph.advance() * factor;
            if ((value.style() & UNDERLINE) != 0) {
                // 紧贴字底：基线就是行框顶边 + size
                fill(Math.round(penX), Math.round(y + size), Math.max(1, Math.round(advance)),
                        1, color);
            }
            if ((value.style() & STRIKE) != 0) {
                fill(Math.round(penX), Math.round(y + size / 2f), Math.max(1, Math.round(advance)),
                        1, color);
            }
            penX += advance;
        }
    }

    private static void drawGlyph(Glyph glyph, float penX, float y, float factor, int color,
                                  float shear) {
        Sprite atlas = glyph.atlas();
        atlas.bind();
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        emit(glyph, penX, y, factor, shear);
        GL11.glEnd();
    }

    private static void emit(Glyph glyph, float penX, float y, float factor, float shear) {
        Sprite atlas = glyph.atlas();
        float left = penX + glyph.bearingX() * factor;
        float top = y + glyph.top() * factor;
        float right = left + glyph.width() * factor;
        float bottom = top + glyph.height() * factor;
        float baseline = y + 8 * factor;
        float topShift = shear * (baseline - top);
        float bottomShift = shear * (baseline - bottom);
        GL11.glTexCoord2f((float) glyph.atlasX() / atlas.width(), (float) glyph.atlasY() / atlas.height());
        GL11.glVertex2f(left + topShift, top);
        GL11.glTexCoord2f((float) (glyph.atlasX() + glyph.width()) / atlas.width(),
                (float) glyph.atlasY() / atlas.height());
        GL11.glVertex2f(right + topShift, top);
        GL11.glTexCoord2f((float) (glyph.atlasX() + glyph.width()) / atlas.width(),
                (float) (glyph.atlasY() + glyph.height()) / atlas.height());
        GL11.glVertex2f(right + bottomShift, bottom);
        GL11.glTexCoord2f((float) glyph.atlasX() / atlas.width(),
                (float) (glyph.atlasY() + glyph.height()) / atlas.height());
        GL11.glVertex2f(left + bottomShift, bottom);
    }

    // ------------------------------------------------------------------ 格式码

    private static final int BOLD = 1;
    private static final int ITALIC = 2;
    private static final int UNDERLINE = 4;
    private static final int STRIKE = 8;
    private static final int OBFUSCATE = 16;
    private static final int[] COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    private record Styled(int codepoint, int color, int style) {
    }

    private static java.util.List<Styled> parse(String text, int baseColor) {
        java.util.List<Styled> out = new java.util.ArrayList<>();
        // 颜色码只换 RGB，alpha 必须跟着调用方走：tooltip 淡出靠的就是这一层 alpha
        int alpha = baseColor & 0xFF000000;
        int color = baseColor;
        int style = 0;
        for (int index = 0; index < text.length(); ) {
            char value = text.charAt(index);
            if (value == '\u00A7' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'r') {
                    style = 0;
                    color = alpha | 0xFFFFFF;
                } else if (code >= '0' && code <= '9') {
                    color = alpha | (COLORS[code - '0'] & 0xFFFFFF);
                } else if (code >= 'a' && code <= 'f') {
                    color = alpha | (COLORS[10 + code - 'a'] & 0xFFFFFF);
                } else if (code == 'k') {
                    style |= OBFUSCATE;
                } else if (code == 'l') {
                    style |= BOLD;
                } else if (code == 'm') {
                    style |= STRIKE;
                } else if (code == 'n') {
                    style |= UNDERLINE;
                } else if (code == 'o') {
                    style |= ITALIC;
                }
                index += 2;
                continue;
            }
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            out.add(new Styled(codepoint, color, style));
        }
        return out;
    }

    private static int darken(int argb) {
        int alpha = argb >>> 24;
        if (alpha >= 0xF0) {
            // 原版的投影不是单纯调暗，而是把不透明文字色换成半透明黑，才能压在任何背景上都看得见
            return 0x4F000000;
        }
        return (alpha << 24) | (((argb >> 16) & 0xFF) * 0x4F / 0xFF << 16)
                | (((argb >> 8) & 0xFF) * 0x4F / 0xFF << 8) | ((argb & 0xFF) * 0x4F / 0xFF);
    }

    /** 水平镜像画一块贴图：原版那个朝右的三角转成朝左，不用再带一张图。 */
    public void spriteFlippedX(Sprite sprite, int x, int y, int w, int h,
                               int u, int v, int regionW, int regionH) {
        sprite.bind();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        color(0xFFFFFFFF);
        float u0 = (float) u / sprite.width();
        float u1 = (float) (u + regionW) / sprite.width();
        float v0 = (float) v / sprite.height();
        float v1 = (float) (v + regionH) / sprite.height();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    /** 绕中心旋转画一块贴图：上/下箭头用同一张朝右三角转出来。 */
    public void spriteRotated(Sprite sprite, int centerX, int centerY, int size, float degrees,
                              int u, int v, int regionW, int regionH) {
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0);
        GL11.glRotatef(degrees, 0, 0, 1);
        sprite(sprite, -size / 2, -size / 2, size, size, u, v, regionW, regionH);
        GL11.glPopMatrix();
    }

    public void scissor(int x, int y, int w, int h) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // GL 的剪刀坐标原点在左下，逻辑坐标原点在左上，要换算
        GL11.glScissor(x * guiScale, (height - y - h) * guiScale, w * guiScale, h * guiScale);
    }

    public void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * 切到透视投影，给 3D 背景（panorama 天空盒）用。
     * 画完一定要 end3d：界面还在正交坐标系里等画，矩阵栈也不能一直涨。
     */
    public void begin3d(float fovDegrees, float near, float far) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        float halfHeight = near * (float) Math.tan(Math.toRadians(fovDegrees) / 2);
        float halfWidth = halfHeight * (float) width / height;
        GL11.glFrustum(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    public void end3d() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}
