package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.Box;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Optional;

public class StatementCompiler implements Opcodes {
    private final TreeMetadata metadata;
    private final MethodVisitor visitor;
    private final String className;
    private final FunctionCompiler functionCompiler;
    private Label continueLabel = null;
    private Label breakLabel = null;

    public StatementCompiler(TreeMetadata metadata, MethodVisitor visitor, String className, FunctionCompiler functionCompiler) {
        this.metadata = metadata;
        this.visitor = visitor;
        this.className = className;
        this.functionCompiler = functionCompiler;
    }
    
    public void compile(Program program) {
        compileBlock(program, program.statements());
        compile(new ReturnStatement(Optional.empty()));
    }

    public void compile(Statement statement) {
        metadata.get(statement, MetadataKey.MAIN_POS).ifPresent(sourceSpan -> functionCompiler.emitLineNumber(sourceSpan.from().row()));
        switch (statement) {
            case ExpressionStatement s -> compileExpression(s);
            case ReturnStatement s -> compileReturn(s);
            case EmptyStatement s -> {}
            case BlockStatement s -> compileBlock(s, s.statements());
            case ForLoopStatement s -> compileFor(s);
            case WhileLoopStatement s -> compileWhile(s);
            case ContinueStatement s -> compileContinue();
            case BreakStatement s -> compileBreak();
            case IfStatement s -> compileIf(s);
            case VariableCreationStatement s -> compileVariableCreation(s);
            default -> throw new UnsupportedOperationException("Unsupported statement: %s".formatted(statement));
        }
    }

    private void compileExpression(ExpressionStatement statement) {
        functionCompiler.compileExpression(statement.expression());
        visitor.visitInsn(POP);
    }

    private void compileReturn(ReturnStatement statement) {
        var expression = statement.value();
        if (expression.isPresent()) {
            functionCompiler.compileExpression(expression.get());
        } else {
            visitor.visitFieldInsn(GETSTATIC, Types.NULL_VALUE, "NULL", Type.getDescriptor(Value.NullValue.class));
        }
        visitor.visitInsn(ARETURN);
    }

    private void compileBlock(ProgramNode node, List<Statement> statements) {
        var scope = metadata.get(node, VariableAnalyser.SCOPE).orElseThrow();
        
        var startLabel = new Label();
        visitor.visitLabel(startLabel);
        for (var child : statements){
            compile(child);
        }
        var endLabel = new Label();
        visitor.visitLabel(endLabel);

        for (var variable : scope.variables()) {
            var type = variable.isCaptured() ? Type.getDescriptor(Box.class) : Type.getDescriptor(Value.class);
            visitor.visitLocalVariable(variable.name(), type, null, startLabel, endLabel, functionCompiler.getOrAllocateVariable(variable));
        }
    }

    private void compileFor(ForLoopStatement statement) {
        compile(statement.initializer());
        var startLabel = new Label();
        var incrementLabel = new Label();
        var endLabel = new Label();
        continueLabel = incrementLabel;
        breakLabel = endLabel;
        
        visitor.visitLabel(startLabel);
        functionCompiler.compileExpression(statement.condition());
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "asBoolean", "()Z", true);
        visitor.visitJumpInsn(IFEQ, endLabel);
        compile(statement.body());
        visitor.visitLabel(incrementLabel);
        compile(statement.incrementer());
        visitor.visitJumpInsn(GOTO, startLabel);
        visitor.visitLabel(endLabel);
        
        continueLabel = null;
        breakLabel = null;
    }

    private void compileWhile(WhileLoopStatement statement) {
        var endLabel = new Label();
        var startLabel = new Label();
        continueLabel = startLabel;
        breakLabel = endLabel;
        
        visitor.visitLabel(startLabel);
        functionCompiler.compileExpression(statement.condition());
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "asBoolean", "()Z", true);
        visitor.visitJumpInsn(IFEQ, endLabel);
        compile(statement.body());
        visitor.visitJumpInsn(GOTO, startLabel);
        visitor.visitLabel(endLabel);
        
        continueLabel = null;
        breakLabel = null;
    }

    private void compileContinue() {
        if (continueLabel == null) {
            throw new IllegalStateException("Continue outside of loop");
        }
        visitor.visitJumpInsn(GOTO, continueLabel);
    }

    private void compileBreak() {
        if (breakLabel == null) {
            throw new IllegalStateException("Break outside of loop");
        }
        visitor.visitJumpInsn(GOTO, breakLabel);
    }

    private void compileIf(IfStatement statement) {
        var endLabel = new Label();
        var elseLabel = new Label();
        functionCompiler.compileExpression(statement.condition());
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "asBoolean", "()Z", true);
        visitor.visitJumpInsn(IFEQ, elseLabel);
        compile(statement.action());
        visitor.visitJumpInsn(GOTO, endLabel);
        visitor.visitLabel(elseLabel);
        if (statement.elseAction() != null) {
            compile(statement.elseAction());
        }
        visitor.visitLabel(endLabel);
    }

    private void compileVariableCreation(VariableCreationStatement statement) {
        functionCompiler.compileExpression(statement.initializer());
        visitor.visitVarInsn(Opcodes.ASTORE, functionCompiler.getOrAllocateVariable(metadata.get(statement, VariableAnalyser.VARIABLE_DEFINITION).orElseThrow()));
    }
}
