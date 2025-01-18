package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

public class DocParseException extends RuntimeException {
    private final DocParseError error;

    public DocParseException(String message, SourceSpan pos, DocParseError.Code code) {
        super(message);
        this.error = new DocParseError(pos, message, code);
    }

    public DocParseException(String message, SourcePos pos, DocParseError.Code code) {
        this(message, new SourceSpan(pos, pos), code);
    }

    public DocParseError error() {
        return error;
    }
}
