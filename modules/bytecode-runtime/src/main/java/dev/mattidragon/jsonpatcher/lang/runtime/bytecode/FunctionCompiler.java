package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.RootVariable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
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
    private final TreeMetadata metadata;
    private int varCounter = 1;
    private int rootNameCounter = 1;
    private int currentLine = 0;
    private final Map<Variable, Integer> variableAllocations = new HashMap<>();
    private final Map<RootVariable, Integer> rootAllocations = new HashMap<>();
    
    public FunctionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className) {
        statementCompiler = new StatementCompiler(metadata, visitor, className, this);
        expressionCompiler = new ExpressionCompiler(metadata, visitor, className, this);
        this.visitor = visitor;
        this.metadata = metadata;
    }
    
    public String allocateRootName() {
        return "$" + rootNameCounter++;
    }
    
    public int allocateParameter() {
        return currentLine++;
    }
    
    public int getOrAllocateVariable(Variable variable) {
        return variableAllocations.computeIfAbsent(variable, var1 -> varCounter++);
    }
    
    public int getOrAllocateRoot(RootVariable root) {
        return rootAllocations.computeIfAbsent(root, var1 -> varCounter++);
    }
    
    public void emitLineNumber(int line) {
        if (line == currentLine) return;
        currentLine = line;
        var label = new Label();
        visitor.visitLabel(label);
        visitor.visitLineNumber(line, label);
    }
    
    public void compileProgram(Program program) {
        var scope = metadata.get(program, VariableAnalyser.SCOPE).orElseThrow();
        rootAllocations.put(scope.root(), allocateParameter());
        statementCompiler.compile(program);
    }
    
    public void compileExpression(Expression expression) {
        expressionCompiler.compile(expression);
    }
}
