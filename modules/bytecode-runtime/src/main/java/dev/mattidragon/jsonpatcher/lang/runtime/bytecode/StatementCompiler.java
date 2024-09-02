package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class StatementCompiler implements Opcodes {
    private final TreeMetadata metadata;
    private final MethodVisitor visitor;
    private final String className;
    private Label continueLabel = null;
    private Label breakLabel = null;

    private StatementCompiler(TreeMetadata metadata, MethodVisitor visitor, String className) {
        this.metadata = metadata;
        this.visitor = visitor;
        this.className = className;
    }

    public static void compile(Statement statement, TreeMetadata metadata, MethodVisitor visitor, String className) {
        new StatementCompiler(metadata, visitor, className).compile(statement);
    }

    private void compile(Statement statement) {
        switch (statement) {
            case ExpressionStatement s -> compileExpression(s);
            case ReturnStatement s -> compileReturn(s);
            case EmptyStatement s -> {}
            case BlockStatement s -> compileBlock(s);
            case WhileLoopStatement s -> compileWhile(s);
            case ContinueStatement s -> compileContinue();
            case BreakStatement s -> compileBreak();
            case IfStatement s -> compileIf(s);
            default -> throw new UnsupportedOperationException("Unsupported statement: %s".formatted(statement));
        }
    }

    private void compileExpression(ExpressionStatement statement) {
        insertExpression(statement.expression());
        visitor.visitInsn(POP);
    }

    private void compileReturn(ReturnStatement statement) {
        var expression = statement.value();
        if (expression.isPresent()) {
            insertExpression(expression.get());
        } else {
            visitor.visitFieldInsn(GETSTATIC, Types.NULL_VALUE, "NULL", Type.getDescriptor(Value.NullValue.class));
        }
        visitor.visitInsn(ARETURN);
    }

    private void compileBlock(BlockStatement statement) {
        for (var child : statement.statements()){
            compile(child);
        }
    }

    private void compileWhile(WhileLoopStatement statement) {
        var endLabel = new Label();
        var startLabel = new Label();
        continueLabel = startLabel;
        breakLabel = endLabel;
        
        visitor.visitLabel(startLabel);
        insertExpression(statement.condition());
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
        insertExpression(statement.condition());
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

    private void insertExpression(Expression expression) {
        ExpressionCompiler.compile(expression, metadata, visitor, className);
    }
}
