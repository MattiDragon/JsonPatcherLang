package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.docs.DocMetadataKeys;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.tag.DocTag;
import dev.mattidragon.jsonpatcher.docs.tag.PositionedString;
import dev.mattidragon.jsonpatcher.docs.tag.TagProcessor;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class DocParser {
    private static final Pattern TAG_PATTERN = Pattern.compile("(?<type>\\w*) *(?<content>.*)");

    private final Tokenizer tokens;
    private final String body;
    private final List<CommentHandler.Comment> tagLines;
    private final DiagnosticsBuilder diagnostics;
    private final SourcePos headerStartPos;
    private final TreeMetadata metadata;

    private final List<DocTag> mutableTagList;
    private final List<DocTag> immutableTagList;

    private List<SourceSpan> dottedNamePositions = List.of();

    private DocParser(String header, String body, List<CommentHandler.Comment> tagLines, SourcePos headerStartPos, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        this.tokens = new Tokenizer(header, headerStartPos);
        this.body = body;
        this.tagLines = tagLines;
        this.diagnostics = diagnostics;
        this.headerStartPos = headerStartPos;
        this.metadata = metadata;
        mutableTagList = new ArrayList<>(tagLines.size());
        immutableTagList = Collections.unmodifiableList(mutableTagList);
    }

    public static @Nullable DocEntry parse(String header, String body, List<CommentHandler.Comment> tags, SourcePos headerStartPos, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        try {
            return new DocParser(header, body, tags, headerStartPos, metadata, diagnostics).parse();
        } catch (FailException e) {
            return null;
        } catch (Tokenizer.EolException e) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(e.getPos().toSpan(), "Unexpected end of line", DocParseDiagnostic.Type.EOL));
            return null;
        }
    }

    private DocEntry parse() throws FailException {
        var hadNext = tokens.hasNext();
        if (!hadNext || !(tokens.next() instanceof DocToken.Name(var firstToken))) {
            var pos = hadNext ? tokens.lastPos() : new SourceSpan(headerStartPos, headerStartPos);
            diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Illegal start of doc comment", DocParseDiagnostic.Type.DOC_PARSE));
            throw new FailException();
        }
        var keywordPos = tokens.lastPos();

        var entry = switch (firstToken) {
            case "library" -> parseLibrary(keywordPos);
            case "global" -> {
                if (tokens.peek() instanceof DocToken.Name(var libraryString) && libraryString.equals("library")) {
                    tokens.next();
                    yield parseGlobalLibrary(keywordPos, tokens.lastPos());
                } else {
                    yield parseGlobal(keywordPos);
                }
            }
            case "property" -> parseProperty(keywordPos);
            case "type" -> parseType(keywordPos);
            case "typealias" -> parseTypeAlias(keywordPos);
            case "metadata" -> parseMetadata(keywordPos);
            case "namespace" -> parseNamespace(keywordPos);
            // Legacy syntax for compatibility
            case "module" -> {
                diagnostics.addDiagnostic(new DocParseDiagnostic(keywordPos, "'module' is deprecated, use 'namespace' instead", DocParseDiagnostic.Type.DEPRECATED_KEYWORD));

                var name = expectName();
                String location = null;
                if (tokens.hasNext()
                    && tokens.peek() instanceof DocToken.Name(var atString)
                    && atString.equals("at")) {
                    tokens.next();
                    location = expectQuotedString();
                }
                expectEol();
                var sharedData = new DocEntry.SharedData(NamespaceDescription.EMPTY, name, body, immutableTagList);
                yield new DocEntry.LibraryEntry(sharedData, Optional.ofNullable(location));
            }
            case "value" -> {
                diagnostics.addDiagnostic(new DocParseDiagnostic(keywordPos, "'value' is deprecated, use 'property' instead", DocParseDiagnostic.Type.DEPRECATED_KEYWORD));

                var owner = expectName();
                expectSymbol(DocToken.Symbol.DOT);
                var name = expectName();
                expectSymbol(DocToken.Symbol.COLON);
                var type = OldTypeParser.parse(tokens, metadata, diagnostics);
                expectEol();
                var sharedData = new DocEntry.SharedData(NamespaceDescription.EMPTY, name, body, immutableTagList);
                yield new DocEntry.PropertyEntry(sharedData, owner, type);
            }
            default -> {
                var pos = tokens.lastPos();
                diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Unknown doc comment type: " + firstToken, DocParseDiagnostic.Type.DOC_PARSE));
                throw new FailException();
            }
        };

        parseTags(entry);
        return entry;
    }

    private void parseTags(DocEntry entry) {
        for (var tagLine : tagLines) {
            var matcher = TAG_PATTERN.matcher(tagLine.text());
            if (!matcher.matches()) {
                throw new IllegalStateException("Tag pattern failed to match '%s' (it should match any string)"
                        .formatted(tagLine.text()));
            }
            var type = matcher.group("type");
            var content = matcher.group("content");

            var startPos = tagLine.start();
            var namePos = new SourceSpan(startPos, startPos.offset(type.length()));
            var contentPos = new SourceSpan(startPos.offset(matcher.start("content")), startPos.offset(tagLine.text().length()));

            var formatted = TagProcessor.COMBINED.process(new PositionedString(type, namePos),
                    new PositionedString(content, contentPos),
                    entry,
                    metadata,
                    diagnostics);

            var tag = new DocTag(type, content, formatted);
            metadata.put(tag, MetadataKey.NAME_POS, namePos);
            metadata.put(tag, MetadataKey.FULL_POS, new SourceSpan(startPos, startPos.offset(tagLine.text().length())));

            mutableTagList.add(tag);
        }
    }

    private DocEntry.NamespaceEntry parseNamespace(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var name = dottedNames.removeLast();
        var namePos = dottedNamePositions.removeLast();
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), name, body, immutableTagList);
        var entry = new DocEntry.NamespaceEntry(sharedData);
        attachStandardMetadata(entry, keywordPos, namePos);
        return entry;
    }

    private DocEntry.MetadataEntry parseMetadata(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var name = dottedNames.removeLast();
        var namePos = dottedNamePositions.removeLast();
        expectSymbol(DocToken.Symbol.COLON);
        var type = TypeParser.parse(tokens, metadata, diagnostics);
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), name, body, immutableTagList);
        var entry = new DocEntry.MetadataEntry(sharedData, type);
        attachStandardMetadata(entry, keywordPos, namePos);
        return entry;
    }

    private DocEntry.TypeAliasEntry parseTypeAlias(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var name = dottedNames.removeLast();
        var namePos = dottedNamePositions.removeLast();
        expectSymbol(DocToken.Symbol.COLON);
        var definition = TypeParser.parse(tokens, metadata, diagnostics);
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), name, body, immutableTagList);
        var entry = new DocEntry.TypeAliasEntry(sharedData, definition);
        attachStandardMetadata(entry, keywordPos, namePos);
        return entry;
    }

    private DocEntry.TypeDeclarationEntry parseType(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var name = dottedNames.removeLast();
        var namePos = dottedNamePositions.removeLast();
        expectSymbol(DocToken.Symbol.COLON);
        var baseType = switch (tokens.next()) {
            case DocToken.Name(var s) when s.equals("object") -> DocEntry.TypeDeclarationEntry.BaseType.OBJECT;
            case DocToken.Name(var s) when s.equals("special") -> DocEntry.TypeDeclarationEntry.BaseType.SPECIAL;
            default -> {
                var pos = tokens.lastPos();
                diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Unknown base type. Must be either object or special", DocParseDiagnostic.Type.DOC_PARSE));
                yield DocEntry.TypeDeclarationEntry.BaseType.OBJECT;
            }
        };
        var baseTypePos = tokens.lastPos();
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), name, body, immutableTagList);
        var entry = new DocEntry.TypeDeclarationEntry(sharedData, baseType);
        attachStandardMetadata(entry, keywordPos, namePos);
        metadata.put(entry, DocMetadataKeys.BASE_TYPE_POS, baseTypePos);
        return entry;
    }

    private DocEntry.PropertyEntry parseProperty(SourceSpan keywordPos) throws FailException {
        var dottedNames = readPropertyName();
        var name = dottedNames.removeLast();
        var namePos = dottedNamePositions.removeLast();
        String owner;
        SourceSpan ownerPos;
        if (dottedNames.isEmpty()) {
            var pos = tokens.lastPos();
            diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Property must have owner", DocParseDiagnostic.Type.DOC_PARSE));
            owner = "";
            ownerPos = new SourceSpan(pos.to().offset(1), pos.to().offset(1));
        } else {
            owner = dottedNames.removeLast();
            ownerPos = dottedNamePositions.removeLast();
        }
        expectSymbol(DocToken.Symbol.COLON);
        var type = TypeParser.parse(tokens, metadata, diagnostics);
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), name, body, immutableTagList);
        var entry = new DocEntry.PropertyEntry(sharedData, owner, type);
        attachStandardMetadata(entry, keywordPos, namePos);
        metadata.put(entry, DocMetadataKeys.PROPERTY_OWNER_POS, ownerPos);
        return entry;
    }

    private DocEntry.GlobalValueEntry parseGlobal(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var globalName = dottedNames.removeLast();
        var globalNamePos = dottedNamePositions.removeLast();
        expectSymbol(DocToken.Symbol.COLON);
        var type = TypeParser.parse(tokens, metadata, diagnostics);
        expectEol();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), globalName, body, immutableTagList);
        var entry = new DocEntry.GlobalValueEntry(sharedData, type);
        attachStandardMetadata(entry, keywordPos, globalNamePos);
        return entry;
    }

    private DocEntry.GlobalLibraryEntry parseGlobalLibrary(SourceSpan keywordPos, SourceSpan secondKeywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var libName = dottedNames.removeLast();
        var libNamePos = dottedNamePositions.removeLast();
        var sharedData = new DocEntry.SharedData(new NamespaceDescription(dottedNames), libName, body, immutableTagList);
        var entry = new DocEntry.GlobalLibraryEntry(sharedData);
        attachStandardMetadata(entry, keywordPos, libNamePos);
        metadata.put(entry, MetadataKey.SECONDARY_KEYWORD_POS, secondKeywordPos);
        return entry;
    }

    private DocEntry.LibraryEntry parseLibrary(SourceSpan keywordPos) throws FailException {
        var dottedNames = readDottedNames();
        var libName = dottedNames.removeLast();
        var libNamePos = dottedNamePositions.removeLast();
        var namespace = new NamespaceDescription(dottedNames);
        var location = checkLibraryLocation();
        var locationsPos = location == null ? null : tokens.lastPos();
        expectEol();
        var sharedData = new DocEntry.SharedData(namespace, libName, body, immutableTagList);
        var entry = new DocEntry.LibraryEntry(sharedData, Optional.ofNullable(location));
        attachStandardMetadata(entry, keywordPos, libNamePos);
        metadata.put(entry, MetadataKey.IMPORT_LOCATION_POS, locationsPos != null ? locationsPos : libNamePos);
        return entry;
    }

    private void attachStandardMetadata(DocEntry entry, SourceSpan keywordPos, SourceSpan namePos) {
        metadata.put(entry, DocMetadataKeys.NAMESPACE_POSITIONS, dottedNamePositions);
        metadata.put(entry, MetadataKey.NAME_POS, namePos);
        metadata.put(entry, MetadataKey.KEYWORD_POS, keywordPos);
        metadata.put(entry, MetadataKey.FULL_POS, SourceSpan.between(keywordPos, tokens.lastPos()));
    }

    private @Nullable String checkLibraryLocation() {
        if (!tokens.hasNext()) return null;

        // Unexpected tokens aren't an error as it might be a condition
        if (!(tokens.peek() instanceof DocToken.Name(var atString)) || !atString.equals("at")) {
            return null;
        }
        tokens.next();

        if (!tokens.hasNext() || !(tokens.next() instanceof DocToken.Quoted(var location))) {
            var pos = tokens.lastPos();
            diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Expected location after 'at'", DocParseDiagnostic.Type.DOC_PARSE));
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

    // Special version of the above that supports a star at the end
    private List<String> readPropertyName() throws FailException {
        var names = new ArrayList<String>();
        dottedNamePositions = new ArrayList<>();
        names.add(expectName());
        dottedNamePositions.add(tokens.lastPos());
        while (tokens.hasNext() && tokens.peek() == DocToken.Symbol.DOT) {
            tokens.next();
            var upcoming = tokens.peek();
            if (upcoming == DocToken.Symbol.STAR) {
                tokens.next();
                names.add("*");
                dottedNamePositions.add(tokens.lastPos());
                if (tokens.hasNext() && tokens.peek() == DocToken.Symbol.DOT) {
                    diagnostics.addDiagnostic(new DocParseDiagnostic(tokens.lastPos(), "Unexpected '.' after '*'", DocParseDiagnostic.Type.DOC_PARSE));
                }
                break;
            } else {
                names.add(expectName());
            }
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
        diagnostics.addDiagnostic(new DocParseDiagnostic(errorPos, "Expected name", DocParseDiagnostic.Type.DOC_PARSE));
        throw new FailException();
    }

    private String expectQuotedString() throws FailException {
        if (tokens.hasNext() && tokens.next() instanceof DocToken.Quoted(var string)) {
            return string;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseDiagnostic(errorPos, "Expected string", DocParseDiagnostic.Type.DOC_PARSE));
        throw new FailException();
    }

    private void expectSymbol(DocToken.Symbol symbol) throws FailException {
        if (tokens.hasNext() && tokens.next() == symbol) {
            return;
        }
        var lastPos = tokens.lastPos();
        var errorPos = new SourceSpan(lastPos.to().offset(1), lastPos.to().offset(1));
        diagnostics.addDiagnostic(new DocParseDiagnostic(errorPos, "Expected " + symbol, DocParseDiagnostic.Type.DOC_PARSE));
        throw new FailException();
    }

    private void expectEol() {
        if (tokens.hasNext()) {
            var pos = tokens.lastPos();
            diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Expected end of line", DocParseDiagnostic.Type.DOC_PARSE));
        }
    }

    private static class FailException extends Exception {
    }
}
