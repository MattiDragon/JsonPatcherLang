package dev.mattidragon.jsonpatcher.docs.newdocs.parse;

import dev.mattidragon.jsonpatcher.docs.newdocs.type.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OldTypeParser {
    private final TreeMetadata metadata;
    private final Tokenizer tokens;
    private final DiagnosticsBuilder diagnostics;

    private OldTypeParser(TreeMetadata metadata, Tokenizer tokens, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    public static NewDocType parse(Tokenizer tokens, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var typeParser = new OldTypeParser(metadata, tokens, diagnostics);
        // TODO: handle EOF here
        return typeParser.root(typeParser.tokens.next());
    }

    private NewDocType root(DocToken token) {
        var type = switch (token) {
            case DocToken.Symbol.BEGIN_PAREN -> function();
            case DocToken.Symbol.BEGIN_SQUARE -> array();
            case DocToken.Symbol.BEGIN_CURLY -> object();
            case DocToken.Name(var name) -> new ReferenceDocType(name);
            default -> {
                var error = "Unexpected token at start of type: " + token;
                var errorPos = tokens.lastPos();

                diagnostics.addDiagnostic(new DocParseError(errorPos, error, DocParseError.Type.TYPE_PARSE));

                var errorType = new ErrorDocType(error);
                metadata.put(errorType, MetadataKey.FULL_POS, errorPos);
                yield errorType;
            }
        };

        while (tokens.hasNext() && tokens.peek() == DocToken.Symbol.BAR) {
            tokens.next();
            type = new UnionDocType(type, root(tokens.next()));
        }

        return type;
    }

    private NewDocType object() {
        var inner = root(tokens.next());
        if (tokens.next() != DocToken.Symbol.END_CURLY) {
            var error = "Expected '}' to close object type";
            var errorPos = tokens.lastPos();

            diagnostics.addDiagnostic(new DocParseError(errorPos, error, DocParseError.Type.TYPE_PARSE));
        }
        return new MapDocType(inner);
    }

    private NewDocType array() {
        var inner = root(tokens.next());
        if (tokens.next() != DocToken.Symbol.END_SQUARE) {
            var error = "Expected ']' to close array type";
            var errorPos = tokens.lastPos();

            diagnostics.addDiagnostic(new DocParseError(errorPos, error, DocParseError.Type.TYPE_PARSE));
        }
        return new ArrayDocType(inner);
    }

    private NewDocType function() {
        var arguments = new ArrayList<FunctionDocType.Argument>();
        args:
        while (tokens.peek() instanceof DocToken.Name(var name)) {
            tokens.next();

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

            if (tokens.next() != DocToken.Symbol.COLON) {
                var error = "Expected ':' after argument name in function type";
                var errorPos = tokens.lastPos();

                diagnostics.addDiagnostic(new DocParseError(errorPos, error, DocParseError.Type.TYPE_PARSE));
            }

            var type = root(tokens.next());

            arguments.add(new FunctionDocType.Argument(type, Optional.of(name), kind));

            switch (tokens.peek()) {
                case DocToken.Symbol.COMMA -> tokens.next();
                case DocToken.Symbol.END_PAREN -> {
                    break args;
                }
                default -> {
                    diagnostics.addDiagnostic(new DocParseError(tokens.lastPos(), "Expected ',' or ')' after argument in function type", DocParseError.Type.TYPE_PARSE));
                    break args;
                }
            }
        }

        if (tokens.next() != DocToken.Symbol.END_PAREN) {
            diagnostics.addDiagnostic(new DocParseError(tokens.lastPos(), "Expected ')' to close function type", DocParseError.Type.TYPE_PARSE));
        }

        if (tokens.next() != DocToken.Symbol.ARROW) {
            diagnostics.addDiagnostic(new DocParseError(tokens.lastPos(), "Expected '->' after function arguments", DocParseError.Type.TYPE_PARSE));
        }

        var returnType = root(tokens.next());

        return new FunctionDocType(List.of(), arguments, returnType);
    }
}
