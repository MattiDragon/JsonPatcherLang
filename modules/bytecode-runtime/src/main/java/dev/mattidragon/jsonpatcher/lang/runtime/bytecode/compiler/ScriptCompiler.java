package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ScriptCompiler {
    public static byte[] compile(Program program, TreeMetadata metadata, CompilerOptions options, Consumer<PreparationContextBuilder> contextBuilder, String scriptName, String className) {
        var globals = new HashSet<String>();
        contextBuilder.accept(new PreparationContextBuilder() {
            @Override
            public PreparationContextBuilder declareVariable(String name) {
                globals.add(name);
                return this;
            }
        });

        var diagnosticsBuilder = new DiagnosticsBuilder();
        VariableAnalyser.analyse(program, metadata, diagnosticsBuilder, globals);
        checkErrors(options.langConfig, diagnosticsBuilder.build());

        var functions = new HashMap<FunctionExpression, String>();
        findLambdas(program, functions, "");

        ConstantAnalyser.analyse(program, metadata);
        
        var compiler = new ClassCompiler(program, metadata, functions, scriptName, className, options);
        compiler.compileStart();
        compiler.compileMain();
        functions.keySet().forEach(compiler::compileLambda);
        
        return compiler.getBytes();
    }

    private static void findLambdas(ProgramNode node, Map<FunctionExpression, String> lambdas, String current) {
        if (node instanceof FunctionDeclarationStatement(String name, FunctionExpression e)) {
            current = name;
            lambdas.put(e, current);
            for (var child : e.getChildren()) {
                findLambdas(child, lambdas, current);
            }
            return;
        } else if (node instanceof FunctionExpression e) {
            current = current + "$lambda";
            lambdas.put(e, current);
        }
        for (var child : node.getChildren()) {
            findLambdas(child, lambdas, current);
        }
    }

    private static void checkErrors(LangConfig config, Diagnostics diagnostics) {
        var errors = diagnostics.errors();
        if (!errors.iterator().hasNext()) return;

        var errorMsg = errors.stream()
                .map(Diagnostic::toDisplay)
                .collect(Collectors.joining("\n"));
        throw new CompilationException(config, "Variable analysis failed:\n" + errorMsg, null);
    }
}
