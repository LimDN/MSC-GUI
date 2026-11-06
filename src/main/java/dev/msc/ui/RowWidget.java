package dev.msc.ui;

/**
 * 设置页一行里的控件：按钮、滑条都算。
 *
 * 位置由 RowBlock 按滚动偏移发下来，控件自己不记布局 —— 这样同一批控件
 * 在窗口改大小、加了滚动条之后，画在哪儿和点哪儿永远是一套坐标。
 */
public interface RowWidget {
    /** 一行的默认高度（逻辑像素），跟原版按钮一致。 */
    int HEIGHT = 20;

    void setBounds(int x, int y, int width);

    default int height() {
        return HEIGHT;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY);

    /** 命中就消费这次点击；滑条这类控件还会顺带开始拖拽。 */
    default boolean click(int mouseX, int mouseY) {
        return false;
    }

    default boolean drag(int mouseX, int mouseY, int button) {
        return false;
    }

    default void release(int button) {
    }

    /**
     * 光标指在这枚控件上时要浮出来的说明；没带说明或没指到就返回 null。
     * 页头那种常驻副标题都收到这里：想知道的人指上去就有，不想知道的人面前干净。
     */
    default WidgetTip tipAt(int mouseX, int mouseY) {
        return null;
    }
}
