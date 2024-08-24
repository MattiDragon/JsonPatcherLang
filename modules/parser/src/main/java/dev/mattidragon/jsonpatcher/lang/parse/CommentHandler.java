package dev.mattidragon.jsonpatcher.lang.parse;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;

import java.util.List;

public interface CommentHandler {
    CommentHandler EMPTY = comments -> {};
    
    static CommentHandler allOf(CommentHandler... handlers) {
        return comments -> {
            for (var handler : handlers) {
                handler.acceptBlock(comments);
            }
        };
    }
    
    void acceptBlock(List<Comment> comments);
    
    record Comment(String text, SourcePos start) {}
}
