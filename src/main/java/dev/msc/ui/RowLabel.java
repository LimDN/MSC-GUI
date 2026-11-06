package dev.msc.ui;

/**
 * 一行不能点的说明字（按键清单、目录、事实行）。
 * 放进 RowBlock 而不是各界面自己算 y：字跟着板子一起滚，窗口再矮也不会爬到「完成」底下。
 */
public final class RowLabel implements RowWidget {
    private String text;
    private int color;
    /** true 就居中，false 就靠左（路径这类长的更适合靠左，读起来不用追中线）。 */
    private boolean centered;
    private int x;
    private int y;
    private int width = 200;

    public RowLabel(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public RowLabel center() {
        this.centered = true;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // 长词条（路径、命令）一律截断，宁可少看几个字也不能盖住右边的滚动条
        String shown = graphics.fit(text, width - 2);
        if (centered) {
            graphics.textCentered(shown, x + width / 2, y + 4, 8, color, false);
        } else {
            graphics.text(shown, x + 1, y + 4, color);
        }
    }
}
