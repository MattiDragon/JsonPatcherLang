package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.*;
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

import java.util.*;

public class FunctionCompiler implements Opcodes {
    private static final int GLOBAL_ROOT_VAR_INDEX = 1;
    private static final int GLOBALS_VAR_INDEX = 2;
    
    private final StatementCompiler statementCompiler;
    private final ExpressionCompiler expressionCompiler;
    private final Map<FunctionExpression, String> lambdaNames;
    private final MethodVisitor visitor;
    private final TreeMetadata metadata;
    private final String className;
    private final CompilerOptions options;
    private int varCounter;
    private int rootNameCounter = 1;
    private int currentLine = 0;
    private final Map<Variable, Integer> variableAllocations = new HashMap<>();
    private final Map<RootVariable, Integer> rootAllocations = new HashMap<>();
    
    public FunctionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className, Map<FunctionExpression, String> lambdaNames, CompilerOptions options) {
        statementCompiler = new StatementCompiler(metadata, visitor, className, this);
        expressionCompiler = new ExpressionCompiler(metadata, visitor, className, this);
        this.visitor = visitor;
        this.metadata = metadata;
        this.className = className;
        this.lambdaNames = lambdaNames;
        this.options = options;
    }
    
    public static void compileMainMethod(TreeMetadata metadata, ClassVisitor classVisitor, String className, Program program, Map<FunctionExpression, String> lambdaNames, CompilerOptions options) {
        // Method header
        var visitor = classVisitor.visitMethod(ACC_PUBLIC,
                "run",
                Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.ObjectValue.class), Type.getType(Map.class)),
                null,
                null);
        visitor.visitParameter("$", 0);
        visitor.visitParameter("globals", 0);
        visitor.visitCode();
        
        var compiler = new FunctionCompiler(metadata, visitor, className, lambdaNames, options);
        compiler.varCounter = 3; // This, root and the global map take up the first three local slots

        // Set the variable index of the top level root value to the passed in parameter
        var scope = compiler.metadata.get(program, VariableAnalyser.SCOPE).orElseThrow();
        compiler.rootAllocations.put(scope.root(), GLOBAL_ROOT_VAR_INDEX); 
        
        var startLabel = new Label();
        visitor.visitLabel(startLabel);
        
        compiler.compileGlobalLoading(program, scope, visitor);
        compiler.statementCompiler.compile(program);
        
        var endLabel = new Label();
        visitor.visitLabel(endLabel);

        compiler.addGlobalMetadata(program, scope, visitor, startLabel, endLabel);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    public static void compileLambda(TreeMetadata metadata, ClassVisitor classVisitor, String className, FunctionExpression expression, String name, Map<FunctionExpression, String> lambdaNames, CompilerOptions options) {
        var scope = (FunctionScope) metadata.get(expression,  VariableAnalyser.SCOPE).orElseThrow();
        var arguments = expression.args().arguments();
        var capturesRoot = arguments.stream().noneMatch(argument -> argument.target() == FunctionArgument.Target.Root.INSTANCE);

        var visitor = classVisitor.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC,
                name,
                getLambdaMethodDescriptor(scope, capturesRoot, arguments),
                null,
                null);

        var compiler = new FunctionCompiler(metadata, visitor, className, lambdaNames, options);
        compiler.varCounter = 1;

        compiler.allocateLambdaCaptures(scope, visitor, capturesRoot);

        compiler.compileLambdaArgProcessing(metadata, arguments, visitor);

        compiler.statementCompiler.compile(expression.body());
        compiler.statementCompiler.compile(new ReturnStatement(Optional.empty()));
        
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static String getLambdaMethodDescriptor(FunctionScope scope, boolean capturesRoot, List<FunctionArgument> functionArgs) {
        var args = new ArrayList<Type>();

        for (int i = 0; i < scope.captures().size(); i++) {
            args.add(Type.getType(Box.class));
        }
        if (capturesRoot) args.add(Type.getType(Value.ObjectValue.class));
        for (int i = 0; i < functionArgs.size(); i++) {
            args.add(Type.getType(Value.class));
        }
        return Type.getMethodDescriptor(Type.getType(Value.class), args.toArray(Type[]::new));
    }

    private void allocateLambdaCaptures(FunctionScope scope, MethodVisitor visitor, boolean capturesRoot) {
        for (var capture : scope.captures()) {
            getOrAllocateVariable(capture);
            visitor.visitParameter(capture.name(), 0);
        }
        if (capturesRoot) {
            getOrAllocateRoot(scope.root());
            visitor.visitParameter("$", 0);
        }
    }

    private void compileLambdaArgProcessing(TreeMetadata metadata, List<FunctionArgument> arguments, MethodVisitor visitor) {
        for (var argument : arguments) {
            boolean captured;
            int varIndex;
            switch (argument.target()) {
                case FunctionArgument.Target.Root root -> {
                    varIndex = getOrAllocateRoot(metadata.get(argument, VariableAnalyser.ROOT_REFERENCE).orElseThrow());
                    captured = false;
                    visitor.visitParameter("$", 0);
                }
                case FunctionArgument.Target.Variable variable -> {
                    var analysedVariable = metadata.get(argument, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow();
                    varIndex = getOrAllocateVariable(analysedVariable);
                    captured = analysedVariable.isCaptured();
                    visitor.visitParameter(variable.name(), 0);
                }
            }

            if (captured) {
                visitor.visitTypeInsn(NEW, Types.BOX);
                visitor.visitInsn(DUP);
            }

            this.visitor.visitVarInsn(ALOAD, varIndex);
            var jump = new Label();

            if (argument.defaultValue().isPresent()) {
                this.visitor.visitJumpInsn(IFNONNULL, jump);
                compileExpression(argument.defaultValue().get());
            }

            if (captured) {
                visitor.visitMethodInsn(INVOKESPECIAL, Types.BOX, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class)), false);
            }

            this.visitor.visitVarInsn(ASTORE, varIndex);
            this.visitor.visitLabel(jump);
        }
    }

    public CompilerOptions options() {
        return options;
    }

    private void addGlobalMetadata(Program program, Scope scope, MethodVisitor visitor, Label startLabel, Label endLabel) {
        for (var variable : scope.variables()) {
            if (variable.definition() != program) continue; // Filter globals, they are defined by the root node
            visitor.visitLocalVariable(variable.name(), variable.isCaptured() ? Type.getDescriptor(Box.class) : Type.getDescriptor(Value.class), null, startLabel, endLabel, getOrAllocateVariable(variable));
        }
    }

    private void compileGlobalLoading(Program program, Scope scope, MethodVisitor visitor) {
        for (var variable : scope.variables()) {
            if (variable.definition() != program) continue; // Filter globals, they are defined by the root node
            if (variable.isCaptured()) {
                visitor.visitTypeInsn(NEW, Types.BOX);
                visitor.visitInsn(DUP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.BOX, "<init>", "()V", false);
                visitor.visitVarInsn(ASTORE, getOrAllocateVariable(variable));
            }
            
            compileVariableCreation(variable, () -> {
                visitor.visitVarInsn(ALOAD, GLOBALS_VAR_INDEX);
                visitor.visitLdcInsn(variable.name());
                visitor.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Map.class), "get", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class)), true);
                visitor.visitTypeInsn(CHECKCAST, Types.VALUE);
            });
        }
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
        visitor.visitVarInsn(ALOAD, 0);
        visitor.visitFieldInsn(GETFIELD, className, "context", Type.getDescriptor(EvaluationContext.class));
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
            visitor.visitVarInsn(ALOAD, index);
            valueExpressionInserter.run();
            visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOX, "setValue", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class)), false);
        } else {
            valueExpressionInserter.run();
            visitor.visitVarInsn(ASTORE, index);
        }
    }

    public void compileExpression(Expression expression) {
        expressionCompiler.compile(expression);
    }
    
    public String getLambdaName(FunctionExpression lambda) {
        return lambdaNames.get(lambda);
    }
}
