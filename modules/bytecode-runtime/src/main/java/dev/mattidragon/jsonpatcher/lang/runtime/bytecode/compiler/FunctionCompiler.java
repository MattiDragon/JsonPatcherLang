package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.RootVariable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ReturnStatement;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.Box;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FunctionCompiler {
    private static final int GLOBAL_ROOT_VAR_INDEX = 1;
    private static final int GLOBALS_VAR_INDEX = 2;
    
    private final StatementCompiler statementCompiler;
    private final ExpressionCompiler expressionCompiler;
    private final Map<FunctionExpression, String> lambdaNames;
    private final MethodVisitor visitor;
    private final TreeMetadata metadata;
    private final String className;
    private int varCounter;
    private int rootNameCounter = 1;
    private int currentLine = 0;
    private final Map<Variable, Integer> variableAllocations = new HashMap<>();
    private final Map<RootVariable, Integer> rootAllocations = new HashMap<>();
    
    public FunctionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className, Map<FunctionExpression, String> lambdaNames) {
        statementCompiler = new StatementCompiler(metadata, visitor, className, this);
        expressionCompiler = new ExpressionCompiler(metadata, visitor, className, this);
        this.visitor = visitor;
        this.metadata = metadata;
        this.className = className;
        this.lambdaNames = lambdaNames;
    }
    
    public static void compileMainMethod(TreeMetadata metadata, ClassVisitor classVisitor, String className, Program program, Map<FunctionExpression, String> lambdaNames) {
        var visitor = classVisitor.visitMethod(Opcodes.ACC_PUBLIC,
                "run",
                Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.ObjectValue.class), Type.getType(Map.class)),
                null,
                null);
        visitor.visitParameter("$", 0);
        visitor.visitParameter("globals", 0);
        visitor.visitCode();
        var compiler = new FunctionCompiler(metadata, visitor, className, lambdaNames);
        compiler.varCounter = GLOBALS_VAR_INDEX + 1;
        compiler.compileProgram(program);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }
    
    public static void compileLambda(TreeMetadata metadata, ClassVisitor classVisitor, String className, FunctionExpression expression, String name, Map<FunctionExpression, String> lambdaNames) {
        var scope = (FunctionScope) metadata.get(expression,  VariableAnalyser.SCOPE).orElseThrow();
        var arguments = expression.args().arguments();
        
        var methodArgs = new ArrayList<Type>();
        var capturesRoot = arguments.stream().noneMatch(argument -> argument.target() == FunctionArgument.Target.Root.INSTANCE);

        for (int i = 0; i < scope.captures().size(); i++) {
            methodArgs.add(Type.getType(Box.class));
        }
        if (capturesRoot) methodArgs.add(Type.getType(Value.ObjectValue.class));
        for (var argument : arguments) {
            methodArgs.add(Type.getType(Value.class));
        }
        
        var visitor = classVisitor.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                name,
                Type.getMethodDescriptor(Type.getType(Value.class), methodArgs.toArray(Type[]::new)),
                null,
                null);

        var compiler = new FunctionCompiler(metadata, visitor, className, lambdaNames);
        compiler.varCounter = 1;

        for (var capture : scope.captures()) {
            compiler.getOrAllocateVariable(capture);
            visitor.visitParameter(capture.name(), 0);
        }
        if (capturesRoot) {
            compiler.getOrAllocateRoot(scope.root());
            visitor.visitParameter("$", 0);
        }

        for (var argument : arguments) {
            switch (argument.target()) {
                case FunctionArgument.Target.Root root -> {
                    compiler.getOrAllocateRoot(metadata.get(argument, VariableAnalyser.ROOT_REFERENCE).orElseThrow());
                    visitor.visitParameter("$", 0);
                }
                case FunctionArgument.Target.Variable variable -> {
                    compiler.getOrAllocateVariable(metadata.get(argument, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow());
                    visitor.visitParameter(variable.name(), 0);
                }
            }
        }
        
        compiler.compileLambda(expression);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }
    
    public String allocateRootName() {
        return "$" + rootNameCounter++;
    }
    
    public int allocateAnonymous() {
        return varCounter++;
    }
    
    public int getOrAllocateVariable(Variable variable) {
        return variableAllocations.computeIfAbsent(variable, var1 -> varCounter++);
    }
    
    public int getOrAllocateRoot(RootVariable root) {
        return rootAllocations.computeIfAbsent(root, var1 -> varCounter++);
    }
    
    public void loadContext() {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, className, "context", Type.getDescriptor(EvaluationContext.class));
    }
    
    public void emitLineNumber(int line) {
        if (line == currentLine) return;
        currentLine = line;
        var label = new Label();
        visitor.visitLabel(label);
        visitor.visitLineNumber(line, label);
    }
    
    public void compileVariableCreation(Variable variable, Runnable valueExpressionInserter) {
        var index = getOrAllocateVariable(variable);
        if (variable.isCaptured()) {
            visitor.visitTypeInsn(Opcodes.NEW, Types.BOX);
            visitor.visitInsn(Opcodes.DUP);
            valueExpressionInserter.run();
            visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Types.BOX, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class)), false);
            visitor.visitVarInsn(Opcodes.ASTORE, index);
        } else {
            visitor.visitVarInsn(Opcodes.ASTORE, index);
        }
    }

    public void compileProgram(Program program) {
        var scope = metadata.get(program, VariableAnalyser.SCOPE).orElseThrow();
        rootAllocations.put(scope.root(), GLOBAL_ROOT_VAR_INDEX);
        var startLabel = new Label();
        visitor.visitLabel(startLabel);
        for (var variable : scope.variables()) {
            if (variable.definition() != program) continue; // Filter globals, they are defined by the root node
            compileVariableCreation(variable, () -> {
                visitor.visitVarInsn(Opcodes.ALOAD, GLOBALS_VAR_INDEX);
                visitor.visitLdcInsn(variable.name());
                visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Map.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class)), true);
                visitor.visitTypeInsn(Opcodes.CHECKCAST, Types.VALUE);
            });
        }
        statementCompiler.compile(program);
        var endLabel = new Label();
        visitor.visitLabel(endLabel);

        for (var variable : scope.variables()) {
            if (variable.definition() != program) continue; // Filter globals, they are defined by the root node
            visitor.visitLocalVariable(variable.name(), variable.isCaptured() ? Type.getDescriptor(Box.class) : Type.getDescriptor(Value.class), null, startLabel, endLabel, getOrAllocateVariable(variable));
        }
    }

    private void compileLambda(FunctionExpression expression) {
        var arguments = expression.args().arguments();
        for (int i = 0; i < arguments.size(); i++) {
            var argument = arguments.get(i);
            if (argument.defaultValue().isPresent()) {
                var jump = new Label();
                visitor.visitVarInsn(Opcodes.ALOAD, i);
                visitor.visitJumpInsn(Opcodes.IFNONNULL, jump);
                compileExpression(argument.defaultValue().get());
                visitor.visitVarInsn(Opcodes.ASTORE, i);
                visitor.visitLabel(jump);
            }
        }

        statementCompiler.compile(expression.body());
        statementCompiler.compile(new ReturnStatement(Optional.empty()));
    }
    
    public void compileExpression(Expression expression) {
        expressionCompiler.compile(expression);
    }
    
    public String getLambdaName(FunctionExpression lambda) {
        return lambdaNames.get(lambda);
    }
}
