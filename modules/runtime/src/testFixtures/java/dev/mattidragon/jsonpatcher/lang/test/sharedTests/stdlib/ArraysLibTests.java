package dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface ArraysLibTests extends SharedTest {
    @Test
    default void testInsert() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                arrays.insert(arr, 1, "a");
                debug.assert(arr.insert(3, "b") is array, "insert should return the array"); # According to docs
                debug.assert(arr == [0, "a", 1, "b", 2, 3], "insert should work");
                """);
    }

    @Test
    default void testPushPop() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                arr.push(10);
                debug.assert(arr.push(20) is array, "push should return the array");
                debug.assert(arr.pop() == 20);
                debug.assert(arr.pop() == 10);
                """);
    }

    @Test
    default void testRemove() {
        TestUtils.testCode(runner(), """
                val arr = [3, 2, 1, 0, 1];
                debug.assert(arr.remove(1) is array, "remove should return the array");
                debug.assert(arr == [3, 2, 0, 1], "remove should remove the correct element");
                debug.assert(arr.removeAt(2) == 0, "removeAt should return the array");
                debug.assert(arr == [3, 2, 1], "removeAt should remove the correct element");
                """);
    }

    @Test
    default void testMap() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                debug.assert(arr.map((it) -> it * 2) == [0, 2, 4, 6], "map should work correctly");
                debug.assert(arr == [0, 1, 2, 3], "map should not mutate the array");
                """);
    }

    @Test
    default void testReplace() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                arrays.replace(arr, (it) -> it * 2);
                debug.assert(arr == [0, 2, 4, 6], "replace should work correctly");
                """);
    }

    @Test
    default void testFilter() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                val filtered = arr.filter((it) -> it % 2 == 0);
                debug.assert(filtered == [0, 2], "filter should work correctly");
                debug.assert(arr == [0, 1, 2, 3], "filter should not mutate the array");
                """);
    }

    @Test
    default void testRemoveIf() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3];
                arrays.removeIf(arr, (it) -> it % 2 == 0);
                debug.assert(arr == [1, 3], "removeIf should work correctly");
                """);
    }

    @Test
    default void testReduce() {
        TestUtils.testCode(runner(), """
                val arr = [1, 2, 3, 4];
                val sum = arr.reduce((value, acc) -> value + acc, 0);
                debug.assert(sum == 10, "reduce should work correctly");
                """);
    }

    @Test
    default void testSlice() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3, 4];
                val slice = arr.slice(1, 4);
                debug.assert(slice == [1, 2, 3], "slice should work correctly");
                """);
    }

    @Test
    default void testIndexOf() {
        TestUtils.testCode(runner(), """
                val arr = [0, 1, 2, 3, 4];
                debug.assert(arr.indexOf(2) == 2, "indexOf should return the correct index");
                debug.assert(arr.indexOf(5) == -1, "indexOf should return -1 for non-existent element");
                """);
    }
}
