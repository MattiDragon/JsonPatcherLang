package dev.mattidragon.jsonpatcher.lang.runtime.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;
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
    public Value invoke(PlatformContext context, Value... args) {
        try {
            return (Value) getWrappedHandle().invokeWithArguments((Object[]) args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Value getProperty(String property, PlatformContext context) {
        if (property.equals("weaklyConverted")) {
            var argCount = nativeHandle.type().parameterCount();
            return new FunctionValue(new FunctionHooks.DefinedFunction(getWeaklyWrappedHandle(), argCount, argCount, false));
        }
        return SpecialValue.super.getProperty(property, context);
    }

    private MethodHandle getWrappedHandle() {
        if (this.wrappedHandle == null) {
            this.wrappedHandle = JavaValueUtil.wrapMethodHandle(nativeHandle);
        }
        return this.wrappedHandle;
    }

    private MethodHandle getWeaklyWrappedHandle() {
        if (this.weaklyWrappedHandle == null) {
            this.weaklyWrappedHandle = JavaValueUtil.wrapMethodHandleWeakly(nativeHandle);
        }
        return this.weaklyWrappedHandle;
    }
}
