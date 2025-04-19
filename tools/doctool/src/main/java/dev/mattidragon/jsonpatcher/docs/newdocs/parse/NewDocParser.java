package dev.mattidragon.jsonpatcher.docs.newdocs.parse;

import dev.mattidragon.jsonpatcher.docs.newdocs.DocMetadataKeys;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.DocCondition;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NewDocParser {
    private final Tokenizer tokens;
    private final String body;
    private final DiagnosticsBuilder diagnostics;
    private final SourcePos headerStartPos;
    private final TreeMetadata metadata;
    private List<SourceSpan> dottedNamePositions = List.of();

    private NewDocParser(String header, String body, SourcePos headerStartPos, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        this.tokens = new Tokenizer(header, headerStartPos);
        this.body = body;
        this.diagnostics = diagnostics;
        this.headerStartPos = headerStartPos;
        this.metadata = metadata;
    }

    public static @Nullable NewDocEntry parse(String header, String body, SourcePos headerStartPos, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        try {
            return new NewDocParser(header, body, headerStartPos, metadata, diagnostics).parse();
        } catch (FailException e) {
            return null;
        }
    }

    private NewDocEntry parse() throws FailException {
        var hadNext = tokens.hasNext();
        if (!hadNext || !(tokens.next() instanceof DocToken.Name(var firstToken))) {
            var pos = hadNext ? tokens.lastPos() : new SourceSpan(headerStartPos, headerStartPos);
            diagnostics.addDiagnostic(new DocParseError(pos, "Illegal start of doc comment", DocParseError.Type.DOC_PARSE));
            throw new FailException();
        }
        var keywordPos = tokens.lastPos();

        // TODO: attach position metadata
        // TODO: handle legacy syntax
        return switch (firstToken) {
            case "library" -> {
                var dottedNames = readDottedNames();
                var libName = dottedNames.removeLast();
                var libNamePos = dottedNamePositions.removeLast();
                var namespace = new NamespaceDescription(dottedNames);
                var location = checkLibraryLocation();
                var locationsPos = location == null ? null : tokens.lastPos();
                var condition = checkCondition();
                expectEol();
                var entry = new NewDocEntry.LibraryEntry(namespace, libName, Optional.ofNullable(location), condition, body);
                metadata.put(entry, DocMetadataKeys.NAMESPACE_POSITIONS, dottedNamePositions);
                metadata.put(entry, MetadataKey.NAME_POS, libNamePos);
                metadata.put(entry, MetadataKey.KEYWORD_POS, keywordPos);
                metadata.put(entry, MetadataKey.IMPORT_LOCATION_POS, locationsPos != null ? locationsPos : libNamePos);
                metadata.put(entry, MetadataKey.FULL_POS, SourceSpan.between(keywordPos, tokens.lastPos()));
                yield entry;
            }
            case "global" -> {
                if (tokens.peek() instanceof DocToken.Name(var libraryString) && libraryString.equals("library")) {
                    tokens.next();
                    var dottedNames = readDottedNames();
                    var libName = dottedNames.removeLast();
                    var libNamePos = dottedNamePositions.removeLast();
                    var condition = checkCondition();
                    var entry = new NewDocEntry.GlobalLibraryEntry(new NamespaceDescription(dottedNames), libName, condition, body);
                    metadata.put(entry, DocMetadataKeys.NAMESPACE_POSITIONS, dottedNamePositions);
                    metadata.put(entry, MetadataKey.NAME_POS, libNamePos);
                    metadata.put(entry, MetadataKey.KEYWORD_POS, keywordPos);
                    metadata.put(entry, MetadataKey.FULL_POS, SourceSpan.between(keywordPos, tokens.lastPos()));
                    yield entry;
                } else {
                    var dottedNames = readDottedNames();
                    var globalName = dottedNames.removeLast();
                    var globalNamePos = dottedNamePositions.removeLast();
                    expectSymbol(DocToken.Symbol.COLON);
                    var type = TypeParser.parse(tokens, metadata, diagnostics);
                    var condition = checkCondition();
                    expectEol();
                    var entry = new NewDocEntry.GlobalValueEntry(new NamespaceDescription(dottedNames), globalName, type, condition, body);
                    metadata.put(entry, DocMetadataKeys.NAMESPACE_POSITIONS, dottedNamePositions);
                    metadata.put(entry, MetadataKey.NAME_POS, globalNamePos);
                    metadata.put(entry, MetadataKey.KEYWORD_POS, keywordPos);
                    metadata.put(entry, MetadataKey.FULL_POS, SourceSpan.between(keywordPos, tokens.lastPos()));
                    yield entry;
                }
            }
            case "property" -> {
                var dottedNames = readDottedNames();
                var name = dottedNames.removeLast();
                var namePos = dottedNamePositions.removeLast();
                String owner;
                SourceSpan ownerPos;
                if (dottedNames.isEmpty()) {
                    var pos = tokens.lastPos();
                    diagnostics.addDiagnostic(new DocParseError(pos, "Property must have owner", DocParseError.Type.DOC_PARSE));
                    owner = "";
                    ownerPos = new SourceSpan(pos.to().offset(1), pos.to().offset(1));
                } else {
                    owner = dottedNames.removeLast();
                    ownerPos = dottedNamePositions.removeLast();
                }
                expectSymbol(DocToken.Symbol.COLON);
                var type = TypeParser.parse(tokens, metadata, diagnostics);
                var condition = checkCondition();
                expectEol();
                var entry = new NewDocEntry.PropertyEntry(new NamespaceDescription(dottedNames), owner, name, type, condition, body);
                metadata.put(entry, DocMetadataKeys.NAMESPACE_POSITIONS, dottedNamePositions);
                metadata.put(entry, DocMetadataKeys.PROPERTY_OWNER_POS, ownerPos);
                metadata.put(entry, MetadataKey.NAME_POS, namePos);
                metadata.put(entry, MetadataKey.KEYWORD_POS, keywordPos);
                metadata.put(entry, MetadataKey.FULL_POS, SourceSpan.between(keywordPos, tokens.lastPos()));
                yield entry;
            }
            case "type" -> {
                var dottedNames = readDottedNames();
                var name = dottedNames.removeLast();
                expectSymbol(DocToken.Symbol.COLON);
                var baseType = switch (tokens.next()) {
                    case DocToken.Name(var s) when s.equals("object") -> NewDocEntry.TypeDeclarationEntry.BaseType.OBJECT;
                    case DocToken.Name(var s) when s.equals("special") -> NewDocEntry.TypeDeclarationEntry.BaseType.SPECIAL;
                    default -> {
                        var pos = tokens.lastPos();
                        diagnostics.addDiagnostic(new DocParseError(pos, "Unknown base type. Must be either object or special", DocParseError.Type.DOC_PARSE));
                        yield NewDocEntry.TypeDeclarationEntry.BaseType.OBJECT;
                    }
                };
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.TypeDeclarationEntry(new NamespaceDescription(dottedNames), name, baseType, condition, body);
            }
            case "typealias" -> {
                var dottedNames = readDottedNames();
                var name = dottedNames.removeLast();
                expectSymbol(DocToken.Symbol.COLON);
                var definition = TypeParser.parse(tokens, metadata, diagnostics);
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.TypeAliasEntry(new NamespaceDescription(dottedNames), name, definition, condition, body);
            }
            case "metadata" -> {
                var dottedNames = readDottedNames();
                var name = dottedNames.removeLast();
                expectSymbol(DocToken.Symbol.COLON);
                var type = TypeParser.parse(tokens, metadata, diagnostics);
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.MetadataEntry(new NamespaceDescription(dottedNames), name, type, condition, body);
            }
            case "namespace" -> {
                var dottedNames = readDottedNames();
                var name = dottedNames.removeLast();
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.NamespaceEntry(new NamespaceDescription(dottedNames), name, condition, body);
            }
            // Legacy syntax for compatibility
            case "module" -> {
                var name = expectName();
                String location = null;
                if (tokens.hasNext()
                    && tokens.peek() instanceof DocToken.Name(var atString)
                    && atString.equals("at")) {
                    tokens.next();
                    location = expectQuotedString();
                }
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.LibraryEntry(NamespaceDescription.EMPTY, name, Optional.ofNullable(location), condition, body);
            }
            case "value" -> {
                var owner = expectName();
                expectSymbol(DocToken.Symbol.DOT);
                var name = expectName();
                expectSymbol(DocToken.Symbol.COLON);
                var type = OldTypeParser.parse(tokens, metadata, diagnostics);
                var condition = checkCondition();
                expectEol();
                yield new NewDocEntry.PropertyEntry(NamespaceDescription.EMPTY, owner, name, type, condition, body);
            }
            default -> {
                var pos = tokens.lastPos();
                diagnostics.addDiagnostic(new DocParseError(pos, "Unknown doc comment type: " + firstToken, DocParseError.Type.DOC_PARSE));
                throw new FailException();
            }
        };
    }

    private Optional<DocCondition> checkCondition() {
        if (!tokens.hasNext() || !(tokens.peek() instanceof DocToken.Name(var whenString)) || !whenString.equals("when")) {
            return Optional.empty();
        }
        tokens.next();

        try {
            return Optional.of(parseCondition());
        } catch (FailException e) {
            return Optional.empty();
        }
    }

    // TODO: make conditions metadata holders and attach positions
    private DocCondition parseCondition() throws FailException {
        var condition = switch (tokens.next()) {
            case DocToken.Symbol.AT -> {
                var name = expectName();

                // TODO: parse value

                yield new DocCondition.MetadataCondition(name, Optional.empty());
            }
            case DocToken.Symbol.HASH -> {
                var group = expectName();
                yield new DocCondition.LibraryGroupCondition(group);
            }
            case DocToken.Name(var s) when s.equals("v") -> {
                var modeSymbol = expectSymbol();
                var mode = switch (modeSymbol) {
                    case DocToken.Symbol.BEGIN_ANGLE -> DocCondition.VersionCondition.Mode.LESSER;
                    case DocToken.Symbol.END_ANGLE -> DocCondition.VersionCondition.Mode.GREATER;
                    case DocToken.Symbol.EQUAL -> DocCondition.VersionCondition.Mode.EXACT;
                    case DocToken.Symbol.CARET -> DocCondition.VersionCondition.Mode.MAJOR;
                    case DocToken.Symbol.TILDE -> DocCondition.VersionCondition.Mode.MINOR;
                    default -> {
                        var pos = tokens.lastPos();
                        diagnostics.addDiagnostic(new DocParseError(pos, "Unknown version comparison mode: " + modeSymbol, DocParseError.Type.DOC_PARSE));
                        yield DocCondition.VersionCondition.Mode.EXACT;
                    }
                };
                var major = expectNumber();
                expectSymbol(DocToken.Symbol.DOT);
                var minor = expectNumber();
                expectSymbol(DocToken.Symbol.DOT);
                var patch = expectNumber();

                yield new DocCondition.VersionCondition(major, minor, patch, mode);
            }
            case DocToken.Symbol.BANG -> {
                var inner = parseCondition();
                yield new DocCondition.NotCondition(inner);
            }
            case DocToken.Symbol.BEGIN_PAREN -> {
                var inner = parseCondition();
                if (tokens.next() != DocToken.Symbol.END_PAREN) {
                    var pos = tokens.lastPos().to().offset(1).toSpan();
                    diagnostics.addDiagnostic(new DocParseError(pos, "Expected closing ')'", DocParseError.Type.DOC_PARSE));
                }
                yield inner;
            }
            default -> {
                diagnostics.addDiagnostic(new DocParseError(tokens.lastPos(), "Unexpected token", DocParseError.Type.DOC_PARSE));
                throw new FailException();
            }
        };

        if (!tokens.hasNext()) return condition;

        while (tokens.peek() == DocToken.Symbol.AND) {
            tokens.next();
            condition = new DocCondition.AndCondition(condition, parseCondition());
        }

        while (tokens.peek() == DocToken.Symbol.BAR) {
            tokens.next();
            condition = new DocCondition.OrCondition(condition, parseCondition());
        }

        return condition;
    }

    private @Nullable String checkLibraryLocation() {
        if (!tokens.hasNext()) return null;

        // Unexpected tokens aren't an error as it might be a condition
        if (!(tokens.peek() instanceof DocToken.Name(var atString)) || !atString.equals("at")) {
            return null;
        }
        tokens.next();

        // TODO: Quoted string
        if (!tokens.hasNext() || !(tokens.next() instanceof DocToken.Name(var location))) {
            var pos = tokens.lastPos();
            diagnostics.addDiagnostic(new DocParseError(pos, "Expected location after 'at'", DocParseError.Type.DOC_PARSE));
            return null;
        }

        return location;
    }

    private List<String> readDottedNames() throws FailException {
        var names = new ArrayList<String>();
        dottedNamePositions = new ArrayList<>();
        names.add(expectName());
        dottedNamePositions.add(tokens.lastPos());
        while (tokens.hasNext() && tokens.peek() == DocToken.Symbol.DOT) {
            tokens.next();
            names.add(expectName());
            dottedNamePositions.add(tokens.lastPos());
        }
        return names;
    }

    private String expectName() throws FailException {
        if (tokens.hasNext() && tokens.next() instanceof DocToken.Name(var name)) {
            return name;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseError(errorPos, "Expected name", DocParseError.Type.DOC_PARSE));
        throw new FailException();
    }

    private int expectNumber() throws FailException {
        if (tokens.hasNext() && tokens.next() instanceof DocToken.Number(var number)) {
            return number;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseError(errorPos, "Expected number", DocParseError.Type.DOC_PARSE));
        throw new FailException();
    }

    private String expectQuotedString() throws FailException {
        if (tokens.hasNext() && tokens.next() instanceof DocToken.Quoted(var string)) {
            return string;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseError(errorPos, "Expected string", DocParseError.Type.DOC_PARSE));
        throw new FailException();
    }

    private void expectSymbol(DocToken.Symbol symbol) throws FailException {
        if (tokens.hasNext() && tokens.next() == symbol) {
            return;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseError(errorPos, "Expected " + symbol, DocParseError.Type.DOC_PARSE));
        throw new FailException();
    }

    private DocToken.Symbol expectSymbol() throws FailException {
        if (tokens.hasNext() && tokens.next() instanceof DocToken.Symbol symbol) {
            return symbol;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseError(errorPos, "Expected symbol", DocParseError.Type.DOC_PARSE));
        throw new FailException();
    }

    private void expectEol() {
        if (tokens.hasNext()) {
            var pos = tokens.lastPos();
            diagnostics.addDiagnostic(new DocParseError(pos, "Expected end of line", DocParseError.Type.DOC_PARSE));
        }
    }

    private static class FailException extends Exception {
    }
}
