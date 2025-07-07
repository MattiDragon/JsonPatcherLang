package dev.mattidragon.jsonpatcher.toolcommon.typing;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.tag.builtin.HardcodedTypeTagProcessor;
import dev.mattidragon.jsonpatcher.docs.tree.DocTree;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeNamespace;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeObject;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeProperty;
import dev.mattidragon.jsonpatcher.docs.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class DocTypeConverter {
    private final Map<String, LazyType> namedTypes = new HashMap<>();
    private final Map<String, NamedType> libraries = new HashMap<>();
    private final Map<String, Type> globals = new HashMap<>();

    public void loadTree(DocTree tree) {
        for (var namespace : tree.namespaces().values()) {
            for (var object : namespace.objects().values()) {
                var fullName = joinName(namespace, object);

                switch (object.entry()) {
                    case DocEntry.GlobalValueEntry globalEntry ->
                            globals.put(globalEntry.name(), convert(globalEntry.type()));
                    case DocEntry.GlobalLibraryEntry globalEntry -> {
                        var type = buildNamedType(fullName, PrimitiveType.OBJECT, object.properties().values());
                        getOrComputeType(fullName).set(type);
                        globals.put(globalEntry.name(), type);
                    }
                    case DocEntry.LibraryEntry libraryEntry -> {
                        var type = buildNamedType(fullName, PrimitiveType.OBJECT, object.properties().values());
                        getOrComputeType(fullName).set(type);
                        libraries.put(libraryEntry.location().orElse(libraryEntry.name()), type);
                    }
                    case DocEntry.MetadataEntry metadataEntry -> {
                        // Metadata will get type checking later
                    }
                    case DocEntry.NamespaceEntry namespaceEntry ->
                            throw new IllegalStateException("Namespaces are not doc objects");
                    case DocEntry.PropertyEntry propertyEntry ->
                            throw new IllegalStateException("Properties are not doc objects");
                    case DocEntry.TypeAliasEntry typeAliasEntry ->
                            getOrComputeType(fullName).set(convert(typeAliasEntry.definition()));

                    case DocEntry.TypeDeclarationEntry typeDeclarationEntry -> {
                        var superType = switch (typeDeclarationEntry.baseType()) {
                            case OBJECT -> PrimitiveType.OBJECT;
                            case SPECIAL -> PrimitiveType.SPECIAL;
                        };
                        getOrComputeType(fullName).set(buildNamedType(fullName, superType, object.properties().values()));
                    }
                    case null -> {}
                }
            }
        }
    }

    public Collection<String> getGlobalNames() {
        return globals.keySet();
    }

    public Optional<Type> getLibraryType(String location) {
        return Optional.ofNullable(libraries.get(location));
    }

    public Optional<Type> getGlobalType(String name) {
        return Optional.ofNullable(globals.get(name));
    }

    public Optional<Type> getNamedType(String name) {
        return Optional.ofNullable(namedTypes.get(name)).map(LazyType::get);
    }

    private NamedType buildNamedType(String name, PrimitiveType superType, Collection<DocTreeProperty> docProperties) {
        Type wildcardType = null;
        var properties = new HashMap<String, Type>();
        for (var property : docProperties) {
            var metadata = property.metadata();
            var propertyName = property.entry().name();
            var propertyType = getPropertyType(property.entry(), metadata);

            if (propertyName.equals("*")) {
                wildcardType = propertyType;
                continue;
            }
            properties.put(propertyName, propertyType);
        }

        // TODO: add option to declare call signature and use that
        return new NamedType(superType, properties, Optional.ofNullable(wildcardType), Optional.empty(), name);
    }

    public Type getPropertyType(DocEntry.PropertyEntry entry, @Nullable TreeMetadata metadata) {
        var specialTypeKind = metadata == null
                ? Optional.<HardcodedTypeTagProcessor.Kind>empty()
                : metadata.get(entry, HardcodedTypeTagProcessor.KIND);

        var propertyType = convert(entry.type());

        if (specialTypeKind.isPresent()) {
            switch (specialTypeKind.get()) {
                case FUNCTION_BIND -> propertyType = new HardcodedType(propertyType, HardcodedType.Kind.FUNCTION_BIND);
                case FUNCTION_CHAIN -> propertyType = new HardcodedType(propertyType, HardcodedType.Kind.FUNCTION_CHAIN);
            }
        }
        return propertyType;
    }

    public Type convert(DocType docType) {
        return convert(docType, new HashMap<>());
    }

    private Type convert(DocType docType, Map<FunctionDocType.TypeArgument, TypeArgument> typeArgMapping) {
        return switch (docType) {
            case ArrayDocType(var element) -> new ArrayType(convert(element, typeArgMapping));
            case MapDocType(var element) -> new ObjectType(convert(element, typeArgMapping));
            case FunctionDocType functionDocType -> {
                var convertedTypeArgs = new ArrayList<TypeArgument>();
                for (var docArgument : functionDocType.typeArguments()) {
                    var bound = docArgument.bound().map(it -> convert(it, typeArgMapping)).orElse(SpecialType.ANY);
                    var typeArgument = new TypeArgument(docArgument.name(), bound);
                    convertedTypeArgs.add(typeArgument);
                    typeArgMapping.put(docArgument, typeArgument);
                }

                var argTypes = functionDocType.argTypes();
                var convertedArgs = argTypes.stream()
                        .map(FunctionDocType.Argument::type)
                        .map(it -> convert(it, typeArgMapping))
                        .toList();
                var requiredArgs = argTypes.stream()
                        .takeWhile(argument -> argument.kind() == FunctionDocType.Argument.Kind.REGULAR)
                        .count();
                var varargs = !argTypes.isEmpty() && argTypes.getFirst().kind() == FunctionDocType.Argument.Kind.VARARGS;

                var returnType = convert(functionDocType.returnType(), typeArgMapping);

                yield new FunctionType(convertedTypeArgs, convertedArgs, (int) requiredArgs, varargs, returnType);
            }
            case ReferenceDocType(var name) -> switch (name) {
                case "number" -> PrimitiveType.NUMBER;
                case "string" -> PrimitiveType.STRING;
                case "boolean" -> PrimitiveType.BOOLEAN;
                case "null" -> PrimitiveType.NULL;
                case "object" -> PrimitiveType.OBJECT;
                case "array" -> PrimitiveType.ARRAY;
                case "function" -> PrimitiveType.FUNCTION;
                case "special" -> PrimitiveType.SPECIAL;
                case "any" -> SpecialType.ANY;
                case "never" -> SpecialType.NEVER;
                case "unknown" -> SpecialType.UNKNOWN;
                default -> getOrComputeType(name);
            };
            case TypeArgumentDocType(var owner) ->
                    Objects.requireNonNull(typeArgMapping.get(owner), "Unexpected type argument usage");
            case UnionDocType(var first, var second) -> UnionType.union(convert(first, typeArgMapping), convert(second, typeArgMapping));
            case ErrorDocType errorDocType -> SpecialType.UNKNOWN;
        };
    }

    private static String joinName(DocTreeNamespace namespace, DocTreeObject object) {
        return String.join(".", namespace.description().withLast(object.name()).parts());
    }

    private LazyType getOrComputeType(String fullName) {
        return namedTypes.computeIfAbsent(fullName, k -> new LazyType());
    }
}

