package dev.msc.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 界面素材的来源。**打包在 jar 里的 PNG 是第一来源**（`src/main/resources/msc/…`），
 * 界面开箱就有原版贴图，不依赖使用者装过官方客户端；
 * 内置没带的那张，再去使用者自己的 .minecraft 里找，最后才由调用方回落到程序化绘制。
 *
 * 一次读会给出**多个候选出处**，因为版本 jar 里的条目可能已经坏掉（本机 1.12.2 的
 * panorama_3 / panorama_5 连 zip CRC 都对不上），调用方解不开就该换下一份，而不是当这张图不存在。
 */
public final class MinecraftAssets {
    /** jar 内前缀：逻辑路径 `textures/gui/title/minecraft.png` → 资源 `/msc/textures/gui/title/minecraft.png`。 */
    private static final String BUNDLE_PREFIX = "/msc/";
    /** 1.12 线：主界面 panorama 与标题 logo 都在版本 jar 的 assets/minecraft/ 下。 */
    private static final String PREFERRED_VERSION = "1.12.2";

    private static final class Family {
        final String version;
        final List<Path> jars;
        /** 该版本 assetIndex 指向的索引文件，本地没下过索引就是 null。 */
        final Path index;

        Family(String version, List<Path> jars, Path index) {
            this.version = version;
            this.jars = jars;
            this.index = index;
        }
    }

    /** 一份字节 + 它从哪来。出处要能原样打在日志和调试面板上。 */
    public record Blob(String origin, byte[] bytes) {
    }

    private final Path root;
    private final List<Family> families;
    private final List<String> failures = new ArrayList<>();
    private final Map<String, String> hits = new LinkedHashMap<>();

    private MinecraftAssets(Path root, List<Family> families) {
        this.root = root;
        this.families = families;
    }

    /**
     * 发现顺序：`-Dmsc.minecraft.dir` → 环境变量 → 本机本地配置（msc-local.properties / `~/.msc/config.properties`）
     * → 常见安装位置。找不到官方安装也返回可用对象，只是内置之外的贴图取不到，界面自动回落。
     */
    private static MinecraftAssets shared;

    /** 全界面共用一份：每次 open() 都要重扫版本目录，别按界面重复扫。 */
    public static MinecraftAssets shared() {
        if (shared == null) {
            shared = open();
        }
        return shared;
    }

    public static MinecraftAssets open() {
        Optional<MinecraftAssets> found = discover();
        return found.isPresent() ? found.get() : new MinecraftAssets(null, List.of());
    }

