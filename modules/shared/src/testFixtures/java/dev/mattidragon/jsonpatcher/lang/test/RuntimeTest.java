package dev.mattidragon.jsonpatcher.lang.test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest
@FieldSource("dev.mattidragon.jsonpatcher.lang.test.TestUtils#TEST_RUNTIMES")
public @interface RuntimeTest {
}
