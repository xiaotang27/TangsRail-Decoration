package top.xiaotang27.trdm.tools;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 一次性小工具：直接读 class 文件的常量池，列出某个类引用了哪些成员（字段/方法）。
 * <p>
 * 用来确认第三方 mod 的<b>实际字节码</b>里到底有没有读某个状态位
 * （例如 MTR 的 {@code TrainServer} 有没有读 {@code BlockPSDAPGDoorBase.UNLOCKED}），
 * 免得只凭源码分支的推测。
 * <p>
 * 用法：{@code ./gradlew classRefs -Pjar=… -Pentry=mtr/data/TrainServer.class -Pfilter=UNLOCKED}
 */
public final class ClassRefs {

    private ClassRefs() {
    }

    public static void main(String[] args) throws Exception {
        final String jarPath = args.length > 0 ? args[0] : "";
        final String entryName = args.length > 1 ? args[1] : "";
        final String filter = args.length > 2 ? args[2] : "";
        final boolean utfMode = args.length > 3 && args[3].equalsIgnoreCase("utf");

        if (jarPath.isEmpty() || entryName.isEmpty()) {
            System.out.println("usage: classRefs -Pjar=<jar> -Pentry=<entry.class> [-Pfilter=<substring>] [utf]");
            return;
        }

        try (ZipFile zipFile = new ZipFile(jarPath)) {
            if (entryName.equals("*")) {
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    final String name = entries.nextElement().getName();
                    if (name.endsWith(".class") && name.contains(filter)) {
                        System.out.println(name);
                    }
                }
                return;
            }

            final ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                System.out.println("entry not found: " + entryName);
                return;
            }
            try (InputStream stream = zipFile.getInputStream(entry)) {
                final Pool pool = new Pool(new DataInputStream(stream));
                if (utfMode) {
                    // 打印命中的 UTF-8 常量：mixin 的 @Inject(method = "...") 就在这里面，
                    // 从里面能看到生产包里的方法描述符有没有被 remap（例如 class_1937 = Level）
                    System.out.println("=== " + entryName + " utf8 constants" + (filter.isEmpty() ? "" : " matching '" + filter + "'"));
                    pool.utf8().stream()
                            .filter(text -> filter.isEmpty() || text.contains(filter))
                            .forEach(text -> System.out.println("  " + text));
                    return;
                }
                System.out.println("=== " + entryName + " member refs" + (filter.isEmpty() ? "" : " matching '" + filter + "'"));
                for (final Member member : pool.members()) {
                    if (filter.isEmpty() || member.describe().contains(filter)) {
                        System.out.println("  " + member.describe());
                    }
                }
            }
        }
    }

    private record Member(String owner, String name, String descriptor) {

        private String describe() {
            return owner + "  " + name + ":" + descriptor;
        }
    }

    /** 极简常量池解析：只为了把 Fieldref / Methodref 取出来。 */
    private static final class Pool {

        private final int[] tags;
        private final Object[] values;
        private final int count;

        private Pool(DataInputStream in) throws Exception {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            count = in.readUnsignedShort();
            tags = new int[count];
            values = new Object[count];
            for (int index = 1; index < count; index++) {
                final int tag = in.readUnsignedByte();
                tags[index] = tag;
                switch (tag) {
                    case 1 -> values[index] = in.readUTF();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        index++; // long / double 占两个槽位
                    }
                    case 7, 8, 16, 19, 20 -> values[index] = in.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> values[index] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 15 -> {
                        in.readUnsignedByte();
                        values[index] = in.readUnsignedShort();
                    }
                    default -> throw new IllegalStateException("unknown constant pool tag " + tag + " at " + index);
                }
            }
        }

        private List<Member> members() {
            final List<Member> members = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                if (tags[index] != 9 && tags[index] != 10 && tags[index] != 11) {
                    continue;
                }
                final int[] pair = (int[]) values[index];
                final String owner = className(pair[0]);
                final int[] nameAndType = (int[]) values[pair[1]];
                final String name = (String) values[nameAndType[0]];
                final String descriptor = (String) values[nameAndType[1]];
                members.add(new Member(owner, name, descriptor));
            }
            return members;
        }

        private String className(int classIndex) {
            final Object value = values[classIndex];
            if (value instanceof Integer index) {
                return (String) values[index];
            }
            return "?";
        }

        private List<String> utf8() {
            final List<String> texts = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                if (tags[index] == 1) {
                    texts.add((String) values[index]);
                }
            }
            return texts;
        }
    }
}
