package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;

public class JavaMethodValue implements Value.SpecialValue {
    private final MethodHandle nativeHandle;
    private @Nullable MethodHandle wrappedHandle;
    private @Nullable MethodHandle weaklyWrappedHandle;

    public JavaMethodValue(MethodHandle nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    @Override
    public Value invoke(EvaluationContext context, Value... args) {
        try {
            return (Value) getWrappedHandle(context).invokeWithArguments((Object[]) args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Value getProperty(String property, EvaluationContext context) {
        if (property.equals("weaklyConverted")) {
            var argCount = nativeHandle.type().parameterCount();
            return new FunctionValue(new FunctionHooks.DefinedFunction(getWeaklyWrappedHandle(context), argCount, argCount, false));
        }
        return SpecialValue.super.getProperty(property, context);
    }

    private MethodHandle getWrappedHandle(EvaluationContext context) {
        if (this.wrappedHandle == null) {
            this.wrappedHandle = JavaValueUtil.wrapMethodHandle(nativeHandle, context);
        }
        return this.wrappedHandle;
    }

    private MethodHandle getWeaklyWrappedHandle(EvaluationContext context) {
        if (this.weaklyWrappedHandle == null) {
            this.weaklyWrappedHandle = JavaValueUtil.wrapMethodHandleWeakly(nativeHandle, context);
        }
        return this.weaklyWrappedHandle;
    }
}
