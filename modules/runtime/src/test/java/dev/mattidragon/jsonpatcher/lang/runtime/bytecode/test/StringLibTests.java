package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class StringLibTests {
    private final TestRunner runner = new BytecodeTestRunner(CompilerOptions.DEFAULT);

    @Test
    public void testMatches() {
        TestUtils.testCode(runner, """
                debug.assert(strings.matches("hello", "h.*o"));
                debug.assert(!strings.matches("hello", "H.*o"));
                debug.assert(strings.matches("12345", "\\\\d+"));
                """);
    }

    @Test
    public void testReplace() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.replace("hello world", "world", "there"), "hello there");
                debug.assertEquals(strings.replace("hello world", "o", "a"), "hella warld");
                debug.assertEquals(strings.replace("hello world", "x", "y"), "hello world");
                """);
    }

    @Test
    public void testReplaceRegex() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.replaceRegex("hello world", "o", "a"), "hella warld");
                debug.assertEquals(strings.replaceRegex("hello world", "\\\\w+", "word"), "word word");
                debug.assertEquals(strings.replaceRegex("123-456-7890", "\\\\d", "x"), "xxx-xxx-xxxx");
                """);
    }

    @Test
    public void testSplit() {
        TestUtils.testCode(runner, """
                var result = strings.split("a,b,c", ",");
                debug.assert(result is array);
                debug.assertEquals(result.length, 3);
                debug.assertEquals(result[0], "a");
                debug.assertEquals(result[1], "b");
                debug.assertEquals(result[2], "c");
                """);
    }

    @Test
    public void testToLowerCase() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.toLowerCase("HELLO"), "hello");
                debug.assertEquals(strings.toLowerCase("Hello World"), "hello world");
                """);
    }

    @Test
    public void testToUpperCase() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.toUpperCase("hello"), "HELLO");
                debug.assertEquals(strings.toUpperCase("Hello World"), "HELLO WORLD");
                """);
    }

    @Test
    public void testTrim() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.trim("  hello  "), "hello");
                debug.assertEquals(strings.trim("hello"), "hello");
                """);
    }

    @Test
    public void testTrimStart() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.trimStart("  hello  "), "hello  ");
                debug.assertEquals(strings.trimStart("hello"), "hello");
                """);
    }

    @Test
    public void testTrimEnd() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.trimEnd("  hello  "), "  hello");
                debug.assertEquals(strings.trimEnd("hello"), "hello");
                """);
    }

    @Test
    public void testStartsWith() {
        TestUtils.testCode(runner, """
                debug.assert(strings.startsWith("hello", "he"));
                debug.assert(!strings.startsWith("hello", "He"));
                """);
    }

    @Test
    public void testEndsWith() {
        TestUtils.testCode(runner, """
                debug.assert(strings.endsWith("hello", "lo"));
                debug.assert(!strings.endsWith("hello", "Lo"));
                """);
    }

    @Test
    public void testContains() {
        TestUtils.testCode(runner, """
                debug.assert(strings.contains("hello", "ell"));
                debug.assert(!strings.contains("hello", "xyz"));
                """);
    }

    @Test
    public void testLength() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.length("hello"), 5);
                debug.assertEquals(strings.length(""), 0);
                """);
    }

    @Test
    public void testIsEmpty() {
        TestUtils.testCode(runner, """
                debug.assert(strings.isEmpty(""));
                debug.assert(!strings.isEmpty("hello"));
                """);
    }

    @Test
    public void testIsBlank() {
        TestUtils.testCode(runner, """
                debug.assert(strings.isBlank(""));
                debug.assert(strings.isBlank("   "));
                debug.assert(!strings.isBlank("hello"));
                """);
    }

    @Test
    public void testCharAt() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.charAt("hello", 1), "e");
                debug.assertEquals(strings.charAt("hello", 4), "o");
                """);
    }

    @Test
    public void testChars() {
        TestUtils.testCode(runner, """
                var result = strings.chars("hello");
                debug.assert(result is array);
                debug.assertEquals(result.length, 5);
                debug.assertEquals(result[0], "h");
                debug.assertEquals(result[1], "e");
                debug.assertEquals(result[2], "l");
                debug.assertEquals(result[3], "l");
                debug.assertEquals(result[4], "o");
                """);
    }

    @Test
    public void testSubstring() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.substring("hello", 1, 4), "ell");
                debug.assertEquals(strings.substring("hello", 2), "llo");
                """);
    }

    @Test
    public void testAsString() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.asString(123), "123.0");
                debug.assertEquals(strings.asString(true), "true");
                debug.assertEquals(strings.asString(null), "null");
                debug.assertEquals(strings.asString([1, 2, 3]), "[1.0, 2.0, 3.0]");
                debug.assertEquals(strings.asString({a: 1, b: 2}), "{a: 1.0, b: 2.0}");
                """);
    }

    @Test
    public void testJoin() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.join(["a", "b", "c"], ","), "a,b,c");
                debug.assertEquals(strings.join([1, 2, 3], "-"), "1.0-2.0-3.0");
                """);
    }

    @Test
    public void testMatchesCornerCases() {
        TestUtils.testCode(runner, """
                debug.assert(strings.matches("", ""));
                debug.assert(!strings.matches("hello", ""));
                debug.assert(!strings.matches("", "h.*o"));
                """);
    }

    @Test
    public void testReplaceCornerCases() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.replace("hello world", "world", ""), "hello ");
                debug.assertEquals(strings.replace("", "world", "there"), "");
                """);
    }

    @Test
    public void testReplaceRegexCornerCases() {
        TestUtils.testCode(runner, """
                debug.assertEquals(strings.replaceRegex("hello world", "o", ""), "hell wrld");
                debug.assertEquals(strings.replaceRegex("", "\\\\w+", "word"), "");
                """);
    }

    @Test
    public void testSplitCornerCases() {
        TestUtils.testCode(runner, """
                var result1 = strings.split("", ",");
                debug.assert(result1 is array);
                debug.assertEquals(result1.length, 1);
                debug.assertEquals(result1[0], "");

                var result2 = strings.split("abc", ",");
                debug.assert(result2 is array);
                debug.assertEquals(result2.length, 1);
                debug.assertEquals(result2[0], "abc");
                """);
    }

    @Test
    public void testCharAtCornerCases() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            TestUtils.runCode(runner, """
                    strings.charAt("hello", -1);
                    """);
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            TestUtils.runCode(runner, """
                    strings.charAt("hello", 5);
                    """);
        });
    }

    @Test
    public void testSubstringCornerCases() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            TestUtils.runCode(runner, """
                    strings.substring("hello", -1, 4);
                    """);
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            TestUtils.runCode(runner, """
                    strings.substring("hello", 1, 6);
                    """);
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            TestUtils.runCode(runner, """
                    strings.substring("hello", 4, 1);
                    """);
        });
    }
}