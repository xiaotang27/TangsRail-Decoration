package top.xiaotang27.trdm.fare;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.ScriptNode;
import top.xiaotang27.trdm.fare.script.ScriptTier;
import top.xiaotang27.trdm.ticket.PassRule;
import top.xiaotang27.trdm.ticket.PassRules;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/**
 * {@link FareConfig} 的 JSON 编解码，默认落盘位置 {@code config/trdm-fares.json}。
 * <p>
 * 金额按“元”写（{@code 2.0}、{@code 2.5}），读入时精确换算成分，方便管理员手改。
 * 写盘先写临时文件再原子替换，并保留上一版 {@code .bak}。
 */
public final class FareJson {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private FareJson() {
    }

    /** 文件不存在返回 {@code null}；内容非法抛异常，由调用方决定回退策略。 */
    public static FareConfig tryLoad(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** 读取失败时静默回退默认配置。 */
    public static FareConfig loadOrDefault(Path path) {
        try {
            final FareConfig config = tryLoad(path);
            return config == null ? FareDefaults.config() : config;
        } catch (Exception exception) {
            return FareDefaults.config();
        }
    }

    public static FareConfig parse(String text) {
        final JsonElement rootElement = JsonParser.parseString(text);
        if (!rootElement.isJsonObject()) {
            throw new JsonParseException("配置根节点必须是对象");
        }
        final JsonObject root = rootElement.getAsJsonObject();
        final FareConfig config = new FareConfig();
        config.schemaVersion = integer(root, "schemaVersion", FareConfig.SCHEMA_VERSION);
        config.revision = longValue(root, "revision", 0L);
        config.enabled = bool(root, "enabled", true);
        config.mode = enumValue(root, "mode", FareConfig.Mode.class, FareConfig.Mode.CUSTOM);
        config.metric = enumValue(root, "pathMetric", FareConfig.Metric.class, FareConfig.Metric.INTERVALS);
        config.transferDiscountCents = money(root, "transferDiscount", 0L);
        config.mergeSameRule = bool(root, "mergeSameRule", true);

        if (root.has("rules") && root.get("rules").isJsonArray()) {
            for (final JsonElement element : root.getAsJsonArray("rules")) {
                if (element.isJsonObject()) {
                    config.rules.add(parseRule(element.getAsJsonObject()));
                }
            }
        }
        if (root.has("routeRules") && root.get("routeRules").isJsonObject()) {
            for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject("routeRules").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    config.routeRules.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        if (root.has("passes") && root.get("passes").isJsonArray()) {
            for (final JsonElement element : root.getAsJsonArray("passes")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject pass = element.getAsJsonObject();
                final String id = string(pass, "id", "");
                if (id.isBlank()) {
                    continue;
                }
                config.passRules.add(new PassRule(id, string(pass, "name", id),
                        integer(pass, "price", 10),
                        Math.max(1, integer(pass, "days", 1))));
            }
        } else {
            config.passRules.addAll(PassRules.defaults());
        }
        return config;
    }

    public static String write(FareConfig config) {
        final JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", FareConfig.SCHEMA_VERSION);
        root.addProperty("revision", config.revision);
        root.addProperty("enabled", config.enabled);
        root.addProperty("mode", config.mode.name());
        root.addProperty("pathMetric", config.metric.name());
        addMoney(root, "transferDiscount", config.transferDiscountCents);
        root.addProperty("mergeSameRule", config.mergeSameRule);

        final JsonArray rules = new JsonArray();
        for (final FareRule rule : config.rules) {
            final JsonObject item = new JsonObject();
            item.addProperty("id", rule.id);
            item.addProperty("name", rule.name);
            item.addProperty("enabled", rule.enabled);
            item.addProperty("kind", rule.kind.name());
            addMoney(item, "maxFare", rule.maxFareCents);
            if (rule.kind == FareRule.Kind.FLAT) {
                addMoney(item, "fare", rule.flatFareCents);
            }
            if (!rule.tiers.isEmpty()) {
                final JsonArray tiers = new JsonArray();
                for (final FareRule.Tier tier : rule.tiers) {
                    final JsonObject tierObject = new JsonObject();
                    if (tier.upTo() != null) {
                        tierObject.addProperty("upTo", tier.upTo());
                    }
                    addMoney(tierObject, "fare", tier.fareCents());
                    tiers.add(tierObject);
                }
                item.add("tiers", tiers);
            }
            if (rule.kind == FareRule.Kind.SCRIPT && rule.script != null) {
                item.add("script", writeScript(rule.script));
            }
            rules.add(item);
        }
        root.add("rules", rules);

        final JsonObject bindings = new JsonObject();
        for (final Map.Entry<String, String> entry : config.routeRules.entrySet()) {
            bindings.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("routeRules", bindings);

        final JsonArray passes = new JsonArray();
        for (final PassRule pass : config.passRules) {
            final JsonObject item = new JsonObject();
            item.addProperty("id", pass.id);
            item.addProperty("name", pass.name);
            item.addProperty("price", pass.priceEmeralds);
            item.addProperty("days", pass.days);
            passes.add(item);
        }
        root.add("passes", passes);
        return GSON.toJson(root);
    }

    public static void save(Path path, FareConfig config) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final Path fileName = path.getFileName();
        final Path temp = parent == null ? Path.of(fileName + ".tmp") : parent.resolve(fileName + ".tmp");
        Files.writeString(temp, write(config), StandardCharsets.UTF_8);
        if (Files.isRegularFile(path)) {
            Files.copy(path, path.resolveSibling(fileName + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ===== 解析工具 =====

    private static FareRule parseRule(JsonObject object) {
        final String id = string(object, "id", null);
        if (id == null || id.isBlank()) {
            throw new JsonParseException("规则缺少 id");
        }
        final String name = string(object, "name", id);
        final String kindName = string(object, "kind", FareRule.Kind.FLAT.name());
        final FareRule.Kind kind;
        try {
            kind = FareRule.Kind.valueOf(kindName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("未知的规则类型: " + kindName);
        }
        final FareRule rule = new FareRule(id, name, kind);
        rule.enabled = bool(object, "enabled", true);
        rule.maxFareCents = money(object, "maxFare", 0L);
        rule.flatFareCents = money(object, "fare", 0L);
        if (object.has("tiers") && object.get("tiers").isJsonArray()) {
            for (final JsonElement element : object.getAsJsonArray("tiers")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject tier = element.getAsJsonObject();
                final Long upTo = tier.has("upTo") && !tier.get("upTo").isJsonNull()
                        ? tier.get("upTo").getAsLong() : null;
                rule.tiers.add(new FareRule.Tier(upTo, money(tier, "fare", 0L)));
            }
        }
        if (kind == FareRule.Kind.SCRIPT && object.has("script") && object.get("script").isJsonObject()) {
            rule.script = parseScript(object.getAsJsonObject("script"));
        }
        return rule;
    }

    // ===== 积木脚本编解码 =====

    private static FareScript parseScript(JsonObject object) {
        final FareScript script = new FareScript();
        script.schemaVersion = integer(object, "schemaVersion", FareScript.SCHEMA_VERSION);
        script.hat = string(object, "hat", "");
        if (object.has("nodes") && object.get("nodes").isJsonArray()) {
            for (final JsonElement element : object.getAsJsonArray("nodes")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject nodeObject = element.getAsJsonObject();
                final ScriptNode node = new ScriptNode(
                        string(nodeObject, "id", ""), string(nodeObject, "type", ""));
                node.next = string(nodeObject, "next", null);
                node.otherwise = string(nodeObject, "otherwise", null);
                node.x = integer(nodeObject, "x", 0);
                node.y = integer(nodeObject, "y", 0);
                if (nodeObject.has("inputs") && nodeObject.get("inputs").isJsonObject()) {
                    for (final Map.Entry<String, JsonElement> entry
                            : nodeObject.getAsJsonObject("inputs").entrySet()) {
                        node.inputs.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                if (nodeObject.has("fields") && nodeObject.get("fields").isJsonObject()) {
                    for (final Map.Entry<String, JsonElement> entry
                            : nodeObject.getAsJsonObject("fields").entrySet()) {
                        node.fields.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                if (nodeObject.has("tiers") && nodeObject.get("tiers").isJsonArray()) {
                    for (final JsonElement tierElement : nodeObject.getAsJsonArray("tiers")) {
                        if (!tierElement.isJsonObject()) {
                            continue;
                        }
                        final JsonObject tierObject = tierElement.getAsJsonObject();
                        final Double upTo = tierObject.has("upTo") && !tierObject.get("upTo").isJsonNull()
                                ? tierObject.get("upTo").getAsDouble() : null;
                        node.tiers.add(new ScriptTier(upTo, doubleValue(tierObject.get("fare"))));
                    }
                }
                script.nodes.add(node);
            }
        }
        return script;
    }

    private static JsonObject writeScript(FareScript script) {
        final JsonObject object = new JsonObject();
        object.addProperty("schemaVersion", script.schemaVersion);
        object.addProperty("hat", script.hat);
        final JsonArray nodes = new JsonArray();
        for (final ScriptNode node : script.nodes) {
            final JsonObject nodeObject = new JsonObject();
            nodeObject.addProperty("id", node.id);
            nodeObject.addProperty("type", node.type);
            if (node.next != null) {
                nodeObject.addProperty("next", node.next);
            }
            if (node.otherwise != null) {
                nodeObject.addProperty("otherwise", node.otherwise);
            }
            nodeObject.addProperty("x", node.x);
            nodeObject.addProperty("y", node.y);
            if (!node.inputs.isEmpty()) {
                final JsonObject inputs = new JsonObject();
                for (final Map.Entry<String, String> entry : node.inputs.entrySet()) {
                    inputs.addProperty(entry.getKey(), entry.getValue());
                }
                nodeObject.add("inputs", inputs);
            }
            if (!node.fields.isEmpty()) {
                final JsonObject fields = new JsonObject();
                for (final Map.Entry<String, String> entry : node.fields.entrySet()) {
                    fields.addProperty(entry.getKey(), entry.getValue());
                }
                nodeObject.add("fields", fields);
            }
            if (!node.tiers.isEmpty()) {
                final JsonArray tiers = new JsonArray();
                for (final ScriptTier tier : node.tiers) {
                    final JsonObject tierObject = new JsonObject();
                    if (tier.upTo() != null) {
                        tierObject.addProperty("upTo", tier.upTo());
                    }
                    tierObject.addProperty("fare", tier.fareYuan());
                    tiers.add(tierObject);
                }
                nodeObject.add("tiers", tiers);
            }
            nodes.add(nodeObject);
        }
        object.add("nodes", nodes);
        return object;
    }

    private static double doubleValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0.0;
        }
        if (element.getAsJsonPrimitive().isString()) {
            return Double.parseDouble(element.getAsString().trim());
        }
        return element.getAsDouble();
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        final JsonElement element = object.get(key);
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return Boolean.parseBoolean(element.getAsString());
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return (int) longValue(object, key, fallback);
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsLong();
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String key, Class<E> type, E fallback) {
        final String value = string(object, key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static long money(JsonObject object, String key, long fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        final JsonElement element = object.get(key);
        final BigDecimal yuan;
        if (element.getAsJsonPrimitive().isString()) {
            yuan = new BigDecimal(element.getAsString().trim());
        } else {
            yuan = element.getAsBigDecimal();
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static void addMoney(JsonObject object, String key, long cents) {
        object.addProperty(key, BigDecimal.valueOf(Math.max(0L, cents), 2));
    }
}
