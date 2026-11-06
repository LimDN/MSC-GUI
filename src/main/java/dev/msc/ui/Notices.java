package dev.msc.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面左下角的小提示：灰色英文，一行行像 log。停 5 秒，再用 1 秒淡掉。
 *
 * 旧的还没淡完就来新的，旧的直接往上顶一格 —— 不做移动动画，新的永远排在最下面。
 * 底部留出主界面那两行原版文字（「已更改」+ 版权声明）的位置，提示不会和它们叠在一起。
 */
public final class Notices {
    private static final double HOLD = 5.0;
    private static final double FADE = 1.0;
    /** 字高 8 + 行距 2。 */
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 3;
    /**
     * 最下面那行的顶边离界面底部多远；再往下就是底部栏的地盘。
     * 32 只够让开主界面的版权行 —— 设置页那块「完成」是整宽的，顶边在 height-26，
     * 提示压在它上面等于把按钮咬掉一条边，所以按最高的那件底部控件留。
     */
    private static final int BOTTOM_ROW = 40;
    /** 只留最近几条，堆太多就把半个界面占满了。 */
    private static final int MAX_LINES = 5;

    private static final class Entry {
        private final String text;
        /** 只存 RGB：淡出时 alpha 每帧重算，不该混在常量里。 */
        private final int rgb;
        private final double bornAt;
        /** 截断结果跟着界面宽度变，缓存一份免得每帧重测。 */
        private int cacheWidth = -1;
        private String display;

        Entry(String text, int rgb, double bornAt) {
            this.text = text;
            this.rgb = rgb;
            this.bornAt = bornAt;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    /** 上一次渲染的时刻：提示多半是在点击回调里喊的，那时拿不到 seconds，就用这帧的时间。 */
    private double clock;

    public void show(String text) {
        show(text, UiColors.DIM & 0xFFFFFF);
    }

    /** 警告走 §c：和日志挨在一排时得一眼分出「这句是拦你的」还是「这句只是出处」。 */
    public void showWarn(String text) {
        show(text, UiColors.WARNING & 0xFFFFFF);
    }

    private void show(String text, int rgb) {
        entries.add(new Entry(text, rgb, clock));
        while (entries.size() > MAX_LINES) {
            entries.remove(0);
        }
    }

    public void render(GuiGraphics graphics, int height, double seconds) {
        clock = seconds;
        entries.removeIf(entry -> seconds - entry.bornAt > HOLD + FADE);
        int maxWidth = graphics.width() - MARGIN * 2;
        int lineTop = height - BOTTOM_ROW;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            double age = seconds - entry.bornAt;
            int alpha = age <= HOLD ? 255 : (int) Math.round(255 * (1 - (age - HOLD) / FADE));
            graphics.textShadow(fitted(entry, graphics, maxWidth), MARGIN, lineTop, alpha << 24 | entry.rgb);
            lineTop -= LINE_HEIGHT;
        }
    }

    /** 一行放不下的路径类提示截中段留头留尾；完整文本在 stdout 里，界面只负责看得出问题出在哪。 */
    private static String fitted(Entry entry, GuiGraphics graphics, int maxWidth) {
        if (entry.cacheWidth == maxWidth) {
            return entry.display;
        }
        String text = entry.text;
        if (graphics.textWidth(text) > maxWidth) {
            int keep = text.length();
            while (keep > 8 && graphics.textWidth(text.substring(0, keep - 3) + "...") > maxWidth) {
                keep--;
            }
            text = text.substring(0, keep - 3) + "...";
        }
        entry.cacheWidth = maxWidth;
        entry.display = text;
        return text;
    }
}
