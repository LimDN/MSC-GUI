package dev.msc.ui.demo;

import dev.msc.ui.Window;

/**
 * 演示页入口：不开 MSC，只把控件层自己跑起来。
 *
 * 版面核对的参数由控件层自己读（见 {@link Window}）：
 * `-Dmsc.ui.hidden=true` 不出窗口、`-Dmsc.ui.dump=路径` 存 PNG、`-Dmsc.ui.dump-frame=N` 存第几帧（默认 2）、
 * `-Dmsc.ui.cursor=x,y` 钉住鼠标、`-Dmsc.ui.click=x,y` 合成一次点击、`-Dmsc.ui.width/height` 改窗口尺寸。
 * 演示进程的工作目录就是仓库根，所以 dump 路径写相对路径也会落在仓库里；给人看的图一律写绝对路径。
 */
public final class WidgetDemo {
    public static void main(String[] args) {
        int width = Integer.getInteger("msc.ui.width", 1280);
        int height = Integer.getInteger("msc.ui.height", 720);
        try (Window window = Window.open(width, height)) {
            window.push(new WidgetDemoScreen());
            window.run();
        } catch (RuntimeException fatal) {
            System.err.println("[msc-ui] 界面起不来: " + fatal.getClass().getSimpleName()
                    + " —— " + fatal.getMessage());
            fatal.printStackTrace();
        }
    }
}
