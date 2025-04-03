package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

public sealed interface Type permits ArrayType, FunctionType, ObjectType, PrimitiveType, SpecialType, TypeArgument, UnionType {
}
