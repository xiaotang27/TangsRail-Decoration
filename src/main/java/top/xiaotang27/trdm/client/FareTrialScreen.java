package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareGraph;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.fare.FareRule;
import top.xiaotang27.trdm.fare.FareStation;
import top.xiaotang27.trdm.fare.script.ScriptCompiler;
import top.xiaotang27.trdm.fare.script.ScriptContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在现有线路/车站上试算当前规则脚本。 */
public final class FareTrialScreen extends Screen {

    private record TrialLine(long routeId, String name, List<Long> stationIds) {
    }

    private final Screen parent;
    private final FareScriptScreen editor;
    private final top.xiaotang27.trdm.fare.script.FareScript script;
    private final FareRule rule;
    private final List<TrialLine> lines = new ArrayList<>();

    private int lineIndex;
    private int fromIndex;
    private int toIndex;
    private String result = "";
    private boolean resultError;

    private Button lineButton;
    private Button fromButton;
    private Button toButton;

    FareTrialScreen(Screen parent, FareScriptScreen editor,
                    top.xiaotang27.trdm.fare.script.FareScript script, FareRule rule) {
        super(Component.translatable("gui.trdm.script.trial.title"));
        this.parent = parent;
        this.editor = editor;
        this.script = script == null ? new top.xiaotang27.trdm.fare.script.FareScript() : script.copy();
        this.rule = rule;
        loadLines();
    }

