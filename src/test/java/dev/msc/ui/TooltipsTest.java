package dev.msc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 气泡被点过之后「不再续命」，压住的应该是计时而不是内容：
 * 列表行的读数在点完之后才回来，气泡要是还冻在点之前那句话，画面上就是两句不同的话并存 —— 界面在撒谎。
 */
class TooltipsTest {
    private static final Object ROW = new Object();

    @Test
    void keepsUpdatingTheTextOfASuppressedTip() {
        Tooltips tips = new Tooltips();
        tips.update(new WidgetTip(ROW, "未解析", 10, 10, false), 0.0);
        tips.poke(new WidgetTip(ROW, "未解析", 10, 10, false));

        tips.update(new WidgetTip(ROW, "无法连接（端口未监听或连接被拒）", 10, 10, false), 1.0);

        assertEquals("无法连接（端口未监听或连接被拒）", tips.shownText());
    }

    @Test
    void clearsTheTextOnceNothingIsHovered() {
        Tooltips tips = new Tooltips();
        tips.update(new WidgetTip(ROW, "未解析", 10, 10, false), 0.0);
        tips.update(null, 1.0);

        assertEquals("未解析", tips.shownText(), "离开控件只起淡出，话还在上面");
    }
}
