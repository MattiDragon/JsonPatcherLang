package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

public sealed interface Type permits ArrayType, FunctionType, LazyType, NamedType, ObjectType, PrimitiveType, SpecialType, TypeArgument, UnionType {
}
