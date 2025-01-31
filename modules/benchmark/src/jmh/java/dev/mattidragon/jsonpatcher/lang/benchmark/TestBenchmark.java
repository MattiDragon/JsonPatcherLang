package dev.mattidragon.jsonpatcher.lang.benchmark;

import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationEnvironment;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.legacy.LegacyRuntime;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class TestBenchmark {
    private final LangConfig config = new LangConfig(LangConfig.StackTraceMode.JAVA);

    @Param({ "5", "10", "20" })
    public int index;

    private EvaluationEnvironment.AddedProgram bytecodeProgram;
    private EvaluationEnvironment.AddedProgram bytecodeProgramWithoutCondy;
    private PreparedProgram legacyProgram;

    @Setup
    public void compileCode() {
        var code = """
                function fib(index) {
                    if (index == 0 || index == 1) return 1;
                    return fib(index - 2) + fib(index - 1);
                }
                return fib($index);
                """;

        var diagnostics = new DiagnosticsBuilder();

        var lex = Lexer.lex(code, "fib", diagnostics);
        var parse = Parser.parse(lex.tokens(), diagnostics);

        var issues = diagnostics.build().errorsAndWarnings();
        if (!issues.isEmpty()) {
            for (var issue : issues) {
                System.out.println(issue.toDisplay());
            }
            throw new IllegalStateException("Issues in parse");
        }

        var env = new EvaluationEnvironment(CompilerOptions.builder().build());
        env.enableDumping("dump-with-condy");
        bytecodeProgram = env.addProgram(parse.program(), parse.treeMetadata(), "fib", "CompiledFib");
        var env2 = new EvaluationEnvironment(CompilerOptions.builder().disableDynamicConstants().build());
        env2.enableDumping("dump-without-condy");
        bytecodeProgramWithoutCondy = env2.addProgram(parse.program(), parse.treeMetadata(), "fib2", "CompiledFib2");

        legacyProgram = new LegacyRuntime().prepare(parse.program(), parse.treeMetadata(), PreparationContextBuilder::declareStdlib);
    }

    @TearDown
    public void removeCompiled() {
        bytecodeProgram = null;
    }

    @Benchmark
    public double fibonacciJavaDouble() {
        return fibDoubleImpl(index);
    }

    private double fibDoubleImpl(double index) {
        if (index == 0 || index == 1) return 1;
        return fibDoubleImpl(index - 2) + fibDoubleImpl(index - 1);
    }

    @Benchmark
    public int fibonacciJavaInt() {
        return fibIntImpl(index);
    }

    private int fibIntImpl(int index) {
        if (index == 0 || index == 1) return 1;
        return fibIntImpl(index - 2) + fibIntImpl(index - 1);
    }

    @Benchmark
    public Value fibonacciBytecode() {
        var root = new Value.ObjectValue();
        root.value().put("index", new Value.NumberValue(index));
        return bytecodeProgram.run(root);
    }

    @Benchmark
    public Value fibonacciBytecodeWithoutCondy() {
        var root = new Value.ObjectValue();
        root.value().put("index", new Value.NumberValue(index));
        return bytecodeProgramWithoutCondy.run(root);
    }

    @Benchmark
    public Value fibonacciLegacy() {
        var root = new Value.ObjectValue();
        root.value().put("index", new Value.NumberValue(index));
        return legacyProgram.run(builder -> builder.addStdlib().root(root), config);
    }
}