    private void loadLines() {
        for (final Route route : ClientData.ROUTES) {
            final List<Long> stationIds = new ArrayList<>();
            for (final Route.RoutePlatform platform : route.platformIds) {
                final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platform.platformId);
                if (station == null) {
                    continue;
                }
                if (stationIds.isEmpty() || stationIds.get(stationIds.size() - 1) != station.id) {
                    stationIds.add(station.id);
                }
            }
            if (stationIds.size() >= 2) {
                lines.add(new TrialLine(route.id,
                        route.name == null || route.name.isBlank()
                                ? Component.translatable("gui.trdm.script.trial.unnamed_line").getString()
                                : route.name,
                        List.copyOf(stationIds)));
            }
        }
        lineIndex = 0;
        fromIndex = 0;
        toIndex = 1;
    }

    @Override
    protected void init() {
        final int x = 24;
        final int w = Math.max(120, this.width - 48);
        lineButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleLine(1))
                .bounds(x + 24, 56, w - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleLine(-1))
                .bounds(x, 56, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleLine(1))
                .bounds(x + w - 20, 56, 20, 20).build());

        fromButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleFrom(1))
                .bounds(x + 24, 96, w - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleFrom(-1))
                .bounds(x, 96, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleFrom(1))
                .bounds(x + w - 20, 96, 20, 20).build());

        toButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleTo(1))
                .bounds(x + 24, 136, w - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleTo(-1))
                .bounds(x, 136, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleTo(1))
                .bounds(x + w - 20, 136, 20, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.trial.calculate"),
                        b -> calculate())
                .bounds(this.width / 2 - 100, 176, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        b -> onClose())
                .bounds(this.width / 2 + 10, 176, 90, 20).build());
        rebuildLabels();
    }

    private void cycleLine(int direction) {
        if (lines.isEmpty()) {
            return;
        }
        lineIndex = Mth.positiveModulo(lineIndex + direction, lines.size());
        fromIndex = 0;
        toIndex = 1;
        result = "";
        rebuildLabels();
    }

    private void cycleFrom(int direction) {
        final List<Long> stations = stations();
        if (stations.isEmpty()) {
            return;
        }
        fromIndex = Mth.positiveModulo(fromIndex + direction, stations.size());
        if (fromIndex == toIndex) {
            fromIndex = Mth.positiveModulo(fromIndex + direction, stations.size());
        }
        result = "";
        rebuildLabels();
    }

    private void cycleTo(int direction) {
        final List<Long> stations = stations();
        if (stations.isEmpty()) {
            return;
        }
        toIndex = Mth.positiveModulo(toIndex + direction, stations.size());
        if (toIndex == fromIndex) {
            toIndex = Mth.positiveModulo(toIndex + direction, stations.size());
        }
        result = "";
        rebuildLabels();
    }

    private List<Long> stations() {
        return lines.isEmpty() ? List.of() : lines.get(lineIndex).stationIds();
    }

    private void rebuildLabels() {
        if (lineButton == null) {
            return;
        }
        final TrialLine line = lines.isEmpty() ? null : lines.get(lineIndex);
        lineButton.setMessage(Component.literal(line == null
                ? Component.translatable("gui.trdm.script.trial.no_line").getString()
                : line.name() + "  ·  " + line.stationIds().size() + " 站"));
        final List<Long> stations = stations();
        fromButton.setMessage(Component.literal(stations.isEmpty() ? "-" : stationName(stations.get(fromIndex))));
        toButton.setMessage(Component.literal(stations.isEmpty() ? "-" : stationName(stations.get(toIndex))));
    }

    private String stationName(long id) {
        final FareGraph graph = FareRuntime.get().graph();
        final FareStation station = graph.station(id);
        return station == null ? Long.toString(id) : station.name();
    }

    private void calculate() {
        resultError = false;
        if (lines.isEmpty()) {
            result = Component.translatable("gui.trdm.script.trial.no_line").getString();
            resultError = true;
            return;
        }
        final List<Long> stations = stations();
        if (stations.size() < 2) {
            result = Component.translatable("gui.trdm.script.trial.no_station").getString();
            resultError = true;
            return;
        }
        final TrialLine line = lines.get(lineIndex);
        final FareConfig config = FareRuntime.get().config();
        final FareGraph graph = FareRuntime.get().graph();
        final long from = stations.get(fromIndex);
        final long to = stations.get(toIndex);
        final FareGraph.Path path = graph.shortest(from, to, config.metric);
        if (path == null || !path.connected()) {
            result = Component.translatable("gui.trdm.script.trial.unreachable").getString();
            resultError = true;
            return;
        }
        final FareStation fromStation = graph.station(from);
        final FareStation toStation = graph.station(to);
        final int fromZone = fromStation == null ? 0 : fromStation.zone();
        final int toZone = toStation == null ? 0 : toStation.zone();
        final Set<String> visiting = new HashSet<>();
        if (rule != null) {
            visiting.add(rule.id);
        }
        try {
            final ScriptCompiler.Result compiled = ScriptCompiler.compile(script);
            if (!compiled.errors().isEmpty()) {
                result = Component.translatable("gui.trdm.script.trial.invalid",
                        String.join("；", compiled.errors())).getString();
                resultError = true;
                return;
            }
            final ScriptContext context = new ScriptContext(path.intervals(), path.meters(),
                    fromZone, toZone, line.routeId(), line.name(), 0L, 0L, 0L);
            double fare = compiled.compiled().run(context, config.ruleResolver(visiting));
            long cents = Math.max(0L, Math.round(fare * 100.0));
            if (rule != null && rule.maxFareCents > 0L) {
                cents = Math.min(cents, rule.maxFareCents);
            }
            result = Component.translatable("gui.trdm.script.trial.result",
                    path.intervals(), path.meters(), fromZone, toZone,
                    FareMoney.yuan(cents)).getString();
        } catch (RuntimeException exception) {
            result = Component.translatable("gui.trdm.script.trial.failed",
                    String.valueOf(exception.getMessage())).getString();
            resultError = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.trdm.script.trial.line"),
                24, 44, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.script.trial.from"),
                24, 84, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.script.trial.to"),
                24, 124, 0xA0A0A0, false);
        if (!result.isEmpty()) {
            final int color = resultError ? 0xFFFF8080 : 0xFF80FFA0;
            for (final net.minecraft.util.FormattedCharSequence line
                    : this.font.split(Component.literal(result), Math.max(120, this.width - 48))) {
                graphics.drawString(this.font, line, 24, 220, color, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
