package dev.msc.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 从已选资源包里读文件，严格按原版结构：包目录下 assets/minecraft/&lt;路径&gt;。
 *
 * 选择顺序就是覆盖顺序：后选的包压在先选的上面（与原版列表自下而上启用一致），
 * 所以从后往前找，第一个存在的文件算数。只读本机目录，不打包、不分发。
 *
 * ui() 给界面贴图用；game() 给游戏内材质预留（世界渲染接纹理时走它）。
 */
public final class ResourcePacks {
    public static Optional<byte[]> ui(String logicalPath) {
        return read(UiPreferences.get().uiResourcePacks(), logicalPath);
    }

    public static Optional<byte[]> game(String logicalPath) {
        return read(UiPreferences.get().gameResourcePacks(), logicalPath);
    }

    private static Optional<byte[]> read(String paths, String logicalPath) {
        if (paths == null || paths.isBlank() || logicalPath == null || logicalPath.isBlank()) {
            return Optional.empty();
        }
        String[] parts = paths.split(";");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (parts[index].isBlank()) {
                continue;
            }
            try {
                Path path = Path.of(parts[index]);
                if (Files.isDirectory(path)) {
                    Path file = path.resolve("assets/minecraft").resolve(logicalPath);
                    if (Files.isRegularFile(file)) {
                        return Optional.of(Files.readAllBytes(file));
                    }
                } else if (path.getFileName().toString().endsWith(".zip") && Files.isRegularFile(path)) {
                    try (ZipFile zip = new ZipFile(path.toFile())) {
                        ZipEntry entry = zip.getEntry("assets/minecraft/" + logicalPath);
                        if (entry != null) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                return Optional.of(in.readAllBytes());
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // 单个包读不动就跳过，下一份来源照常试
            }
        }
        return Optional.empty();
    }

    private ResourcePacks() {
    }
}
