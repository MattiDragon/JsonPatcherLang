package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DocParser implements CommentHandler {
    // Regex for parsing comment headers. Works by first checking the kind of header in a capture group and then using lookbehind to parse based on that.
    // This allows use to capture the kind in a single group, which wouldn't be possible if the capture was with the parsing.
    //                                    The kind of doc comment      If kind is type, parse type                            If kind is value, parse value                                              If kind is module, parse module
    //                                   |------------------------|   |----------------------------------------------------| |------------------------------------------------------------------------| |------------------------------------------------------------------|
    private static final String REGEX = "(?<kind>type|value|module)(?:(?<=type) *(?<typename>\\w+) *: *(?<typedefinition>.+)|(?<=value) *(?<owner>\\w+)\\.(?<valuename>\\w+) *: *(?<valuedefinition>.+)|(?<=module) *(?<modulename>\\w+)(?: +at +\"(?<modulelocation>.+)\")?)";
    private static final Pattern HEADER_PATTERN = Pattern.compile(REGEX);
    private final LangConfig config;
    private final List<DocEntry> entries = new ArrayList<>();
    private final List<DocParseException> errors = new ArrayList<>();

    public DocParser(LangConfig config) {
        this.config = config;
    }

    public void parse(String code, String file) {
        var result = Lexer.lex(config, code, file, this);
        for (var error : result.errors()) {
            errors.add(new DocParseException(config, "Failed to lex %s: %s".formatted(file, error.getMessage()), error.getPos()));
        }
    }

    public List<DocEntry> getEntries() {
        return entries;
    }

    public List<DocParseException> getErrors() {
        return errors;
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
                errors.add(e);
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
        var matcher = HEADER_PATTERN.matcher(header.text());
        if (!matcher.matches()) {
            throw new DocParseException(config, "Failed to parse comment header", header.start());
        }

        return switch (matcher.group("kind")) {
            case "type" -> new DocEntry.Type(
                    matcher.group("typename"),
                    TypeParser.parse(matcher.group("typedefinition"), config, groupPos(header, matcher, "typedefinition").from()),
                    body,
                    groupPos(header, matcher, "typename"));
            case "value" -> new DocEntry.Value(
                    matcher.group("owner"),
                    matcher.group("valuename"),
                    TypeParser.parse(matcher.group("valuedefinition"), config, groupPos(header, matcher, "valuedefinition").from()),
                    body,
                    groupPos(header, matcher, "owner"),
                    groupPos(header, matcher, "valuename"));
            case "module" -> new DocEntry.Module(
                    matcher.group("modulename"),
                    matcher.group("modulelocation") != null ? matcher.group("modulelocation") : matcher.group("modulename"),
                    body,
                    groupPos(header, matcher, "modulename"),
                    matcher.group("modulelocation") != null ? groupPos(header, matcher, "modulelocation") : null
            );
            default -> throw new IllegalStateException("Regex produced impossible capture group");
        };
    }
    
    private SourceSpan groupPos(CommentHandler.Comment header, Matcher matcher, String group) {
        var start = header.start();
        return new SourceSpan(start.offset(matcher.start(group)), start.offset(matcher.end(group) - 1));
    }
}
