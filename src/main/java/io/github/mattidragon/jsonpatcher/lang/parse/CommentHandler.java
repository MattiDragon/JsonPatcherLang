package io.github.mattidragon.jsonpatcher.lang.parse;

import java.util.List;

public interface CommentHandler {
    CommentHandler EMPTY = comments -> {};
    
    void acceptBlock(List<Comment> comments);
    
    record Comment(String text, SourcePos start) {}
}
