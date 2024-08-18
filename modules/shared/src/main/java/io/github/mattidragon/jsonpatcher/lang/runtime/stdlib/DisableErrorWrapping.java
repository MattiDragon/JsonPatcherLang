package io.github.mattidragon.jsonpatcher.lang.runtime.stdlib;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disables wrapping of errors thrown from library methods. 
 * Used by the debug library to clean up stacktraces when using asserts and throws.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableErrorWrapping {
}
