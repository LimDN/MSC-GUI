package dev.msc.ui;

/**
 * 界面用色：一律取原版 § 色号表里的值，不再各界面自己凑一个近似色。
 *
 * 之前「警告」在三个界面是三种红、「说明行」是一整片蓝，改一处就漏三处。
 * 蓝色留给插件页（那一面的蓝是它自己的标识），其余说明行统一走 §e 黄。
 */
public final class UiColors {
    /** §f */
    public static final int WHITE = 0xFFFFFFFF;
    /** 正文里比白稍暗的一档，用于长段落。 */
    public static final int TEXT = 0xFFE0E0E0;
    /** §7 次要信息、路径、读数值。 */
    public static final int DIM = 0xFFA0A0A0;
    /** 比 §7 再暗一档：日志行、标签这类不该抢眼的字。 */
    public static final int DIMMER = 0xFF909090;
    /** §c 警告：不能做的事、做砸了的事。 */
    public static final int WARNING = 0xFFFF5555;
    /** §a 已生效那一档的状态字：和 §c 配成一对，红=没做、绿=做了。 */
    public static final int GREEN = 0xFF55FF55;
    /** §e 提示行：状态补充、详情说明。 */
    public static final int HINT = 0xFFFFFF55;
    /** §6 金：进程面板标题那一行。 */
    public static final int GOLD = 0xFFFFAA00;
    /** 原版按钮指上去时字会转成的那档黄（§e 亮一点）。 */
    public static final int HOVER_TEXT = 0xFFFFFFA0;
    /** 那片淡天蓝只留给插件页：别的界面的说明行一律走 §e。 */
    public static final int PLUGIN_BLUE = 0xFF9FE8FF;
    /** 日志行的字色：比正文暗、比 §7 亮，长段读起来不刺眼。 */
    public static final int LOG_TEXT = 0xFFC8C8C8;
    /** 列表选中行的底与边（沿用原版多人列表那块蓝）。 */
    public static final int ROW_SELECTED = 0xFF2A6E9E;
    public static final int ROW_SELECTED_EDGE = 0xFF6FB6E8;
    public static final int ROW_IDLE = 0xFF1D1D1D;
    public static final int EDGE = 0xFF3A3A3A;
    /** 内容框底下的半透明黑：让行和按钮从泥土底上分出来。 */
    public static final int PANEL = 0x80000000;
    /** 日志框比列表更暗一档，读长行时不晃眼。 */
    public static final int LOG_PANEL = 0xC0000000;

    private UiColors() {
    }
}
