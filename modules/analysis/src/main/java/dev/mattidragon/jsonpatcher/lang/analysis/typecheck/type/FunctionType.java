package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

import java.util.List;

public record FunctionType(List<TypeArgument> typeArguments, List<Type> args, int requiredArgs, boolean varargs, Type returnType) implements Type {
}
