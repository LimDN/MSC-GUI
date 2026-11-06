package dev.msc.ui;

/**
 * 一个字形的定位：所在贴图 + 贴图内的像素矩形 + 排版量。
 *
 * 坐标一律是**图集像素**，不是逻辑像素——位图字库和 TrueType 烘出来的图集共用这一套描述，
 * 换算成逻辑尺寸由绘制方按字号统一乘一个系数完成。
 * top 是相对行框顶边的位置：两套字体都把基线放在图集第 16 像素，混排才不会一高一低。
 */
public record Glyph(int advance, int bearingX, int top, int width, int height, int atlasX, int atlasY,
                   Sprite atlas) {
    public boolean blank() {
        return atlas == null || width == 0 || height == 0;
    }
}
