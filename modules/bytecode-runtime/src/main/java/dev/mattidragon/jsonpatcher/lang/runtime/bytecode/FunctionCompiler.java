package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.HashMap;
import java.util.Map;

public class FunctionCompiler {
    private final StatementCompiler statementCompiler;
    private final ExpressionCompiler expressionCompiler;
    private final MethodVisitor visitor;
    private int varCounter = 1;
    private int currentLine = 0;
    private final Map<Variable, Integer> variableAllocations = new HashMap<>();
    
    public FunctionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className) {
        statementCompiler = new StatementCompiler(metadata, visitor, className, this);
        expressionCompiler = new ExpressionCompiler(metadata, visitor, className, this);
        this.visitor = visitor;
    }
    
    public int getOrAllocateVariable(Variable variable) {
        return variableAllocations.computeIfAbsent(variable, var1 -> varCounter++);
    }
    
    public void emitLineNumber(int line) {
        if (line == currentLine) return;
        currentLine = line;
        var label = new Label();
        visitor.visitLabel(label);
        visitor.visitLineNumber(line, label);
    }
    
    public void compileProgram(Program program) {
        statementCompiler.compile(program);
    }
    
    public void compileStatement(Statement statement) {
        statementCompiler.compile(statement);
    }
    
    public void compileExpression(Expression expression) {
        expressionCompiler.compile(expression);
    }
}
