package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

public class ScriptCompiler {
    public static byte[] compile(Program program, TreeMetadata metadata, LangConfig config, Consumer<PreparationContextBuilder> contextBuilder) {
        var globals = new HashSet<String>();
        contextBuilder.accept(new PreparationContextBuilder() {
            @Override
            public PreparationContextBuilder declareVariable(String name) {
                globals.add(name);
                return this;
            }
        });
        
        var variableAnalysis = VariableAnalyser.analyse(program, metadata, globals);
        checkErrors(config, variableAnalysis);
        var functions = new HashMap<FunctionExpression, String>();
        findLambdas(program, functions, "");
        
        var compiler = new ClassCompiler(program, metadata, functions);
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

    private static void checkErrors(LangConfig config, VariableAnalysis variableAnalysis) {
        var errors = variableAnalysis.errors();
        if (errors.isEmpty()) return;
        
        CompilationException e = null;
        for (var error : errors) {
            var subException = switch (error) {
                case VariableAnalyser.AnalysisError.DuplicateVariable duplicateVariable ->
                        new CompilationException(config, "Variable %s would shadow other variable by the same name".formatted(duplicateVariable.getVariableName()), duplicateVariable.getPos());
                case VariableAnalyser.AnalysisError.MissingVariable missingVariable ->
                        new CompilationException(config, "Cannot find variable called %s".formatted(missingVariable.getVariableName()), missingVariable.getPos());
            };
            if (e == null) {
                e = subException;
            } else {
                e.addSuppressed(subException);
            }
        }
        throw e;
    }
}
