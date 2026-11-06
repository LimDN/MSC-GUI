package dev.msc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 栅格五种摆法的坐标。绘制和命中用的是同一套数，所以这套数错了整页都是错的 ——
 * 原版音乐页那种「主音量整行 + 类目并列 + 声音/语音占左半格」全靠这里分档。
 */
class RowBlockLayoutTest {
    private static final int BLOCK_WIDTH = RowBlock.OPTION_WIDTH * 2 + RowBlock.OPTION_GAP;

    /** 记下 layout 发下来的坐标，其余一律不管。 */
    private static final class Probe implements RowWidget {
        private int x = -1;
        private int y = -1;
        private int width = -1;

        @Override
        public void setBounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        }
    }

    @Test
    void pairAndHalfShareTheSameTwoColumns() {
        Probe left = new Probe();
        Probe right = new Probe();
        Probe lone = new Probe();
        RowBlock rows = new RowBlock();
        rows.pair(left, right);
        rows.half(lone);
        rows.layout(0, 0, BLOCK_WIDTH, rows.contentHeight());

        int firstColumn = (BLOCK_WIDTH - (RowBlock.OPTION_WIDTH * 2 + RowBlock.OPTION_GAP)) / 2;
        assertEquals(firstColumn, left.x);
        assertEquals(firstColumn + RowBlock.OPTION_WIDTH + RowBlock.OPTION_GAP, right.x);
        assertEquals(RowBlock.OPTION_WIDTH, left.width);
        assertEquals(RowBlock.OPTION_WIDTH, right.width);
        // 占左半格：和并列那枚同宽同起点，右边那一格是空的
        assertEquals(firstColumn, lone.x);
        assertEquals(RowBlock.OPTION_WIDTH, lone.width);
    }

    @Test
    void fullSpansTheBlockWhileWideAndCenteredUseTheirOwnWidths() {
        Probe full = new Probe();
        Probe wide = new Probe();
        Probe centered = new Probe();
        RowBlock rows = new RowBlock();
        rows.full(full);
        rows.wide(wide);
        rows.centered(centered);
        rows.layout(0, 0, BLOCK_WIDTH, rows.contentHeight());

        assertEquals(0, full.x);
        assertEquals(BLOCK_WIDTH, full.width);
        assertEquals(RowBlock.WIDE_WIDTH, wide.width);
        assertEquals((BLOCK_WIDTH - RowBlock.WIDE_WIDTH) / 2, wide.x);
        assertEquals(RowBlock.OPTION_WIDTH, centered.width);
        assertEquals((BLOCK_WIDTH - RowBlock.OPTION_WIDTH) / 2, centered.x);
    }

    @Test
    void rowsLineUpByPitchFromTheTopOfTheBlock() {
        Probe first = new Probe();
        Probe second = new Probe();
        RowBlock rows = new RowBlock();
        rows.wide(first);
        rows.wide(second);
        rows.layout(0, 44, BLOCK_WIDTH, rows.contentHeight());

        assertEquals(44, first.y);
        assertEquals(44 + RowBlock.PITCH, second.y);
    }
}
