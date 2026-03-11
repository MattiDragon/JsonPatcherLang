package dev.mattidragon.jsonpatcher.lang.parse;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;

import java.util.List;

public interface CommentHandler {
    CommentHandler EMPTY = comments -> {
    };

    static CommentHandler allOf(CommentHandler... handlers) {
        return new CommentHandler() {
            @Override
            public void acceptBlock(List<Comment> comments) {
                for (var handler : handlers) {
                    handler.acceptBlock(comments);
                }
            }

            @Override
            public void newFile() {
                for (var handler : handlers) {
                    handler.newFile();
                }
            }
        };
    }

    void acceptBlock(List<Comment> comments);

    default void newFile() {
    }

    record Comment(String text, SourcePos start) {
    }
}
