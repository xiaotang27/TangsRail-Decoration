package top.xiaotang27.trdm.render;

import top.xiaotang27.trdm.Trdm;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 屏蔽门警示灯状态：<b>只在门真的在动的时候闪</b>。
 * <p>
 * 两个输入：服务器同步来的目标开度 {@code targetValue}（0~MAX_OPEN_VALUE）和
 * 「插值开度有没有追上目标」这个布尔量 {@code moving}。门在动 → 按世界 tick 量化地闪
 * （20 tick = 1 秒，亮半秒灭半秒）；门停稳 → 按目标定格：开着常亮、关着熄灭。
 * <p>
 * 所以<b>门完全关上之后灯就灭了</b>，不会再多闪两秒；指示灯的明灭节奏与世界 tick 绑定，
 * 不看逐帧插值，一个 tick 内状态恒定，不会抽搐。
 */
final class TrdmDoorLight {

    /** 门停稳后再多闪这么多 tick，避免插值在临界点抖动导致灯闪断。 */
    private static final int STOP_TAIL_TICKS = 2;

    private static final boolean DEBUG = Boolean.getBoolean("trdm.debugDoorLight");
    private static final Map<Object, State> STATES = new WeakHashMap<>();

    private TrdmDoorLight() {
    }

    /**
     * @param key         门（用方块实体当键，一扇门一条状态）
     * @param targetValue 目标开度（0 = 关，MAX_OPEN_VALUE = 全开）
     * @param moving      门的插值开度还没追上目标（= 门正在动）
     * @param gameTime    世界 tick
     */
    static boolean isOn(Object key, int targetValue, boolean moving, long gameTime) {
        final State state;
        synchronized (STATES) {
            state = STATES.computeIfAbsent(key, ignored -> new State());
        }

        if (moving) {
            state.movingUntil = gameTime + STOP_TAIL_TICKS;
        }
        final boolean blinking = moving || gameTime <= state.movingUntil;
        final boolean on = blinking ? isFlashOn(gameTime) : targetValue > 0;

        if (DEBUG && on != state.lastLogged) {
            state.lastLogged = on;
            Trdm.LOGGER.info("[door light] tick={} target={} moving={} on={}", gameTime, targetValue, moving, on);
        }
        return on;
    }

    /**
     * 亮 0.5s / 灭 0.5s 的方波，按世界 tick 量化：20 tick = 1 秒，
     * 所以 {@code (gameTime / 10) % 2 == 0} 就是「亮半秒、灭半秒」（1Hz）。
     */
    static boolean isFlashOn(long gameTime) {
        return (gameTime / 10L) % 2 == 0;
    }

    private static final class State {

        private long movingUntil;
        private boolean lastLogged;
    }
}
