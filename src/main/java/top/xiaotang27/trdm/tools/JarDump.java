package top.xiaotang27.trdm.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 一次性小工具：列出/导出 jar 里的资源条目，模型只打印精简摘要（每个 element 的 from/to），
 * 用来确认 MTR 原版模型到底长什么样（是不是半块、端头怎么弯）。
 */
public final class JarDump {

    private JarDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: JarDump <jar> <entryPrefix> [listOnly]");
            return;
        }
        final File jarFile = new File(args[0]);
        final String prefix = args[1];
        final boolean listOnly = args.length > 2 && args[2].equalsIgnoreCase("list");
        // 第三个参数传 "raw"：模型也原样打印（摘要看不出面/uv 细节）
        final boolean raw = args.length > 2 && args[2].equalsIgnoreCase("raw");

        try (final ZipFile zip = new ZipFile(jarFile)) {
            final List<String> names = new ArrayList<>();
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".json")) {
                    names.add(entry.getName());
                }
            }
            names.sort(String::compareTo);
            for (final String name : names) {
                System.out.println("=== " + name);
                if (listOnly) {
                    continue;
                }
                final String text = new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (raw) {
                    System.out.println(text.replaceAll("\\s+", " "));
                } else if (name.contains("/blockstates/")) {
                    System.out.println(compact(text));
                } else {
                    System.out.println(summariseModel(text));
                }
            }
        }
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ");
    }

    /**
     * 模型摘要：每个 element 的 from/to + 用到的贴图键。
     */
    private static String summariseModel(String text) {
        final StringBuilder builder = new StringBuilder();
        final java.util.regex.Matcher elements = java.util.regex.Pattern
                .compile("\"from\"\\s*:\\s*\\[([^\\]]*)]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]*)]").matcher(text);
        int index = 0;
        while (elements.find()) {
            index++;
            builder.append("\n   element#").append(index)
                    .append(" from[").append(elements.group(1).replace(" ", ""))
                    .append("] to[").append(elements.group(2).replace(" ", "")).append(']');
            // 该 element 后面出现的面贴图
            final int end = Math.min(text.length(), elements.end() + 600);
            final java.util.regex.Matcher faces = java.util.regex.Pattern
                    .compile("\"(north|south|east|west|up|down)\"\\s*:\\s*\\{\\s*\"texture\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(text.substring(elements.end(), end));
            final StringBuilder faceText = new StringBuilder();
            while (faces.find()) {
                faceText.append(' ').append(faces.group(1)).append('=').append(faces.group(2));
            }
            builder.append("  faces:").append(faceText);
        }
        if (index == 0) {
            builder.append(compact(text));
        }
        return builder.toString();
    }
}
