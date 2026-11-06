package dev.msc.ui;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * 没有官方资源时的兜底背景：自己画一张体素地形，色调与构图按 1.12 之前那版主界面的感觉来。
 * 目的是让 MSC 在一台没装过 Minecraft 的机器上也能把界面跑起来，而不是模仿某张具体的图。
 */
public final class FallbackArt {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 200;

    private FallbackArt() {
    }

    public static Sprite terrainBackdrop(long seed) {
        ByteBuffer pixels = Sprite.allocate(WIDTH * HEIGHT * 4);
        Random random = new Random(seed);
        int[] horizon = new int[WIDTH];
        int level = HEIGHT / 2;
        for (int column = 0; column < WIDTH; column++) {
            if (random.nextInt(10) == 0) {
                level += random.nextInt(9) - 4;
                level = Math.max(HEIGHT / 3, Math.min(HEIGHT * 3 / 4, level));
            }
            horizon[column] = level;
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 4;
                int rgb;
                int ground = horizon[x];
                if (y < ground) {
                    rgb = sky(x, y, ground);
                } else {
                    rgb = strata(x, y, ground);
                }
                pixels.put(offset, (byte) ((rgb >> 16) & 0xFF));
                pixels.put(offset + 1, (byte) ((rgb >> 8) & 0xFF));
                pixels.put(offset + 2, (byte) (rgb & 0xFF));
                pixels.put(offset + 3, (byte) 0xFF);
            }
        }
        return Sprite.rgba(pixels, WIDTH, HEIGHT);
    }

    private static int sky(int x, int y, int ground) {
        float t = (float) y / Math.max(1, ground);
        int top = 0x4A7EC8;
        int bottom = 0xBFD9F2;
        int r = (top >> 16 & 0xFF) + (((bottom >> 16 & 0xFF) - (top >> 16 & 0xFF)) * (int) (t * 255) / 255);
        int g = (top >> 8 & 0xFF) + (((bottom >> 8 & 0xFF) - (top >> 8 & 0xFF)) * (int) (t * 255) / 255);
        int b = (top & 0xFF) + (((bottom & 0xFF) - (top & 0xFF)) * (int) (t * 255) / 255);
        // 方块云：16 格一跳，硬边，不做平滑
        if ((x / 16 + y / 16) % 7 == 0 && y < ground - 12 && (x % 16) < 12 && ((x * 7 + y * 3) % 11) < 6) {
            return 0xF2F6FA;
        }
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8)
                | Math.max(0, Math.min(255, b));
    }

    private static int strata(int x, int y, int ground) {
        int cell = 8;
        int depth = (y - ground) / cell;
        int shade = ((x / cell) + (y / cell)) % 2 == 0 ? 0xEC : 0xD8;
        if (depth == 0) {
            return greener(shade);   // 草皮
        }
        if (depth < 3) {
            return earth(shade);     // 泥土
        }
        if (depth < 6) {
            return stone(shade);     // 圆石
        }
        return bedrock(shade);
    }

    private static int greener(int shade) {
        return (0x5C * shade / 255 << 16) | (0xA8 * shade / 255 << 8) | (0x4A * shade / 255);
    }

    private static int earth(int shade) {
        return (0x8A * shade / 255 << 16) | (0x6A * shade / 255 << 8) | (0x4A * shade / 255);
    }

    private static int stone(int shade) {
        return (shade << 16) | (shade << 8) | shade;
    }

    private static int bedrock(int shade) {
        int dark = shade * 0x55 / 255;
        return (dark << 16) | (dark << 8) | dark;
    }
}
