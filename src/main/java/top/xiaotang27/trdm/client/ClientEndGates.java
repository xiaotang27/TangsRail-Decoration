package top.xiaotang27.trdm.client;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** 客户端记录哪些玻璃格被标成了端门（由服务端 S2C 包写入，渲染直接读这里）。 */
public final class ClientEndGates {

    private static final Map<BlockPos, Boolean> FLAGS = new HashMap<>();
    private static final Map<BlockPos, Boolean> OPEN = new HashMap<>();

    private ClientEndGates() {
    }

    public static void set(BlockPos pos, boolean endGate) {
        if (endGate) {
            FLAGS.put(pos.immutable(), true);
        } else {
            FLAGS.remove(pos);
        }
    }

    public static boolean is(BlockPos pos) {
        return FLAGS.containsKey(pos);
    }

    public static void setOpen(BlockPos pos, boolean open) {
        if (open) {
            OPEN.put(pos.immutable(), true);
        } else {
            OPEN.remove(pos);
        }
    }

    public static boolean isOpen(BlockPos pos) {
        return OPEN.containsKey(pos);
    }
}
