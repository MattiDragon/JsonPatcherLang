package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import org.objectweb.asm.ClassWriter;

/**
 * A subclass of ClassWriter exclusively so that subclasses are resolved on the correct classloader.
 * Because minecraft includes asm, it is loaded on the default java classloader.
 * Our code will end up on the knot classloader because it's inside a mod.
 * Unless we make asm use the knot classloader, it will fail to resolve our classes, which is necessary when computing frames.
 */
class CustomClassWriter extends ClassWriter {
    public CustomClassWriter(int flags) {
        super(flags);
    }
}
