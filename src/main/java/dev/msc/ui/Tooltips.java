package dev.msc.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮 tips：光标停在控件上就在光标边上浮出一块黑底气泡，跟着鼠标走；
 * 鼠标一移出控件范围立刻开始淡，一直在控件上不动也只停 3 秒然后用 0.5 秒淡掉。
 *
 * 计时从冒泡那一刻起算，鼠标在同一枚控件上继续动也不续期：气泡是查词用的，
 * 不该因为手指停在上面就长期挂半个界面。
 *
 * 摆位照原版：横向往空间多的一侧偏（光标靠右半就浮在左上角，靠左半就浮在右上角），
 * 纵向摆在光标上方，上方放不下才翻到下方。字色分两档 —— 普通说明 §e 黄，
 * 删、停、丢弃这类回不去的操作 §c 红，一眼就能分出这枚点下去要不要后悔。
 *
 * 这套东西取代的是页头那行常驻副标题：一句话挂在页面上，谁都得看；
 * 挪成气泡后只有想知道的人才看得到，页面也腾出了一行。
 *
 * 只跟一枚控件。淡完时如果光标还没离开，就记一笔「这枚已经说过了」，
 * 不记的话下一帧又指在它身上会立刻重新冒出来，看着像淡出没生效。
 *
 * 「悬浮提示」这个开关（客户端设置里那枚）关掉后这里整个不吃：不冒泡、不计时，
 * 其余规则不变，再打开就照旧。
 */
public final class Tooltips {
    /** 冒泡之后最长停留多久（秒）：鼠标一直停在控件上不续期，静默满这个时长就开始淡。 */
    private static final double HOLD = 3.0;
    /** 淡出用时（秒）。 */
    private static final double FADE = 0.5;
    /** 气泡与光标之间的缝（纵向）。 */
    private static final int GAP = 4;
    /** 气泡与光标之间的缝（横向）：原版那截留得宽，字才不会压在指针底下。 */
    private static final int CURSOR_GAP = 10;
    /** 气泡内边距。 */
    private static final int PAD = 3;
    /** 字高 8 + 行距 2。 */
    private static final int LINE_HEIGHT = 10;
    /** 一行最多排多宽：再长就折行，免得一根横条盖掉半个界面。 */
    private static final int MAX_TEXT_WIDTH = 190;
    /** 离画面边至少留这么宽，气泡不出界。 */
    private static final int SCREEN_MARGIN = 2;

    private WidgetTip shown;
    /** 这一轮冒泡的起点：静默满 HOLD 就开始淡，鼠标在同一枚控件上挪动也不续期。 */
    private double liveAt;
    /** 淡出的起点，负数表示还没开始淡。 */
    private double fadeAt = -1;
    /** 光标此刻还压在 shown 那枚控件上吗：只有「离开过又指回来」才算新一轮，一直指着不算。 */
    private boolean hovering;
    /** 淡完了但光标还没离开的那枚控件，暂时不为它重新冒泡。 */
    private Object suppressed;
    private String wrappedFor = "";
    private int wrappedWidth = -1;
    private List<String> wrappedLines = List.of();

    /**
     * 每帧交进来：光标正指着的那枚控件给出的气泡，没有就 null。
     * 由界面在画完控件之后调 {@link #render} 之前调。
     */
    public void update(WidgetTip tip, double seconds) {
        if (!UiPreferences.get().tooltipsEnabled()) {
            // 开关关掉：正在淡的这枚也立刻收掉，重新打开时不会留一个半透明的旧气泡
            shown = null;
            suppressed = null;
            fadeAt = -1;
            return;
        }
        if (tip == null || tip.text() == null || tip.text().isEmpty()) {
            // 一移出控件范围就开始淡，不等满 HOLD；下次再指回来照样冒
            if (shown != null && fadeAt < 0) {
                fadeAt = seconds;
            }
            hovering = false;
            suppressed = null;
            return;
        }
        if (suppressed == tip.owner()) {
            // 压住的是这一枚的计时，不是它的内容：读数变了气泡就得跟着换，不然会停在旧话上撒谎
            shown = tip;
            return;
        }
        if (shown == null || shown.owner() != tip.owner() || !hovering) {
            // 换了控件，或者离开过又指回来：重新起一轮
            liveAt = seconds;
            fadeAt = -1;
        }
        // 一直指着同一枚不动不算新一轮：计时不跟着鼠标续期，静默满 HOLD 照样淡
        hovering = true;
        shown = tip;
    }

    /** 此刻气泡上那句话；没在显示就是空串。单测盯着「压住计时不能连内容一起冻住」。 */
    String shownText() {
        return shown == null ? "" : shown.text();
    }

    /**
     * 刚按下按钮或拖动滑条：这枚到此为止，剩下的时间走完就淡掉，光标没挪开也不再续。
     * 只有光标还压在同一枚控件上才算数 —— 在别处按的一下不该给正在淡出的气泡续命。
     */
    public void poke(WidgetTip underCursor) {
        if (shown == null || underCursor == null || underCursor.owner() != shown.owner()) {
            return;
        }
        suppressed = shown.owner();
    }

