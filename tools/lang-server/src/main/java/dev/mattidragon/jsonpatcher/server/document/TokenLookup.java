package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TokenLookup {
    private final PosLookup<Integer> indices = new PosLookup<>();
    private final List<PositionedToken> tokens;

    public TokenLookup(List<PositionedToken> tokens) {
        this.tokens = tokens;
        for (var i = 0; i < tokens.size(); i++) {
            indices.add(tokens.get(i).pos(), i);
        }
    }

    public @Nullable IndexedToken getAt(SourcePos pos) {
        var index = indices.getFirstAt(pos);
        if (index == null) return null;
        return new IndexedToken(index);
    }

    public class IndexedToken {
        private final int index;

        private IndexedToken(int index) {
            this.index = index;
        }

        public Token token() {
            return tokens.get(index).token();
        }

        public SourceSpan pos() {
            return tokens.get(index).pos();
        }

        public @Nullable IndexedToken next() {
            if (index + 1 == tokens.size()) return null;
            return new IndexedToken(index + 1);
        }

        public @Nullable IndexedToken previous() {
            if (index == 0) return null;
            return new IndexedToken(index - 1);
        }
    }
}
