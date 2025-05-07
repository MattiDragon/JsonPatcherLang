package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

sealed interface DocToken {
    record Name(String value) implements DocToken {
    }

    record Quoted(String value) implements DocToken {
    }

    record VarName(String value) implements DocToken {
    }

    record Number(int value) implements DocToken {
    }

    enum Symbol implements DocToken {
        BEGIN_PAREN,
        END_PAREN,
        BEGIN_SQUARE,
        END_SQUARE,
        BEGIN_CURLY,
        END_CURLY,
        BEGIN_ANGLE,
        END_ANGLE,
        BAR,
        COLON,
        COMMA,
        DOT,
        AT,
        BANG,
        AND,
        ARROW,
        HASH,
        LESS,
        GREATER,
        EQUAL,
        CARET,
        QUESTION_MARK,
        STAR,
        TILDE
    }

    record Error(String message, String id, SourceSpan pos) implements DocToken, Diagnostic {
        @Override
        public @Nullable ProgramNode node() {
            return null;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Kind kind() {
            return Kind.ERROR;
        }
    }
}
