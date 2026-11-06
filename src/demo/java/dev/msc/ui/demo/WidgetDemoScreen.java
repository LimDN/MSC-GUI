package dev.msc.ui.demo;

import java.util.ArrayList;
import java.util.List;

import dev.msc.ui.GuiButton;
import dev.msc.ui.GuiGraphics;
import dev.msc.ui.GuiSlider;
import dev.msc.ui.GuiTextField;
import dev.msc.ui.RowWidget;
import dev.msc.ui.ScrollArea;
import dev.msc.ui.Screen;
import dev.msc.ui.UiColors;
import dev.msc.ui.WidgetTip;

/**
 * 控件层的演示页：按钮、输入框、滑条、滚动列表、悬浮气泡、左下角浮条各摆一枚，
 * 用来在没有 MSC 的情况下证明「这些控件自己就能跑」。
 *
 * 文案是写死的英文，不走 MSC 的语言文件 —— 这一页是自检台，不是产品界面。
 * 没放素材时按钮与背景走程序化兜底，样子会糙一点，读数和交互不受影响。
 */
public final class WidgetDemoScreen extends Screen {
    private static final int ROW_HEIGHT = RowWidget.HEIGHT;
    /** 滑条占 122..142，「list」那行字占 146..156，列表从 158 起 —— 三样叠着放，字不能骑在滑条边上。 */
    private static final int LIST_TOP = 158;

    private final List<String> rows = new ArrayList<>();
    private final ScrollArea list = new ScrollArea();
    private GuiButton pressButton;
    private GuiButton quitButton;
    private GuiTextField nameField;
    private GuiSlider volumeSlider;
    private int listLeft;
    private int presses;

    public WidgetDemoScreen() {
        for (int index = 0; index < 24; index++) {
            rows.add(String.format("row %02d  -  scroll me", index));
        }
    }

    @Override
    protected void init() {
        int half = (blockWidth() - 2) / 2;
        int left = blockLeft();
        listLeft = left;
        pressButton = new GuiButton(left, 70, half, "press me", () -> {
            presses++;
            notice("pressed " + presses + "x, field=" + nameField.value());
        }).setTooltip("counts clicks; the counter is under the title");
        quitButton = new GuiButton(left + half + 2, 70, half, "exit", window::exit)
                .setTooltip("closes the window");
        nameField = new GuiTextField(left, 100, blockWidth(), "unnamed")
                .setTooltip("plain text field - backspace and arrows work");
        volumeSlider = new GuiSlider("volume", 0, 1, 0.05, 0.6,
                value -> String.format("%.2f", value),
                value -> notice("volume " + String.format("%.2f", value)));
        volumeSlider.setBounds(left, 122, blockWidth());
        list.layout(left, LIST_TOP, blockWidth(), Math.max(ROW_HEIGHT, height - LIST_TOP - 24), ROW_HEIGHT);
        list.setRows(rows.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, double seconds) {
        renderDirtBackground(graphics);
        graphics.textCentered("msc-ui widget demo", width / 2, 24, 8, UiColors.WHITE, true);
        graphics.textCentered("presses: " + presses, width / 2, 40, 8, UiColors.DIM, false);
        renderPanel(graphics, 60, height - 20);
        pressButton.render(graphics, mouseX, mouseY);
        quitButton.render(graphics, mouseX, mouseY);
        nameField.render(graphics, seconds);
        volumeSlider.render(graphics, mouseX, mouseY);
        graphics.text("list", listLeft, LIST_TOP - 12, UiColors.DIM);
        int last = Math.min(rows.size(), list.first() + list.visibleRows());
        for (int row = list.first(); row < last; row++) {
            graphics.text(rows.get(row), listLeft + 2, LIST_TOP + (row - list.first()) * ROW_HEIGHT,
                    row == list.first() ? UiColors.WHITE : UiColors.DIM);
        }
        list.render(graphics, mouseX, mouseY);
        renderTooltips(graphics, tipUnder(mouseX, mouseY), seconds);
        renderNotices(graphics, seconds);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        WidgetTip underCursor = tipUnder(mouseX, mouseY);
        if (pressButton.click(mouseX, mouseY) || quitButton.click(mouseX, mouseY)
                || volumeSlider.click(mouseX, mouseY)) {
            pokeTooltips(underCursor);
            return true;
        }
        if (list.press(mouseX, mouseY)) {
            return true;
        }
        if (!nameField.mouseClicked(mouseX, mouseY)) {
            GuiTextField.releaseFocusOutside(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY, int button) {
        if (volumeSlider.drag(mouseX, mouseY, button)) {
            return true;
        }
        list.drag(mouseY);
        return nameField.drag(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        volumeSlider.release(button);
        nameField.release(button);
        list.release();
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        list.scroll(amount);
        return true;
    }

    @Override
    public boolean keyPressed(int key) {
        return nameField.keyPressed(key);
    }

    @Override
    public boolean keyChar(char value) {
        nameField.charTyped(value);
        return true;
    }

    private WidgetTip tipUnder(int mouseX, int mouseY) {
        return tipAmong(List.of(pressButton, quitButton, nameField, volumeSlider), mouseX, mouseY);
    }
}
