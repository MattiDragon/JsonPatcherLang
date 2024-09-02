package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ReturnStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.StatementCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
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
import java.util.Optional;

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
        
        var mainMethod = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "run", Type.getMethodDescriptor(Type.getType(Value.class)), null, null);
        mainMethod.visitCode();
        
        var code = """
                for (1; 2; 3) {
                    if (5) {
                        continue;
                    }
                    if (4) {
                        break;
                    }
                    6;
                }
                """;
        var result = TestUtils.parseFull(code);

        for (var statement : result.program().statements()) {
            StatementCompiler.compile(statement, result.treeMetadata(), mainMethod, className);
        }
        StatementCompiler.compile(new ReturnStatement(Optional.empty()), result.treeMetadata(), mainMethod, className);

        mainMethod.visitMaxs(0, 0);
        mainMethod.visitEnd();
        
        classWriter.visitEnd();

        var bytes = classWriter.toByteArray();

        CheckClassAdapter.verify(new ClassReader(bytes), false, new PrintWriter(System.err));
        
        Files.write(Path.of("run", className.substring(className.lastIndexOf('/') + 1) + ".class"), bytes);

//        try {
//            var definedLookup = GeneratedProgram.PACKAGE_ACCESS.defineHiddenClass(bytes, false);
//            var constructor = definedLookup.findConstructor(definedLookup.lookupClass(), MethodType.methodType(void.class, EvaluationContext.class));
//            var instance = (GeneratedProgram) constructor.invoke(new EvaluationContext(new LangConfig(LangConfig.StackTraceMode.JAVA)));
//            instance.run();
//        } catch (Throwable e) {
//            throw new RuntimeException("Failed to run", e);
//        }
    }
    
    private static String getClassName() {
        return "dev/mattidragon/jsonpatcher/lang/runtime/bytecode/generated/C" + classCounter++;
    }
}
