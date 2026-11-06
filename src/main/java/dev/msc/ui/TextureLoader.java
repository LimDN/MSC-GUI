package dev.msc.ui;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

/** PNG 字节 → GL 纹理。解码统一按 RGBA，透明通道直接可用。 */
public final class TextureLoader {
    private TextureLoader() {
    }

    public static Optional<Sprite> fromPng(byte[] png) {
        // 一张 panorama 就 60KB+，压在默认 64KB 的 MemoryStack 上会直接爆栈，走直接堆内存
        ByteBuffer encoded = MemoryUtil.memAlloc(png.length);
        ByteBuffer decoded = null;
        try {
            encoded.put(png);
            encoded.flip();
            int[] width = new int[1];
            int[] height = new int[1];
            int[] channels = new int[1];
            // 不翻转：画布的 y 轴本来就朝下，贴图第 0 行落在矩形顶边才是原样
            decoded = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (decoded == null) {
                return Optional.empty();
            }
            return Optional.of(Sprite.rgba(decoded, width[0], height[0]));
        } finally {
            MemoryUtil.memFree(encoded);
            if (decoded != null) {
                STBImage.stbi_image_free(decoded);
            }
        }
    }

    /** 已选界面资源包优先，其次 MSC 打包的那一份；成套贴图要同一代美术，不能混进别的版本。 */
    public static Optional<Sprite> fromBundle(MinecraftAssets assets, String logicalPath) {
        Optional<byte[]> packed = ResourcePacks.ui(logicalPath);
        if (packed.isPresent()) {
            Optional<Sprite> sprite = fromPng(packed.get());
            if (sprite.isPresent()) {
                assets.noteHit(logicalPath, "ui resource pack");
                return sprite;
            }
            assets.noteFailure("ui pack PNG unreadable: " + logicalPath);
        }
        return assets.bundled(logicalPath).flatMap(blob -> {
            Optional<Sprite> sprite = fromPng(blob.bytes());
            if (sprite.isPresent()) {
                assets.noteHit(logicalPath, blob.origin());
            } else {
                assets.noteFailure("bundled PNG unreadable: " + logicalPath);
            }
            return sprite;
        });
    }

    /** 内置 → 本机官方安装，逐个出处试，解不开就换下一份；全部失败才回落。 */
    public static Optional<Sprite> fromAsset(MinecraftAssets assets, String logicalPath) {
        List<MinecraftAssets.Blob> blobs = assets.blobs(logicalPath);
        if (blobs.isEmpty()) {
            assets.noteFailure("no source for " + logicalPath);
            return Optional.empty();
        }
        for (MinecraftAssets.Blob blob : blobs) {
            Optional<Sprite> sprite = fromPng(blob.bytes());
            if (sprite.isPresent()) {
                assets.noteHit(logicalPath, blob.origin());
                return sprite;
            }
            assets.noteFailure("unreadable PNG: " + blob.origin());
        }
        return Optional.empty();
    }
}