    private static Optional<MinecraftAssets> discover() {
        String configured = System.getProperty("msc.minecraft.dir");
        if (configured == null) {
            configured = System.getenv("MSC_MINECRAFT_DIR");
        }
        if (configured == null) {
            configured = fromLocalConfig();
        }
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured));
        }
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, "AppData", "Roaming", ".minecraft"));
        candidates.add(Path.of(home, ".minecraft"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("assets")) && Files.isDirectory(candidate.resolve("versions"))) {
                return Optional.of(new MinecraftAssets(candidate, familiesOf(candidate)));
            }
        }
        return Optional.empty();
    }

    /** 首选 1.12 线（要的就是 1.12 之前的地形 panorama），其余版本按名字倒序兜底。 */
    private static List<Family> familiesOf(Path root) {
        List<Path> versionDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.resolve("versions"))) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    versionDirs.add(entry);
                }
            }
        } catch (IOException unreadable) {
            return List.of();
        }
        versionDirs.sort(Comparator
                .comparing((Path dir) -> !dir.getFileName().toString().equals(PREFERRED_VERSION))
                .thenComparing(dir -> dir.getFileName().toString(), Comparator.reverseOrder()));
        List<Family> families = new ArrayList<>();
        for (Path dir : versionDirs) {
            String name = dir.getFileName().toString();
            Path index = assetIndex(root, dir, name);
            List<Path> jars = findArchives(dir);
            if (!jars.isEmpty() || index != null) {
                families.add(new Family(name, jars, index));
            }
        }
        return families;
    }

    /** 版本 json 里的 assetIndex.id 才是索引文件名（1.12.2 → 1.12.json），抄错就整条 objects 路都走不通。 */
    private static Path assetIndex(Path root, Path versionDir, String versionName) {
        String id = null;
        Path versionJson = versionDir.resolve(versionName + ".json");
        if (Files.isRegularFile(versionJson)) {
            try {
                Matcher matcher = Pattern.compile("\"assetIndex\"\\s*:\\s*\\{[^{}]*?\"id\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(Files.readString(versionJson));
                if (matcher.find()) {
                    id = matcher.group(1);
                }
            } catch (IOException unreadable) {
                id = null;
            }
        }
        Path indexed = root.resolve("assets").resolve("indexes").resolve((id != null ? id : versionName) + ".json");
        return Files.isRegularFile(indexed) ? indexed : null;
    }

    /** 每台机器的 .minecraft 位置不一样，这行路径记在本地配置里，不进仓库。 */
    private static String fromLocalConfig() {
        List<Path> files = List.of(
                Path.of(System.getProperty("user.dir"), "msc-local.properties"),
                Path.of(System.getProperty("user.home"), ".msc", "config.properties"));
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                properties.load(reader);
                return properties.getProperty("minecraft.dir");
            } catch (IOException unreadable) {
                return null;
            }
        }
        return null;
    }

    /** PCL 一类的启动器会把 jar 改名成 .zip，两边都认。 */
    private static List<Path> findArchives(Path versionDir) {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(versionDir)) {
            return jars;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString().toLowerCase();
                if (Files.isRegularFile(entry) && (name.endsWith(".jar") || name.endsWith(".zip"))) {
                    jars.add(entry);
                }
            }
        } catch (IOException unreadable) {
            return List.of();
        }
        return jars;
    }

    public String describe() {
        if (root == null) {
            return "no official install, bundled textures only";
        }
        return "official install " + root + " - " + families.size() + " families (first "
                + (families.isEmpty() ? "none" : families.get(0).version) + ")";
    }

    /** 解不开、读不到都要留话，左下角的 log 浮条和 stdout 要能看见到底是哪份文件坏了。 */
    public void noteFailure(String message) {
        if (failures.size() < 24) {
            failures.add(message);
        }
    }

    public List<String> failures() {
        return failures;
    }

    /** 命中处原样记下来：谁在用哪张图、图从哪个文件来，不该靠猜。 */
    public void noteHit(String logicalPath, String origin) {
        hits.put(logicalPath, origin);
    }

    public String hitOf(String logicalPath) {
        return hits.getOrDefault(logicalPath, "not loaded");
    }

    /** 同一逻辑路径的全部出处：内置优先，再按版本家族，最后查解包出来的 virtual 目录。 */
    public List<Blob> blobs(String logicalPath) {
        List<Blob> blobs = new ArrayList<>();
        bundled(logicalPath).ifPresent(blobs::add);
        for (Family family : families) {
            for (Path jar : family.jars) {
                String entryName = "assets/minecraft/" + logicalPath;
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry != null) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            blobs.add(new Blob(family.version + " " + jar.getFileName() + "!" + entryName,
                                    in.readAllBytes()));
                        }
                    }
                } catch (IOException | RuntimeException broken) {
                    // 条目存在但读不出（CRC 不符 / 本地头与中心目录不一致）也要报，别静默当没这张图
                    noteFailure("unreadable entry " + logicalPath + " in " + jar.getFileName()
                            + ": " + broken.getMessage());
                }
            }
            fromObjectIndex(family, logicalPath).ifPresent(blobs::add);
        }
        if (root != null) {
            Path virtual = root.resolve("assets").resolve("virtual").resolve("legacy").resolve(logicalPath);
            if (Files.isRegularFile(virtual)) {
                byte[] bytes = readBytes(virtual);
                if (bytes != null) {
                    blobs.add(new Blob("virtual/legacy/" + logicalPath, bytes));
                }
            }
        }
        return blobs;
    }

    /** MSC 自己打包的那一份；成套的贴图（panorama）只认这个来源，不能一半 1.12 一半 1.18。 */
    public Optional<Blob> bundled(String logicalPath) {
        try (InputStream in = MinecraftAssets.class.getResourceAsStream(BUNDLE_PREFIX + logicalPath)) {
            return in == null ? Optional.empty()
                    : Optional.of(new Blob("MSC 内置 msc/" + logicalPath, in.readAllBytes()));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * 资产索引是一棵 `{"name":{"hash":"40hex","size":n}}` 的树，按这个形状手写字面量提取，
     * 引一个 JSON 库只为查一张图不值当。
     */
    private Optional<Blob> fromObjectIndex(Family family, String logicalPath) {
        if (family.index == null) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(family.index);
            Matcher matcher = Pattern.compile(Pattern.quote("\"" + logicalPath + "\"")
                    + "\\s*:\\s*\\{[^{}]*?\"hash\"\\s*:\\s*\"([0-9a-f]{40})\"").matcher(json);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String hash = matcher.group(1);
            Path object = root.resolve("assets").resolve("objects")
                    .resolve(hash.substring(0, 2)).resolve(hash);
            if (!Files.isRegularFile(object)) {
                return Optional.empty();
            }
            return Optional.of(new Blob(family.version + " objects/" + hash.substring(0, 2) + "/" + hash,
                    Files.readAllBytes(object)));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** 自己带的字体：微软雅黑（含中文）→ 文黑 → 宋体，本机装什么系统就用什么，不随 MSC 分发。 */
    public static Optional<Path> discoverCjkFont() {
        String windows = System.getenv("WINDIR") != null ? System.getenv("WINDIR") : "C:/Windows";
        for (String name : new String[] {"msyh.ttc", "simhei.ttf", "simsun.ttc", "NotoSansSC-VF.ttf"}) {
            Path candidate = Path.of(windows, "Fonts", name);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
