package dev.msc.ui;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 一张 GL 纹理。UI 全是像素风，缩放一律 NEAREST，不开 mipmap —— 开了糊边就不像原版了。
 * 唯一的例外是高清 logo：见 {@link #smoothScaled()}，那种素材不平均就会掉边。
 */
public final class Sprite implements AutoCloseable {
    /** GL_ALPHA：只存遮罩，颜色由 glColor 调制，字形图集用这个。 */
    public static final int FORMAT_ALPHA = GL11.GL_ALPHA;
    public static final int FORMAT_RGBA = GL11.GL_RGBA;

    private final int id;
    private final int width;
    private final int height;
    private final int format;

    private Sprite(int id, int width, int height, int format) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.format = format;
    }

    public static Sprite upload(ByteBuffer pixels, int width, int height, int format) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, format, GL11.GL_UNSIGNED_BYTE, pixels);
        return new Sprite(texture, width, height, format);
    }

    /** stb 解出来的 PNG 一律按 RGBA 上传，透明像素直接可用。 */
    public static Sprite rgba(ByteBuffer pixels, int width, int height) {
        return upload(pixels, width, height, FORMAT_RGBA);
    }

    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int textureId() {
        return id;
    }

    /** 从纹理取一块到 RGBA 缓冲，程序化贴图生成用。 */
    public static ByteBuffer allocate(int bytes) {
        return MemoryUtil.memAlloc(bytes);
    }

    /**
     * 高清素材往小画时用的过滤：NEAREST 每 2.56 个源像素只留 1 个，logo 那圈 3D 描边会被抽掉一半，
     * 看着像字被切了一刀。换成三线性 + mipmap 后按面积平均，缩到多小都不掉边。
     *
     * 光换过滤还不够：PNG 里的透明像素 RGB 存的是纯黑（实测这两张 logo 的透明像素 100% 是 0,0,0），
     * 烘 mipmap 时黑会被平均进半透明的边缘，一整圈下来 logo 就发灰、底边像被切掉。所以先就地补色：
     * 给每个透明像素找一个最近的不透明颜色，alpha 一个字节都不动 —— 遮挡关系不变，平均时不再掺黑。
     *
     * 只对「缩小」有意义：放大仍想要硬边就不该调这个。补色和 mipmap 都在装载时做一次，不每帧重做。
     */
    public void smoothScaled() {
        if (format != FORMAT_RGBA) {
            return;
        }
        ByteBuffer pixels = allocate(width * height * 4);
        try {
            bind();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            bleedColors(pixels);
            // 换内容不换 id：别处已经记住的这张纹理照样是这张
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, FORMAT_RGBA, width, height, 0,
                    FORMAT_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /**
     * 横向、纵向各扫两趟（chamfer）：每个像素拿到离它最近的那个不透明颜色，
     * 顺带记下走了多远，后面这一趟只在更近时才覆盖。四趟走完，全透明的角落也能拿到颜色。
     * 只写 RGB，alpha 保持原样。
     */
    private void bleedColors(ByteBuffer pixels) {
        int count = width * height;
        int[] color = new int[count];
        int[] steps = new int[count];
        java.util.Arrays.fill(steps, -1);
        for (int index = 0; index < count; index++) {
            int base = index * 4;
            if (pixels.get(base + 3) != 0) {
                color[index] = ((pixels.get(base) & 0xFF) << 16) | ((pixels.get(base + 1) & 0xFF) << 8)
                        | (pixels.get(base + 2) & 0xFF);
                steps[index] = 0;
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean horizontal = pass < 2;
            boolean ascending = (pass & 1) == 0;
            int lines = horizontal ? height : width;
            int along = horizontal ? width : height;
            for (int line = 0; line < lines; line++) {
                int carryColor = 0;
                int carrySteps = -1;
                for (int step = 0; step < along; step++) {
                    int position = ascending ? step : along - 1 - step;
                    int index = horizontal ? line * width + position : position * width + line;
                    if (steps[index] == 0) {
                        carryColor = color[index];
                        carrySteps = 0;
                        continue;
                    }
                    if (carrySteps < 0) {
                        continue;
                    }
                    if (steps[index] < 0 || carrySteps + 1 < steps[index]) {
                        steps[index] = carrySteps + 1;
                        color[index] = carryColor;
                    }
                    carryColor = color[index];
                    carrySteps = steps[index];
                }
            }
        }
        for (int index = 0; index < count; index++) {
            if (steps[index] > 0) {
                int base = index * 4;
                pixels.put(base, (byte) (color[index] >> 16));
                pixels.put(base + 1, (byte) (color[index] >> 8));
                pixels.put(base + 2, (byte) color[index]);
            }
        }
    }

    /**
     * 图里真正有墨的那一块，{x, y, w, h}，y 从图顶算：高清 logo 那两张的画布上下都留了一截全透明，
     * 按画布算宽高会把「JAVA EDITION」顶到下面去，也会把字拉变形。整张全透明就返回整张。
     *
     * 走 glGetTexImage 读回而不是再解一次 PNG：出处（内置 / 本机官方安装）只留在 TextureLoader 里。
     * 行序不用换算 —— 上传时 PNG 顶行落在纹理 v=0，读回来顶行也在前，和 {@code sprite()} 的 v 是同一套坐标。
     */
    public int[] inkBounds() {
        if (format != FORMAT_RGBA) {
            throw new IllegalStateException("仅 RGBA 贴图可测量墨迹边界，当前 format=" + format);
        }
        ByteBuffer pixels = allocate(width * height * 4);
        try {
            bind();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels.get((y * width + x) * 4 + 3) & 0xFF) == 0) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
            if (maxX < 0) {
                return new int[] {0, 0, width, height};
            }
            return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /**
     * 水平镜像出另一张贴图：panorama 缺的那一面靠它补。
     * 走 glGetTexImage 读回再上传，是为了让"从哪个出处加载"这件事只留在 TextureLoader 里，
     * 补出来的镜像不需要知道原图是内置还是本机官方安装。
     */
    public Sprite mirroredHorizontally() {
        if (format != FORMAT_RGBA) {
            throw new IllegalStateException("仅 RGBA 贴图可镜像，当前 format=" + format);
        }
        ByteBuffer source = allocate(width * height * 4);
        ByteBuffer flipped = allocate(width * height * 4);
        try {
            bind();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, source);
            for (int row = 0; row < height; row++) {
                // 读回和上传是同一个行序，所以这里只镜像列，行号原样搬
                for (int column = 0; column < width; column++) {
                    int from = (row * width + (width - 1 - column)) * 4;
                    int to = (row * width + column) * 4;
                    for (int channel = 0; channel < 4; channel++) {
                        flipped.put(to + channel, source.get(from + channel));
                    }
                }
            }
            return upload(flipped, width, height, FORMAT_RGBA);
        } finally {
            MemoryUtil.memFree(source);
            // upload 已经把内容拷进纹理了，这张缓冲可以立刻还
            MemoryUtil.memFree(flipped);
        }
    }

    @Override
    public void close() {
        GL11.glDeleteTextures(id);
    }
}
