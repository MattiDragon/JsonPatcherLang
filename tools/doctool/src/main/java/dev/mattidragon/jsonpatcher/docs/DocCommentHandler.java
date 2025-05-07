package dev.mattidragon.jsonpatcher.docs;

import dev.mattidragon.jsonpatcher.docs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.NewDocParser;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DocCommentHandler implements CommentHandler {
    private final List<NewDocEntry> entries = new ArrayList<>();
    private final DiagnosticsBuilder diagnostics;
    private final TreeMetadata metadata;

    public DocCommentHandler(DiagnosticsBuilder diagnostics, TreeMetadata metadata) {
        this.diagnostics = diagnostics;
        this.metadata = metadata;
    }

    @Override
    public void acceptBlock(List<Comment> block) {
        var docBlocks = new ArrayList<List<Comment>>();
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
            var body = docBlock.stream()
                    .skip(1)
                    .map(Comment::text)
                    .collect(Collectors.joining("\n"));

            var entry = NewDocParser.parse(
                    docBlock.getFirst().text(),
                    body,
                    docBlock.getFirst().start(),
                    metadata,
                    diagnostics
            );
            if (entry == null) {
                return;
            }
            entries.add(entry);
        }
    }

    public List<NewDocEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    private static CommentHandler.Comment trimStart(CommentHandler.Comment line) {
        var origPos = line.start();
        var preTrim = line.text().substring(1);
        var postTrim = preTrim.stripLeading();
        var pos = origPos.offset(1 + (preTrim.length() - postTrim.length()));
        return new CommentHandler.Comment(postTrim.stripTrailing(), pos);
    }
}
