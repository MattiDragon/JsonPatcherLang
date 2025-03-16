package dev.mattidragon.jsonpatcher.docs.newdocs.parse;

import dev.mattidragon.jsonpatcher.docs.newdocs.type.*;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.*;

public class TypeParser {
    private final TreeMetadata metadata;
    private final Tokenizer tokens;
    private final DiagnosticsBuilder diagnostics;
    private final Map<String, FunctionDocType.TypeArgument> typeArguments = new HashMap<>();

    private TypeParser(TreeMetadata metadata, Tokenizer tokens, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    public static NewDocType parse(Tokenizer tokens, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var typeParser = new TypeParser(metadata, tokens, diagnostics);
        return typeParser.root(typeParser.tokens.next());
    }

    private NewDocType root(DocToken token) {
        var type = switch (token) {
            case DocToken.Symbol.BEGIN_PAREN -> parseFunction(List.of());
            case DocToken.Symbol.BEGIN_ANGLE -> parseGenericFunction();
            case DocToken.Symbol.BEGIN_CURLY -> parseParens();
            case DocToken.Name(String value) -> parseName(value);
            case DocToken.VarName(String value) -> parseVar(value);
            default -> {
                var error = "Unexpected token at start of type: " + token;
                var errorPos = tokens.lastPos();

                diagnostics.addDiagnostic(new DocParseError(errorPos, error, DocParseError.Type.TYPE_PARSE));

                var errorType = new ErrorDocType(error);
                metadata.put(errorType, MetadataKey.FULL_POS, errorPos);
                yield errorType;
            }
        };

        loop:
        while (tokens.hasNext()) {
            switch (tokens.peek()) {
                case DocToken.Symbol.BEGIN_SQUARE -> {
                    tokens.next();
                    var beginPos = tokens.lastPos();
                    var prevPos = metadata.get(type, MetadataKey.FULL_POS).orElseThrow();

                    if (tokens.next() != DocToken.Symbol.END_SQUARE) {
                        addDiagnostic(tokens.lastPos(), "Expected ']");
                    }

                    type = new ArrayDocType(type);
                    metadata.put(type, MetadataKey.FULL_POS, SourceSpan.between(prevPos, tokens.lastPos()));
                    metadata.put(type, MetadataKey.KEYWORD_POS, SourceSpan.between(beginPos, tokens.lastPos()));
                }
                case DocToken.Symbol.BEGIN_CURLY -> {
                    tokens.next();
                    var beginPos = tokens.lastPos();
                    var prevPos = metadata.get(type, MetadataKey.FULL_POS).orElseThrow();

                    if (tokens.next() != DocToken.Symbol.END_CURLY) {
                        addDiagnostic(tokens.lastPos(), "Expected '}");
                    }

                    type = new MapDocType(type);
                    metadata.put(type, MetadataKey.FULL_POS, SourceSpan.between(prevPos, tokens.lastPos()));
                    metadata.put(type, MetadataKey.KEYWORD_POS, SourceSpan.between(beginPos, tokens.lastPos()));
                }
                default -> {
                    break loop;
                }
            }
        }

        while (tokens.hasNext() && tokens.peek() == DocToken.Symbol.BAR) {
            tokens.next();
            var barPos = tokens.lastPos();
            var prevPos = metadata.get(type, MetadataKey.FULL_POS).orElseThrow();
            type = new UnionDocType(type, root(tokens.next()));
            metadata.put(type, MetadataKey.FULL_POS, SourceSpan.between(prevPos, tokens.lastPos()));
            metadata.put(type, MetadataKey.KEYWORD_POS, barPos);
        }

        return type;
    }

    private NewDocType parseVar(String name) {
        if (typeArguments.containsKey(name)) {
            return new TypeArgumentDocType(typeArguments.get(name));
        }
        var type = new ErrorDocType("Unknown type argument: " + name);
        metadata.put(type, MetadataKey.FULL_POS, tokens.lastPos());
        return type;
    }

    private NewDocType parseName(String value) {
        var type = new ReferenceDocType(value);
        metadata.put(type, MetadataKey.FULL_POS, tokens.lastPos());
        return type;
    }

    private NewDocType parseGenericFunction() {
        var startPos = tokens.lastPos();

        var typeArguments = new ArrayList<FunctionDocType.TypeArgument>();
        while (tokens.peek() != DocToken.Symbol.END_ANGLE) {
            var nameToken = tokens.next();
            String name;
            if (nameToken instanceof DocToken.VarName(var value)) {
                name = value;
            } else {
                name = "???";
                addDiagnostic(tokens.lastPos(), "Invalid name for type argument: " + nameToken);
            }
            var namePos = tokens.lastPos();

            NewDocType type = null;
            if (tokens.peek() == DocToken.Symbol.COLON) {
                tokens.next();
                type = root(tokens.next());
            }
            var typeArgument = new FunctionDocType.TypeArgument(name, Optional.ofNullable(type));
            metadata.put(typeArgument, MetadataKey.NAME_POS, namePos);
            metadata.put(typeArgument, MetadataKey.FULL_POS, SourceSpan.between(namePos, tokens.lastPos()));
            typeArguments.add(typeArgument);

            switch (tokens.peek()) {
                case DocToken.Symbol.END_ANGLE -> {}
                case DocToken.Symbol.COMMA -> tokens.next();
                default -> addDiagnostic(tokens.nextPos(), "Invalid name for type argument: " + nameToken);
            }
        }
        tokens.next(); // Eat closing angle bracket

        if (tokens.peek() == DocToken.Symbol.BEGIN_PAREN) {
            tokens.next();
        } else {
            addDiagnostic(tokens.lastPos(), "Expected '(', but got " + tokens.peek());
        }

        var erroredGenerics = new ArrayList<String>();
        for (var generic : typeArguments) {
            if (this.typeArguments.containsKey(generic.name())) {
                addDiagnostic(metadata.get(generic, MetadataKey.NAME_POS).orElseThrow(), "Type argument shadowing not allowed: " + generic.name());
                erroredGenerics.add(generic.name());
            } else {
                this.typeArguments.put(generic.name(), generic);
            }
        }

        var type = parseFunction(typeArguments);

        for (var generic : typeArguments) {
            if (!erroredGenerics.contains(generic.name())) {
                this.typeArguments.remove(generic.name());
            }
        }

        var endPos = tokens.lastPos();
        metadata.put(type, MetadataKey.FULL_POS, SourceSpan.between(startPos, endPos));
        return type;
    }

    private NewDocType parseFunction(List<FunctionDocType.TypeArgument> typeArguments) {
        var startPos = tokens.lastPos();

        var arguments = new ArrayList<FunctionDocType.Argument>();
        while (tokens.peek() != DocToken.Symbol.END_PAREN) {
            var token = tokens.next();
            var argStartPos = tokens.lastPos();

            SourceSpan argNamePos = null;
            String argName = null;
            if (token instanceof DocToken.Name(var value) && tokens.peek() == DocToken.Symbol.COLON) {
                argName = value;
                argNamePos = tokens.lastPos();
                tokens.next(); // Eat colon
                token = tokens.next(); // Get next token for type parsing
            }

            var type = root(token);

            FunctionDocType.Argument.Kind kind;
            switch (tokens.peek()) {
                case DocToken.Symbol.STAR -> {
                    tokens.next();
                    kind = FunctionDocType.Argument.Kind.VARARGS;
                }
                case DocToken.Symbol.QUESTION_MARK -> {
                    tokens.next();
                    kind = FunctionDocType.Argument.Kind.OPTIONAL;
                }
                default -> kind = FunctionDocType.Argument.Kind.REGULAR;
            }

            var arg = new FunctionDocType.Argument(type, Optional.ofNullable(argName), kind);
            if (argNamePos != null) {
                metadata.put(arg, MetadataKey.NAME_POS, argNamePos);
            }
            metadata.put(arg, MetadataKey.FULL_POS, SourceSpan.between(argStartPos, tokens.lastPos()));
            arguments.add(arg);

            switch (tokens.peek()) {
                case DocToken.Symbol.END_PAREN -> {}
                case DocToken.Symbol.COMMA -> tokens.next();
                default -> addDiagnostic(tokens.nextPos(), "Expected comma or closing parenthesis after argument");
            }
        }

        tokens.next(); // Eat closing parenthesis
        if (tokens.peek() == DocToken.Symbol.ARROW) {
            tokens.next();
        } else {
            addDiagnostic(tokens.lastPos(), "Expected '->' after function arguments");
        }

        var type = new FunctionDocType(typeArguments, arguments, root(tokens.next()));
        metadata.put(type, MetadataKey.FULL_POS, SourceSpan.between(startPos, tokens.lastPos()));
        return type;
    }

    private void addDiagnostic(SourceSpan pos, String message) {
        diagnostics.addDiagnostic(new DocParseError(pos, message, DocParseError.Type.TYPE_PARSE));
    }

    private NewDocType parseParens() {
        var type = root(tokens.next());
        if (tokens.peek() == DocToken.Symbol.END_CURLY) {
            tokens.next();
        } else {
            addDiagnostic(tokens.lastPos(), "Expected closing curly brace");
        }
        return type;
    }

}
