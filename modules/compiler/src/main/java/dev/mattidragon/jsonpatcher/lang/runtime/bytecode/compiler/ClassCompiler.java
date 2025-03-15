package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ClassCompiler {
    private final Map<FunctionExpression, String> lambdaNames;
    private final ClassWriter classWriter;
    private final String scriptName;
    private final String className;
    private final Program program;
    private final TreeMetadata metadata;
    private final CompilerOptions options;

    public ClassCompiler(Program program, TreeMetadata metadata, Map<FunctionExpression, String> lambdaNames, String scriptName, String className, CompilerOptions options) {
        classWriter = new CustomClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.scriptName = scriptName;
        this.className = className;
        this.program = program;
        this.metadata = metadata;
        this.options = options;

        lambdaNames = new HashMap<>(lambdaNames);
        var usedLambdaNames = new HashSet<String>();
        
        for (var entry : lambdaNames.entrySet()) {
            var count = 0;
            String lambdaName;
            do {
                if (count++ == 0) {
                    lambdaName = entry.getValue();
                } else {
                    lambdaName = entry.getValue() + "$" + (count - 1);
                }
            } while (usedLambdaNames.contains(lambdaName));
            
            usedLambdaNames.add(lambdaName);
            entry.setValue(lambdaName);
        }
        this.lambdaNames = lambdaNames;
    }

    public void compileStart() {
        classWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", new String[]{Types.GENERATED_PROGRAM.getInternalName()});
        classWriter.visitSource(scriptName, null);
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "context", Types.EVALUATION_CONTEXT.getDescriptor(), null, null);
        addConstructor();
    }

    public void compileMain() {
        FunctionCompiler.compileMainMethod(metadata, classWriter, className, program, lambdaNames, options);
    }

    public void compileLambda(FunctionExpression expression) {
        var name = lambdaNames.get(expression);
        try {
            FunctionCompiler.compileLambda(metadata, classWriter, className, expression, name, lambdaNames, options);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error while compiling function '" + name + "'", e);
        }
    }

    public byte[] getBytes() {
        return classWriter.toByteArray();
    }
    
    private void addConstructor() {
        var visitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Types.EVALUATION_CONTEXT), null, null);
        visitor.visitCode();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitVarInsn(Opcodes.ALOAD, 1);
        visitor.visitFieldInsn(Opcodes.PUTFIELD, className, "context", Types.EVALUATION_CONTEXT.getDescriptor());
        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitParameter("context", 0);
        visitor.visitEnd();
    }
}
