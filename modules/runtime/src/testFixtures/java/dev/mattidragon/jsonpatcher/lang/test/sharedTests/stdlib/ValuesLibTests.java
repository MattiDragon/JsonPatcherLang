package dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface ValuesLibTests extends SharedTest {
    @Test
    default void testGetSetPropertyOnJava() {
        TestUtils.testCode(runner(), """
                import "reflection";
                val subjectClass = reflection.findClass("%s");
                val subject = subjectClass();
                
                debug.assert(subject.testValue == true, "get regular 1");
                debug.assert(values.getProperty(subject, "testValue") == true, "get hack 1");
                values.setProperty(subject, "testValue", false);
                debug.assert(subject.testValue == false, "get regular 2");
                debug.assert(values.getProperty(subject, "testValue") == false, "get hack 2");
                
                debug.assert(subject.testMethod() == 10, "get regular 3");
                debug.assert(values.getProperty(subject, "testMethod")() == 10, "get hack 3");
                """.formatted(TestSubject.class.getName()));
    }

    @Test
    default void testGetSetPropertyOnObject() {
        TestUtils.testCode(runner(), """
                val obj = { value: 10, text: "hello" };
                
                debug.assert(obj.value == 10, "get regular 1");
                debug.assert(values.getProperty(obj, "value") == 10, "get hack 1");
                values.setProperty(obj, "value", 20);
                debug.assert(obj.value == 20, "get regular 2");
                debug.assert(values.getProperty(obj, "value") == 20, "get hack 2");
                
                debug.assert(obj.text == "hello", "get regular 3");
                debug.assert(values.getProperty(obj, "text") == "hello", "get hack 3");
                values.setProperty(obj, "text", "world");
                debug.assert(obj.text == "world", "get regular 4");
                debug.assert(values.getProperty(obj, "text") == "world", "get hack 4");
                """);
    }

    @Test
    default void testGetPropertyOnPrimitive() {
        TestUtils.testCode(runner(), """
                debug.assert(values.getProperty("hello", "toUpperCase")() == "HELLO", "string method");
                debug.assert(values.getProperty([1, 2, 3], "length") == 3, "array property");
                debug.assert(values.getProperty(debug.assert, "then") is function, "function property");
                """);
    }

    @SuppressWarnings("unused")
    class TestSubject {
        public boolean testValue = true;

        public int testMethod() {
            return 10;
        }
    }
}
