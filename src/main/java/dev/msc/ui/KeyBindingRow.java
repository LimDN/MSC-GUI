package dev.msc.ui;

/**
 * 设置页里的「动作名 + 键位按钮 + 重置」一行。三个控件都是现成的：
 * 键位与重置用 {@link GuiButton}，动作名直接画字；位置由 {@link RowBlock} 发下来。
 */
public final class KeyBindingRow implements RowWidget {
    private static final int KEY_WIDTH = 96;
    private static final int RESET_WIDTH = 46;

    private final String label;
    private final GuiButton keyButton;
    private final GuiButton resetButton;
    private int x;
    private int y;

    public KeyBindingRow(String label, String keyName, String resetLabel,
                         Runnable rebind, Runnable reset) {
        this.label = label;
        this.keyButton = new GuiButton(0, 0, KEY_WIDTH, keyName, rebind);
        this.resetButton = new GuiButton(0, 0, RESET_WIDTH, resetLabel, reset);
    }

    @Override
    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        keyButton.setBounds(x + width - KEY_WIDTH - RESET_WIDTH - 6, y, KEY_WIDTH);
        resetButton.setBounds(x + width - RESET_WIDTH, y, RESET_WIDTH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.textShadow(label, x + 2, y + 6, UiColors.WHITE);
        keyButton.render(graphics, mouseX, mouseY);
        resetButton.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        return keyButton.click(mouseX, mouseY) || resetButton.click(mouseX, mouseY);
    }

    @Override
    public WidgetTip tipAt(int mouseX, int mouseY) {
        WidgetTip tip = keyButton.tipAt(mouseX, mouseY);
        return tip != null ? tip : resetButton.tipAt(mouseX, mouseY);
    }
}
