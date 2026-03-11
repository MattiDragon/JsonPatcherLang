package dev.mattidragon.jsonpatcher.docs;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.docs.parse.DocParser;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DocCommentHandler implements CommentHandler {
    private static final NamespaceDescription EMPTY_NAMESPACE = new NamespaceDescription(List.of());

    private final List<DocEntry> entries = new ArrayList<>();
    private final DiagnosticsBuilder diagnostics;
    private final TreeMetadata metadata;

    private NamespaceDescription currentNs = EMPTY_NAMESPACE;

    public DocCommentHandler(DiagnosticsBuilder diagnostics, TreeMetadata metadata) {
        this.diagnostics = diagnostics;
        this.metadata = metadata;
    }

    @Override
    public void acceptBlock(List<Comment> commentBlock) {
        var docBlocks = new ArrayList<Block>();
        var tagLines = new ArrayList<Comment>();
        var docLines = new ArrayList<Comment>();

        for (var line : commentBlock) {
            if (line.text().startsWith("|")) {
                var trimmedLine = trimStart(line);
                if (trimmedLine.text().startsWith("@")) {
                    tagLines.add(trimStart(trimmedLine));
                } else {
                    docLines.add(trimmedLine);
                }
            } else if (!docLines.isEmpty()) {
                docBlocks.add(new DocBlock(docLines, tagLines));
                tagLines = new ArrayList<>();
                docLines = new ArrayList<>();
            } else if (!tagLines.isEmpty()) {
                var pos = new SourceSpan(tagLines.getFirst().start(),
                        tagLines.getLast().start().offset(tagLines.getLast().text().length()));
                diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Illegal doc comment with only tags", DocParseDiagnostic.Type.TAG_ONLY_COMMENT));
                tagLines = new ArrayList<>();
            }

            if (line.text().stripLeading().startsWith("@@doc_namespace")) {
                var trimmedLine = trimStart(line);
                var content = trimmedLine.text().substring("@@doc_namespace".length());
                docBlocks.add(new NsSwitchBlock(new Comment(content, trimmedLine.start().offset("@@doc_namespace".length()))));
            }
        }
        if (!docLines.isEmpty()) {
            docBlocks.add(new DocBlock(docLines, tagLines));
        } else if (!tagLines.isEmpty()) {
            var pos = new SourceSpan(tagLines.getFirst().start(),
                    tagLines.getLast().start().offset(tagLines.getLast().text().length()));
            diagnostics.addDiagnostic(new DocParseDiagnostic(pos, "Illegal doc comment with only tags", DocParseDiagnostic.Type.TAG_ONLY_COMMENT));
        }

        for (var block : docBlocks) {
            switch (block) {
                case DocBlock docBlock -> {
                    var body = docBlock.docLines()
                            .stream()
                            .skip(1)
                            .map(Comment::text)
                            .collect(Collectors.joining("\n"));

                    var entry = DocParser.parse(
                            docBlock.docLines().getFirst().text(),
                            body,
                            docBlock.tagLines(),
                            docBlock.docLines().getFirst().start(),
                            currentNs,
                            metadata,
                            diagnostics
                    );
                    if (entry == null) {
                        continue;
                    }
                    entries.add(entry);
                }
                case NsSwitchBlock(var line) -> {
                    if (line.text().isEmpty()) {
                        currentNs = EMPTY_NAMESPACE;
                    } else {
                        var parts = line.text().strip().split("\\.");
                        currentNs = new NamespaceDescription(List.of(parts));
                    }
                }
            }
        }
    }

    @Override
    public void newFile() {
        currentNs = EMPTY_NAMESPACE;
    }

    public List<DocEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    private static Comment trimStart(Comment line) {
        var origPos = line.start();
        var preTrim = line.text().substring(1);
        var postTrim = preTrim.stripLeading();
        var pos = origPos.offset(1 + (preTrim.length() - postTrim.length()));
        return new Comment(postTrim.stripTrailing(), pos);
    }

    private sealed interface Block {
    }

    private record NsSwitchBlock(Comment line) implements Block {
    }

    private record DocBlock(List<Comment> docLines, List<Comment> tagLines) implements Block {
    }
}