    public void render(GuiGraphics graphics, double seconds) {
        if (shown == null) {
            return;
        }
        if (fadeAt < 0 && seconds - liveAt >= HOLD) {
            // 静默到头：起点按满 HOLD 那一刻算，别让最后一帧的余量把淡出拉长
            fadeAt = liveAt + HOLD;
        }
        if (fadeAt < 0) {
            draw(graphics, shown, 255);
            return;
        }
        double past = seconds - fadeAt;
        if (past >= FADE) {
            // 淡完时光标还指着它就记一笔「这枚说过了」，不然下一帧立刻又冒出来
            suppressed = shown.owner();
            shown = null;
            fadeAt = -1;
            return;
        }
        draw(graphics, shown, (int) Math.round(255 * (1 - past / FADE)));
    }

    private void draw(GuiGraphics graphics, WidgetTip tip, int alpha) {
        List<String> lines = wrap(graphics, tip.text(), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        int screenW = graphics.width();
        int screenH = graphics.height();
        int boxWidth = boxWidth(graphics, lines);
        int boxHeight = boxHeight(lines);
        // 横向看光标离哪条边远：离右边远就摆在光标右侧（读起来是左上角那块），反之摆左侧
        int x = screenW - tip.mouseX() >= tip.mouseX()
                ? tip.mouseX() + CURSOR_GAP
                : tip.mouseX() - CURSOR_GAP - boxWidth;
        int y = tip.mouseY() - GAP - boxHeight;
        // 上方放不下就翻到下方，再出不去就整体夹回画面里
        if (y < SCREEN_MARGIN) {
            y = tip.mouseY() + GAP;
        }
        paint(graphics, lines, tip.danger(), boxWidth, clampX(x, boxWidth, screenW),
                clampY(y, boxHeight, screenH), alpha);
    }

    private static int clampX(int value, int boxWidth, int screenW) {
        return Math.max(SCREEN_MARGIN, Math.min(screenW - boxWidth - SCREEN_MARGIN, value));
    }

    private static int clampY(int value, int boxHeight, int screenH) {
        return Math.max(SCREEN_MARGIN, Math.min(screenH - boxHeight - SCREEN_MARGIN, value));
    }

    private static int boxWidth(GuiGraphics graphics, List<String> lines) {
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, graphics.textWidth(line));
        }
        return textWidth + PAD * 2;
    }

    private static int boxHeight(List<String> lines) {
        return lines.size() * LINE_HEIGHT + PAD * 2 - 2;
    }

    private static void paint(GuiGraphics graphics, List<String> lines, boolean danger,
                              int boxWidth, int x, int y, int alpha) {
        int boxHeight = boxHeight(lines);
        graphics.fill(x, y, boxWidth, boxHeight, fade(0xF0100010, alpha));
        graphics.outline(x, y, boxWidth, boxHeight, fade(0xFF8A8A8A, alpha));
        int textColor = fade(danger ? UiColors.WARNING : UiColors.HINT, alpha);
        int lineTop = y + PAD;
        for (String line : lines) {
            graphics.text(line, x + PAD, lineTop, textColor);
            lineTop += LINE_HEIGHT;
        }
    }

    /**
     * 折行：一行放不下就退到上一个空格处（英文词不拆断）；
     * 中文本来就没有空格，退无可退就按字切。
     * 「 - 」这种分隔符优先当换行点：宁可两行各说一句，也别把破折号单独甩在行尾。
     * 只按字宽量一次并缓存，气泡每帧都要画，测量不该跟着重来。
     */
    private List<String> wrap(GuiGraphics graphics, String text, int maxWidth) {
        if (text.equals(wrappedFor) && maxWidth == wrappedWidth) {
            return wrappedLines;
        }
        List<String> lines = new ArrayList<>();
        if (graphics.textWidth(text) <= maxWidth) {
            lines.add(text);
        } else {
            for (String part : text.split(" - ")) {
                wrapPart(graphics, part, maxWidth, lines);
            }
        }
        wrappedFor = text;
        wrappedWidth = maxWidth;
        wrappedLines = lines;
        return lines;
    }

    private static void wrapPart(GuiGraphics graphics, String text, int maxWidth, List<String> lines) {
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int afterLastSpace = -1;
            int width = 0;
            while (end < text.length()) {
                int cell = text.codePointAt(end);
                int cellWidth = graphics.textWidth(new String(Character.toChars(cell)));
                if (width + cellWidth > maxWidth) {
                    break;
                }
                width += cellWidth;
                end += Character.charCount(cell);
                if (cell == ' ') {
                    afterLastSpace = end;
                }
            }
            if (end < text.length() && afterLastSpace > start) {
                end = afterLastSpace;
            }
            if (end <= start) {
                end = start + 1;
            }
            String line = text.substring(start, end).strip();
            if (!line.isEmpty()) {
                lines.add(line);
            }
            start = end;
        }
    }

    /** 淡出按各自本来就有的 alpha 再乘一档：气泡底本来就是半透的 0xF0，不能一把拍成不透明。 */
    private static int fade(int argb, int alpha) {
        int scaled = (int) Math.round((argb >>> 24) * alpha / 255.0);
        return (scaled << 24) | (argb & 0xFFFFFF);
    }
}
