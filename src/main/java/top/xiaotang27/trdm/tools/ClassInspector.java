package top.xiaotang27.trdm.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 一次性小工具：打印若干类（含嵌套类）的字段与方法签名 —— 用来确认 YMTR 里
 * 客户端缓存的数据结构到底给了哪些信息（例如线路/站台方向），不靠猜。
 * <p>
 * 用法：{@code ./gradlew inspectClass -Pcls=mtr.client.ClientCache$PlatformRouteDetails}
 */
public final class ClassInspector {

    private ClassInspector() {
    }

    public static void main(String[] args) throws Exception {
        for (final String name : args) {
            inspect(name);
        }
    }

    private static void inspect(String name) {
        System.out.println("=== " + name);
        final Class<?> type;
        try {
            type = Class.forName(name, false, ClassInspector.class.getClassLoader());
        } catch (Throwable e) {
            System.out.println("  <not found: " + e + ">");
            return;
        }
        System.out.println("  modifiers: " + Modifier.toString(type.getModifiers()) + "  super: " + type.getSuperclass());
        final Field[] fields = type.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (final Field field : fields) {
            System.out.println("  field  " + Modifier.toString(field.getModifiers()) + " "
                    + field.getType().getTypeName() + " " + field.getName());
        }
        final Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (final Method method : methods) {
            final StringBuilder builder = new StringBuilder();
            builder.append("  method ").append(Modifier.toString(method.getModifiers())).append(' ')
                    .append(method.getReturnType().getTypeName()).append(' ').append(method.getName()).append('(');
            final Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(parameters[i].getTypeName());
            }
            System.out.println(builder.append(')').toString());
        }
        final Class<?>[] nested = type.getDeclaredClasses();
        for (final Class<?> inner : nested) {
            inspect(inner.getName());
        }
    }
}
