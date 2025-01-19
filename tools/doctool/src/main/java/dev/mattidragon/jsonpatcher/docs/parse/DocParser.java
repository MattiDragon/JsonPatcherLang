package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DocParser implements CommentHandler {
    private final List<DocEntry> entries = new ArrayList<>();
    private final DiagnosticsBuilder diagnostics;

    public DocParser(DiagnosticsBuilder diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void parse(String code, String file) {
        Lexer.lex(code, file, diagnostics, this);
    }

    public List<DocEntry> getEntries() {
        return entries;
    }

    @Override
    public void acceptBlock(List<CommentHandler.Comment> block) {
        var docBlocks = new ArrayList<List<CommentHandler.Comment>>();
        var current = new ArrayList<CommentHandler.Comment>();
        for (var line : block) {
            if (line.text().startsWith("|")) {
                current.add(trimStart(line));
            } else if (!current.isEmpty()) {
                docBlocks.add(current);
            }
        }
        if (!current.isEmpty()) {
            docBlocks.add(current);
        }
        
        for (var docBlock : docBlocks) {
            try {
                var entry = parseEntry(docBlock.getFirst(), docBlock.stream().map(CommentHandler.Comment::text).skip(1).collect(Collectors.joining("\n")));
                entries.add(entry);
            } catch (DocParseException e) {
                diagnostics.addDiagnostic(e.error());
            }
        }
    }

    private static CommentHandler.Comment trimStart(CommentHandler.Comment line) {
        var origPos = line.start();
        var preTrim = line.text().substring(1);
        var postTrim = preTrim.stripLeading();
        var pos = origPos.offset(1 + (preTrim.length() - postTrim.length()));
        return new CommentHandler.Comment(postTrim.stripTrailing(), pos);
    }

    private DocEntry parseEntry(CommentHandler.Comment header, String body) {
        var parseTool = new ParseTool(header.text(), header.start());
        parseTool.skipWhitespace();
        var entryType = parseTool.readWord();
        var entryTypePos = parseTool.span();
        switch (entryType) {
            case "type" -> {
                parseTool.skipWhitespace();
                var name = parseTool.readWord();
                var namePos = parseTool.span();
                parseTool.skipWhitespace();
                parseTool.expect(':');
                parseTool.skipWhitespace();
                var definition = TypeParser.parse(parseTool);
                parseTool.expectEol();
                return new DocEntry.Type(name, definition, body, namePos);
            }
            case "value" -> {
                parseTool.skipWhitespace();
                var owner = parseTool.readWord();
                var ownerPos = parseTool.span();
                parseTool.expect('.');
                var name = parseTool.readWord();
                var namePos = parseTool.span();
                parseTool.skipWhitespace();
                parseTool.expect(':');
                parseTool.skipWhitespace();
                var definition = TypeParser.parse(parseTool);
                parseTool.expectEol();
                return new DocEntry.Value(owner, name, definition, body, ownerPos, namePos);
            }
            case "module" -> {
                parseTool.skipWhitespace();
                var name = parseTool.readWord();
                var namePos = parseTool.span();
                parseTool.skipWhitespace();
                var location = name;
                SourceSpan locationPos = null;
                if (parseTool.hasNext()) {
                    parseTool.expectWord("at");
                    parseTool.skipWhitespace();
                    location = parseTool.readString();
                    locationPos = parseTool.span();
                }
                parseTool.expectEol();
                return new DocEntry.Module(name, location, body, namePos, locationPos);
            }
            case "global" -> {
                parseTool.skipWhitespace();
                var globalEntryType = parseTool.readWord();
                var globalEntryTypePos = parseTool.span();
                switch (globalEntryType) {
                    case "value" -> {
                        parseTool.skipWhitespace();
                        var name = parseTool.readWord();
                        var namePos = parseTool.span();
                        parseTool.skipWhitespace();
                        parseTool.expect(':');
                        parseTool.skipWhitespace();
                        var definition = TypeParser.parse(parseTool);
                        parseTool.skipWhitespace();
                        var requiredMetadata = readRequiresClause(parseTool);
                        parseTool.expectEol();
                        return new DocEntry.GlobalValue(name, definition, body, namePos, requiredMetadata);
                    }
                    case "module" -> {
                        parseTool.skipWhitespace();
                        var name = parseTool.readWord();
                        var namePos = parseTool.span();
                        var requiredMetadata = readRequiresClause(parseTool);
                        parseTool.expectEol();
                        return new DocEntry.GlobalModule(name, body, namePos, requiredMetadata);
                    }
                    default -> throw new DocParseException("Unknown doc entry type: global " + globalEntryType, globalEntryTypePos, DocParseError.Code.UNKNOWN_ENTRY_TYPE);
                }
            }
            default -> throw new DocParseException("Unknown doc entry type: " + entryType, entryTypePos, DocParseError.Code.UNKNOWN_ENTRY_TYPE);
        }
    }

    private static ArrayList<String> readRequiresClause(ParseTool parseTool) {
        var requiredMetadata = new ArrayList<String>();
        parseTool.skipWhitespace();
        if (parseTool.hasNext()) {
            parseTool.expectWord("requires");
            parseTool.skipWhitespace();
            requiredMetadata.add(parseTool.readWord());
            parseTool.skipWhitespace();
            while (parseTool.hasNext() && parseTool.peek() == ',') {
                parseTool.next();
                parseTool.skipWhitespace();
                requiredMetadata.add(parseTool.readWord());
                parseTool.skipWhitespace();
            }
        }
        return requiredMetadata;
    }
}
