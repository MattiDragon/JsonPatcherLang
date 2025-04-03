package dev.mattidragon.jsonpatcher.lang.analysis.test.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeComparison;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeComparisonTests {
    @Test
    public void testBasicSubtyping() {
        var arrayType = new ArrayType(PrimitiveType.NUMBER);
        var objectType = new ObjectType(PrimitiveType.NUMBER);
        var functionType = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER, PrimitiveType.BOOLEAN), 2, false, PrimitiveType.NULL);

        assertTrue(TypeComparison.isSubtype(SpecialType.ANY, SpecialType.ANY));
        assertTrue(TypeComparison.isSubtype(SpecialType.NEVER, SpecialType.ANY));
        assertFalse(TypeComparison.isSubtype(SpecialType.ANY, SpecialType.NEVER));
        assertTrue(TypeComparison.isSubtype(SpecialType.UNKNOWN, SpecialType.NEVER));
        assertTrue(TypeComparison.isSubtype(arrayType, PrimitiveType.ARRAY));
        assertTrue(TypeComparison.isSubtype(functionType, PrimitiveType.FUNCTION));
        assertTrue(TypeComparison.isSubtype(objectType, PrimitiveType.OBJECT));
    }

    @Test
    public void testArgCountSubtyping() {
        var functionType1 = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER, PrimitiveType.BOOLEAN), 1, false, PrimitiveType.NULL);
        var functionType2 = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER), 1, false, PrimitiveType.NULL);

        assertTrue(TypeComparison.isSubtype(functionType1, functionType2));
        assertFalse(TypeComparison.isSubtype(functionType2, functionType1));
    }

    @Test
    public void testGenericFunctionSubtyping() {
        var typeArg1 = new TypeArgument("1", SpecialType.ANY);
        var functionType1 = new FunctionType(List.of(typeArg1), List.of(typeArg1, new ArrayType(typeArg1)), 2, false, typeArg1);

        var typeArg2 = new TypeArgument("2", SpecialType.ANY);
        var functionType2 = new FunctionType(List.of(typeArg2), List.of(typeArg2, new ArrayType(typeArg2)), 2, false, typeArg2);

        assertTrue(TypeComparison.isSubtype(functionType1, functionType2));
    }
}
