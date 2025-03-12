package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.remap;

import java.lang.constant.MethodTypeDesc;
import java.util.ServiceLoader;

public interface Remapper {
    Remapper COMBINED = new CombinedRemapper();

    String remapClassToNamed(String name);

    String remapClassToRuntime(String name);
    String remapFieldToRuntime(String owner, String name, String descriptor);
    String remapMethodToRuntime(String owner, String name, String descriptor);

    default String remapFieldDescToRuntime(String desc) {
        var arrayCount = desc.lastIndexOf('[') + 1;
        var strippedDesc = desc.substring(arrayCount);

        // Primitive
        if (strippedDesc.length() == 1) return desc;
        if (!strippedDesc.startsWith("L")) throw new IllegalStateException();

        var className = strippedDesc.substring(1, strippedDesc.length() - 1);

        return "[".repeat(arrayCount) + "L" + remapClassToRuntime(className) + ";";
    }

    default String remapMethodDescToRuntime(String desc) {
        var typeDesc = MethodTypeDesc.ofDescriptor(desc);
        var remappedDesc = new StringBuilder();

        remappedDesc.append("(");
        for (var param : typeDesc.parameterList()) {
            remappedDesc.append(remapFieldDescToRuntime(param.descriptorString()));
        }
        remappedDesc.append(")");
        remappedDesc.append(remapFieldDescToRuntime(typeDesc.returnType().descriptorString()));

        return remappedDesc.toString();
    }

    default String remapFieldDescToNamed(String desc) {
        var arrayCount = desc.lastIndexOf('[') + 1;
        var strippedDesc = desc.substring(arrayCount);

        // Primitive
        if (strippedDesc.length() == 1) return desc;
        if (!strippedDesc.startsWith("L")) throw new IllegalStateException();

        var className = strippedDesc.substring(1, strippedDesc.length() - 1);

        return "[".repeat(arrayCount) + "L" + remapClassToNamed(className) + ";";
    }

    default String remapMethodDescToNamed(String desc) {
        var typeDesc = MethodTypeDesc.ofDescriptor(desc);
        var remappedDesc = new StringBuilder();

        remappedDesc.append("(");
        for (var param : typeDesc.parameterList()) {
            remappedDesc.append(remapFieldDescToNamed(param.descriptorString()));
        }
        remappedDesc.append(")");
        remappedDesc.append(remapFieldDescToNamed(typeDesc.returnType().descriptorString()));

        return remappedDesc.toString();
    }

    class CombinedRemapper implements Remapper {
        private final ServiceLoader<Remapper> loader = ServiceLoader.load(Remapper.class);

        private CombinedRemapper() {
        }

        @Override
        public String remapClassToNamed(String name) {
            var currentName = name;
            for (var remapper : loader) {
                currentName = remapper.remapClassToNamed(currentName);
            }
            return currentName;
        }

        @Override
        public String remapClassToRuntime(String name) {
            var currentName = name;
            for (var remapper : loader) {
                currentName = remapper.remapClassToRuntime(currentName);
            }
            return currentName;
        }

        @Override
        public String remapFieldToRuntime(String owner, String name, String descriptor) {
            var currentName = name;
            for (var remapper : loader) {
                currentName = remapper.remapFieldToRuntime(owner, currentName, descriptor);
            }
            return currentName;
        }

        @Override
        public String remapMethodToRuntime(String owner, String name, String descriptor) {
            var currentName = name;
            for (var remapper : loader) {
                currentName = remapper.remapMethodToRuntime(owner, currentName, descriptor);
            }
            return currentName;
        }
    }
}
