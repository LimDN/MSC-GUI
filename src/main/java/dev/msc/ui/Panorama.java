package dev.msc.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

/**
 * 主界面背景的 panorama 天空盒：人站在立方体中间看四周，绕竖轴慢转，和原版标题一致。
 *
 * 六面沿用原版编号：0..3 是四壁（顺时针一圈），4 是顶（天），5 是底（地）。
 * 视野 70°、俯角朝上 10° 时底面进不了画面，所以缺面只补四壁 —— 用最近一壁的水平镜像顶上：
 * 镜像把那一壁的右边缘变成新壁的左边缘，接缝正好对上，破绽留在转过去的那条边上。
 */
public final class Panorama {
    private static final float FIELD_OF_VIEW = 70f;
    private static final float NEAR = 0.05f;
    private static final float FAR = 10f;
    /** 俯角取正（朝上）：原版标题也是天空占上半，底面那一格本来就缺。 */
    private static final float PITCH_DEGREES = 10f;
    private static final int WALLS = 4;

    /** 每壁四个角：顶 u=0、顶 u=1、底 u=1、底 u=0，都是从立方体**里面**看的顺序。 */
    private static final float[][] WALL_CORNERS = {
            {-1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1, -1},
            {1, 1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1},
            {1, 1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1},
            {-1, 1, 1, -1, 1, -1, -1, -1, -1, -1, -1, 1},
    };
    private static final float[] SKY_CORNERS = {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1};
    private static final float[] CORNER_UV = {0, 0, 1, 0, 1, 1, 0, 1};
    /**
     * 顶面的 v 要反过来：panorama_4 这张图与四壁顶边的接缝按原样差 8 个色阶，
     * 上下翻过来之后四条边都只剩 0.5 —— 逐像素比出来的，不是猜的（底面 panorama_5 则不用翻）。
     */
    private static final float[] SKY_UV = {0, 1, 1, 1, 1, 0, 0, 0};

    private final Sprite[] wall;
    private final Sprite sky;
    private final List<String> notes;
    /**
     * 已经转过的角度，按帧累加出来的。
     * 用它而不是 seconds * 转速：转速是「可访问性」里能滑的，直接乘的话一动滑条画面就跳一大截。
     */
    private double angleDegrees;
    private double lastSeconds = Double.NaN;

    private Panorama(Sprite[] wall, Sprite sky, List<String> notes) {
        this.wall = wall;
        this.sky = sky;
        this.notes = notes;
    }

    /**
     * 按原版编号排好的六面（缺的是 null）。补镜像要把纹理读回像素，所以必须在 GL 上下文里调用。
     * 一面壁都凑不出来时返回 null，让调用方退回静态背景。
     */
    public static Panorama of(Sprite[] byFace) {
        List<String> notes = new ArrayList<>();
        Sprite[] wall = new Sprite[WALLS];
        boolean[] original = new boolean[WALLS];
        for (int index = 0; index < WALLS; index++) {
            wall[index] = byFace[index];
            original[index] = byFace[index] != null;
        }
        for (int index = 0; index < WALLS; index++) {
            if (wall[index] != null) {
                continue;
            }
            int source = nearestOriginal(original, index);
            if (source < 0) {
                return null;
            }
            wall[index] = wall[source].mirroredHorizontally();
            boolean behind = source == Math.floorMod(index - 1, WALLS);
            // 镜像哪一头决定保住哪条缝：借前一壁就保住新壁的左缝，借后一壁就保住右缝。
            // 一律往前借的话，缺两壁时两壁都是同一张的镜像，转过去的那格看着就是「角度忽然不对」。
            notes.add("panorama wall " + index + " = mirrored wall " + source
                    + (behind ? " (left seam kept)" : " (right seam kept)"));
        }
        Sprite sky = byFace.length > 4 ? byFace[4] : null;
        if (sky == null) {
            notes.add("panorama sky face missing, walls only");
        }
        return new Panorama(wall, sky, notes);
    }

    /**
     * 只认真图的最近一壁：补出来的镜像不能再拿来镜像，否则转一圈全是复制品。
     * 前后同距时取前一壁（固定这个偏向，出图才可复现）。
     */
    private static int nearestOriginal(boolean[] original, int from) {
        for (int step = 1; step < original.length; step++) {
            int behind = Math.floorMod(from - step, original.length);
            if (original[behind]) {
                return behind;
            }
            int ahead = Math.floorMod(from + step, original.length);
            if (original[ahead]) {
                return ahead;
            }
        }
        return -1;
    }

    public List<String> notes() {
        return notes;
    }

    /**
     * @param degreesPerSecond 背景转速（度/秒），0 就是不转；由全局设置里那条滑条给
     */
    public void render(GuiGraphics graphics, double seconds, double degreesPerSecond) {
        // 第一帧没有上一帧可比，从 0 度起；之后只累加这一帧真正走过的时间
        if (Double.isNaN(lastSeconds)) {
            angleDegrees = 0;
        } else {
            angleDegrees += (seconds - lastSeconds) * degreesPerSecond;
        }
        lastSeconds = seconds;
        graphics.begin3d(FIELD_OF_VIEW, NEAR, FAR);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        // 模型视图是后乘：先写俯角、后写方位角，复合为 R_x(-pitch) * R_y(-yaw)。
        // 世界竖轴在相机里的 X 分量恒为 0，所以转到任何方位地平线都是平的。
        // 反过来写（先方位角后俯角）会把俯角变成绕视线的滚转 —— 转起来整圈是斜的（实测）。
        GL11.glRotated(-PITCH_DEGREES, 1, 0, 0);
        GL11.glRotated(angleDegrees, 0, 1, 0);
        for (int index = 0; index < WALLS; index++) {
            quad(wall[index], WALL_CORNERS[index], CORNER_UV);
        }
        if (sky != null) {
            quad(sky, SKY_CORNERS, SKY_UV);
        }
        graphics.end3d();
    }

    private static void quad(Sprite texture, float[] corners, float[] uv) {
        texture.bind();
        GL11.glBegin(GL11.GL_QUADS);
        for (int index = 0; index < 4; index++) {
            GL11.glTexCoord2f(uv[index * 2], uv[index * 2 + 1]);
            GL11.glVertex3f(corners[index * 3], corners[index * 3 + 1], corners[index * 3 + 2]);
        }
        GL11.glEnd();
    }
}
