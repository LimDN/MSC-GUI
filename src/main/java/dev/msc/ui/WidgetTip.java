package dev.msc.ui;

/**
 * 一枚控件交出来的悬浮说明：文字 + 光标此刻在哪儿 + 这句话说的是不是危险操作。
 *
 * 坐标交的是光标而不是控件矩形：气泡照原版跟着鼠标走，控件滚动、改 GUI 缩放都不用重算。
 * 每次现交、不缓存，气泡才不可能飘在半空指着别的东西。
 *
 * @param danger true 时字用 §c 红（删、停、丢弃这类回不去的操作），false 用 §e 黄
 */
public record WidgetTip(Object owner, String text, int mouseX, int mouseY, boolean danger) {
}
