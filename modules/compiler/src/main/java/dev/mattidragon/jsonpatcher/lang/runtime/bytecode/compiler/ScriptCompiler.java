package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ScriptCompiler {
    public static byte[] compile(Program program, TreeMetadata metadata, CompilerOptions options, Set<String> globals, String scriptName, String className, DiagnosticsBuilder diagnosticsBuilder) {
        VariableAnalyser.analyse(program, metadata, diagnosticsBuilder, globals);
        // If there are any errors from variable analysis (or previous step) then we can't safely compile the code.
        checkErrors(diagnosticsBuilder.build());

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

    private static void checkErrors(Diagnostics diagnostics) {
        var errors = diagnostics.errors();
        if (!errors.iterator().hasNext()) return;

        throw new CompilationException(errors);
    }
}
