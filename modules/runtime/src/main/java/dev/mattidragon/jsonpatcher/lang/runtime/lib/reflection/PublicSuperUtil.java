package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;

class PublicSuperUtil {
    private PublicSuperUtil() {
    }

    /**
     * Finds a superclass or superinterface declaration of the passed in method, which is a public member of a public
     * class. This is necessary for unreflecting it into a method handle.
     * @param method The method to process
     * @return A public method in a public class which is overridden by the one passed in.
     * @throws IllegalStateException If no suitable superclass implementation can be found.
     */
    static Method findAccessibleSuper(Method method) {
        if (method.getDeclaringClass().accessFlags().contains(AccessFlag.PUBLIC)) {
            return method;
        }
        var queue = new ArrayDeque<Class<?>>();
        queue.add(method.getDeclaringClass());

        var parameterTypes = method.getParameterTypes();

        do {
            var clazz = queue.removeFirst();
            if (clazz.accessFlags().contains(AccessFlag.PUBLIC)) {
                try {
                    var candidate = clazz.getDeclaredMethod(method.getName(), parameterTypes);
                    if (candidate.accessFlags().contains(AccessFlag.PUBLIC)) {
                        return candidate;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            queue.addAll(Arrays.asList(clazz.getInterfaces()));
            var superClass = clazz.getSuperclass();
            if (superClass != null) {
                queue.add(superClass);
            }
        } while (!queue.isEmpty());
        throw new IllegalStateException("No public super method found for " + method);
    }
}
