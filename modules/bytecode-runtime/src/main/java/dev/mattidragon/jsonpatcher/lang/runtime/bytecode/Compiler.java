package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.ValueExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;

public class Compiler {
    private static int classCounter = 0;
    
    public static void main(String[] args) throws IOException {
        var classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        var className = getClassName();
        classWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", new String[]{Type.getInternalName(GeneratedProgram.class)});
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "context", Type.getDescriptor(EvaluationContext.class), null, null);
        
        var init = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(EvaluationContext.class)), null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitFieldInsn(Opcodes.PUTFIELD, className, "context", Type.getDescriptor(EvaluationContext.class));
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        
        var mainMethod = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mainMethod.visitCode();
        
        /*ExpressionCompiler.compile(new ArrayInitializerExpression(List.of(
                new TernaryExpression(new ValueExpression(Value.BooleanValue.TRUE), new ValueExpression(new Value.NumberValue(1)), new ValueExpression(new Value.NumberValue(2))),
                new ValueExpression(new Value.NumberValue(1)),
                new ValueExpression(Value.NullValue.NULL)
        )), new TreeMetadata(), mainMethod);*/
        /*ExpressionCompiler.compile(new ObjectInitializerExpression(List.of(
                new ObjectInitializerExpression.Entry("a", null, new ValueExpression(new Value.NumberValue(1))),
                new ObjectInitializerExpression.Entry("b", null, new ValueExpression(new Value.NumberValue(2))),
                new ObjectInitializerExpression.Entry("c", null, new ValueExpression(new Value.NumberValue(3)))
        )), new TreeMetadata(), mainMethod, className);*/
        ExpressionCompiler.compile(new BinaryExpression(
                new ValueExpression(new Value.NumberValue(1)),
                new ValueExpression(new Value.NumberValue(2)),
                BinaryExpression.Operator.PLUS
        ), new TreeMetadata(), mainMethod, className);
        
        mainMethod.visitInsn(Opcodes.POP);
        mainMethod.visitInsn(Opcodes.RETURN);
        mainMethod.visitMaxs(0, 0);
        mainMethod.visitEnd();
        
        classWriter.visitEnd();

        var bytes = classWriter.toByteArray();

        CheckClassAdapter.verify(new ClassReader(bytes), false, new PrintWriter(System.err));
        
        Files.write(Path.of("run", className.substring(className.lastIndexOf('/') + 1) + ".class"), bytes);

        try {
            var definedLookup = GeneratedProgram.PACKAGE_ACCESS.defineHiddenClass(bytes, false);
            var constructor = definedLookup.findConstructor(definedLookup.lookupClass(), MethodType.methodType(void.class, EvaluationContext.class));
            var instance = (GeneratedProgram) constructor.invoke(new EvaluationContext(new LangConfig(LangConfig.StackTraceMode.JAVA)));
            instance.run();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run", e);
        }
    }
    
    private static String getClassName() {
        return "dev/mattidragon/jsonpatcher/lang/runtime/bytecode/generated/C" + classCounter++;
    }
}
