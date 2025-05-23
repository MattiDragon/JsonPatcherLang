package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.docs.data.DocCondition;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class DocConditionParser {
    public static DocCondition parse(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var token = tokenizer.next();
        if (!tokenizer.hasNext() || !(token instanceof DocToken.Name(var name))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }
        
        return switch (name) {
            case "all", "and" -> parseMultiChild(tokenizer, metadata, diagnostics, DocCondition.AndCondition::new);
            case "any", "or" -> parseMultiChild(tokenizer, metadata, diagnostics, DocCondition.OrCondition::new);
            case "none" -> {
                var inner = parseMultiChild(tokenizer, metadata, diagnostics, DocCondition.OrCondition::new);
                var wrapped = new DocCondition.NotCondition(inner);
                metadata.copy(inner, wrapped, MetadataKey.NAME_POS);
                metadata.copy(inner, wrapped, MetadataKey.FULL_POS);
                yield wrapped;
            }
            case "not" -> parseNot(tokenizer, metadata, diagnostics);
            case "libgroup" -> parseLibGroup(tokenizer, metadata, diagnostics);
            case "metadata" -> parseMetadata(tokenizer, metadata, diagnostics);
            case "version" -> parseVersion(tokenizer, metadata, diagnostics);
            
            case String other -> {
                diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Unknown condition function: " + other, DocParseDiagnostic.Type.UNKNOWN_CONDITION));
                var startPos = tokenizer.lastPos();
                
                // Skip content, counting parens to handle nested inner syntax
                if (tokenizer.peek() == DocToken.Symbol.BEGIN_PAREN) {
                    tokenizer.next();
                    skipToEnd(tokenizer);
                }
                var endPos = tokenizer.lastPos();
                
                // Empty or condition never succeeds
                var condition = never();
                metadata.put(condition, MetadataKey.NAME_POS, startPos);
                metadata.put(condition, MetadataKey.FULL_POS, SourceSpan.between(startPos, endPos));
                yield condition;
            }
        };
    }

    private static void skipToEnd(Tokenizer tokenizer) {
        var parenCount = 1;
        while (parenCount > 0 && tokenizer.hasNext()) {
            switch (tokenizer.next()) {
                case DocToken.Symbol.BEGIN_PAREN -> parenCount++;
                case DocToken.Symbol.END_PAREN -> parenCount--;
                default -> {}
            }
        }
    }

    private static DocCondition parseVersion(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var namePos = tokenizer.lastPos();

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.BEGIN_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '(' after condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var mode = switch (tokenizer.hasNext() ? tokenizer.next() : null) {
            case DocToken.Symbol.BEGIN_ANGLE -> DocCondition.VersionCondition.Mode.LESSER;
            case DocToken.Symbol.END_ANGLE -> DocCondition.VersionCondition.Mode.GREATER;
            case DocToken.Symbol.EQUAL -> DocCondition.VersionCondition.Mode.EXACT;
            case DocToken.Symbol.CARET -> DocCondition.VersionCondition.Mode.MAJOR;
            case DocToken.Symbol.TILDE -> DocCondition.VersionCondition.Mode.MINOR;
            case null, default -> {
                diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected comparison specifier", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
                yield null;
            }
        };
        if (mode == null) {
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || !(tokenizer.next() instanceof DocToken.Number(var major))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected major version number", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.DOT) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '.' after major version number", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || !(tokenizer.next() instanceof DocToken.Number(var minor))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected minor version number", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.DOT) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '.' after minor version number", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || !(tokenizer.next() instanceof DocToken.Number(var patch))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected patch version number", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.END_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ')' after condition function argument", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var condition = new DocCondition.VersionCondition(major, minor, patch, mode);
        metadata.put(condition, MetadataKey.NAME_POS, namePos);
        metadata.put(condition, MetadataKey.FULL_POS, SourceSpan.between(namePos, tokenizer.lastPos()));
        return condition;
    }

    private static DocCondition parseMultiChild(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics, Function<List<DocCondition>, DocCondition> constructor) {
        var startPos = tokenizer.lastPos();

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.BEGIN_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '(' after condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var conditions = new ArrayList<DocCondition>();
        while (tokenizer.hasNext()) {
            conditions.add(parse(tokenizer, metadata, diagnostics));

            if (!tokenizer.hasNext()) {
                diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ')' after condition function arguments", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
                return never();
            }

            if (tokenizer.peek() == DocToken.Symbol.END_PAREN) {
                tokenizer.next();
                break;
            }

            if (tokenizer.peek() != DocToken.Symbol.COMMA) {
                diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ',' or ')' after condition function argument", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
                skipToEnd(tokenizer);
                return never();
            }

            tokenizer.next();
        }

        var endPos = tokenizer.lastPos();

        var condition = constructor.apply(conditions);
        metadata.put(condition, MetadataKey.NAME_POS, startPos);
        metadata.put(condition, MetadataKey.FULL_POS, SourceSpan.between(startPos, endPos));
        return condition;
    }

    private static DocCondition parseNot(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.BEGIN_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '(' after condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var inner = parse(tokenizer, metadata, diagnostics);

        if (!tokenizer.hasNext() || tokenizer.peek() != DocToken.Symbol.END_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ')' after condition function argument", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var wrapped = new DocCondition.NotCondition(inner);
        metadata.copy(inner, wrapped, MetadataKey.NAME_POS);
        metadata.copy(inner, wrapped, MetadataKey.FULL_POS);
        return wrapped;
    }

    private static DocCondition parseLibGroup(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var namePos = tokenizer.lastPos();

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.BEGIN_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '(' after condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        if (!tokenizer.hasNext() || !(tokenizer.next() instanceof DocToken.Quoted(var groupName))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected library group name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.END_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ')' after condition function argument", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var condition = new DocCondition.LibraryGroupCondition(groupName);
        metadata.put(condition, MetadataKey.NAME_POS, namePos);
        metadata.put(condition, MetadataKey.FULL_POS, SourceSpan.between(tokenizer.lastPos(), tokenizer.lastPos()));
        return condition;
    }

    private static DocCondition parseMetadata(Tokenizer tokenizer, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var namePos = tokenizer.lastPos();

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.BEGIN_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected '(' after condition function name", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        if (!tokenizer.hasNext() || !(tokenizer.next() instanceof DocToken.Name(var key))) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected metadata key", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            skipToEnd(tokenizer);
            return never();
        }

        // TODO: parse value

        if (!tokenizer.hasNext() || tokenizer.next() != DocToken.Symbol.END_PAREN) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(tokenizer.lastPos(), "Expected ')' after condition function argument", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
            return never();
        }

        var condition = new DocCondition.MetadataCondition(key, Optional.empty());
        metadata.put(condition, MetadataKey.NAME_POS, namePos);
        metadata.put(condition, MetadataKey.FULL_POS, SourceSpan.between(tokenizer.lastPos(), tokenizer.lastPos()));
        return condition;
    }

    private static DocCondition never() {
        return new DocCondition.OrCondition(List.of());
    }
}
