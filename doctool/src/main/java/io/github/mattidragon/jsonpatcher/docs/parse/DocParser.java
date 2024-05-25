package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DocParser {
    // Regex for parsing comment headers. Works by first checking the kind of header in a capture group and then using lookbehind to parse based on that.
    // This allows use to capture the kind in a single group, which wouldn't be possible if the capture was with the parsing.
    //                                    The kind of doc comment      If kind is type, parse type                            If kind is value, parse value                                              If kind is module, parse module
    //                                   |------------------------|   |----------------------------------------------------| |------------------------------------------------------------------------| |------------------------------|
    private static final String REGEX = "(?<kind>type|value|module)(?:(?<=type) *(?<typename>\\w+) *: *(?<typedefinition>.+)|(?<=value) *(?<owner>\\w+)\\.(?<valuename>\\w+) *: *(?<valuedefinition>.+)|(?<=module) *(?<modulename>\\w+))";
    private static final Pattern HEADER_PATTERN = Pattern.compile(REGEX);
    private final List<DocEntry> entries = new ArrayList<>();
    private final List<DocParseException> errors = new ArrayList<>();
    private String currentFile;

    public void parse(String code, String file) {
        try {
            currentFile = file;
            Lexer.lex(code, file, this::handleBlock);
            currentFile = null;
        } catch (Lexer.LexException e) {
            errors.add(new DocParseException("Failed to lex %s: %s".formatted(file, e.getMessage())));
        }
    }

    public List<DocEntry> getEntries() {
        return entries;
    }

    public List<DocParseException> getErrors() {
        return errors;
    }

    private void handleBlock(List<String> block) {
        var docBlocks = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        for (var line : block) {
            if (line.startsWith("|")) {
                current.add(line.substring(1).trim());
            } else if (!current.isEmpty()) {
                docBlocks.add(current);
            }
        }
        if (!current.isEmpty()) {
            docBlocks.add(current);
        }
        
        for (var docBlock : docBlocks) {
            try {
                var entry = parseEntry(docBlock.getFirst(), docBlock.stream().skip(1).collect(Collectors.joining("\n")));
                entries.add(entry);
            } catch (DocParseException e) {
                errors.add(e);
            }
        }
    }
    
    private DocEntry parseEntry(String header, String body) {
        var matcher = HEADER_PATTERN.matcher(header);
        if (!matcher.matches()) {
            throw new DocParseException("Failed to parse comment header in %s:\n%s".formatted(currentFile, header));
        }

        return switch (matcher.group("kind")) {
            case "type" -> new DocEntry.Type(matcher.group("typename"), TypeParser.parse(matcher.group("typedefinition"), currentFile), body);
            case "value" -> new DocEntry.Value(matcher.group("owner"), matcher.group("valuename"), TypeParser.parse(matcher.group("valuedefinition"), currentFile), body);
            case "module" -> new DocEntry.Module(matcher.group("modulename"), body);
            default -> throw new IllegalStateException("Regex produced impossible capture group");
        };
    }
}
