package top.xiaotang27.trdm.fare.script;

/** 脚本编译或运行失败。 */
public class ScriptException extends RuntimeException {

    public ScriptException(String message) {
        super(message);
    }
}
