package top.xiaotang27.trdm.fare.script;

/** 脚本执行的统一入口：编译 + 运行，失败抛 {@link ScriptException}。 */
public final class ScriptRuntime {

    private ScriptRuntime() {
    }

    public static double evaluate(FareScript script, ScriptContext context) {
        return evaluate(script, context, null);
    }

    public static double evaluate(FareScript script, ScriptContext context, RuleResolver resolver) {
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        if (result.compiled() == null) {
            throw new ScriptException(String.join("；", result.errors()));
        }
        return result.compiled().run(context, resolver);
    }
}
