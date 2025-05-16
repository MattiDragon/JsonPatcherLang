package dev.mattidragon.jsonpatcher.lang.error;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

/**
 * Represents a general diagnostic in any step of the toolchain.
 */
public interface Diagnostic {
    /**
     * Diagnostics should almost always come with a position,
     * but in some cases one might not be available.
     * In such cases this method can return {@code null}
     *
     * @return The position this diagnostic happened at if available
     */
    @Nullable
    SourceSpan pos();

    /**
     * Most diagnostics during and after the parser correspond to nodes in the program tree.
     * This method can be used to retrieve the node.
     *
     * @return A program node related to the diagnostic if available
     */
    @Nullable
    ProgramNode node();

    /**
     * @return An exception relating to the diagnostic, if applicable.
     */
    default @Nullable RuntimeException exception() {
        return null;
    }

    /**
     * Provides a human-readable message describing the diagnostic
     */
    String message();

    default String toDisplay() {
        var pos = pos();

        var codeHighlight = buildHighlight(pos);

        return """
                %s %s: %s
                | at %s in %s%s
                """.formatted(
                kind(),
                id(),
                message(),
                pos == null ? "<unknown>" : pos.format(),
                pos == null ? "<unknown>" : pos.from().file().name(),
                codeHighlight
        );
    }

    private static StringBuilder buildHighlight(@Nullable SourceSpan pos) {
        var codeHighlight = new StringBuilder();
        if (pos != null && pos.from().row() == pos.to().row()) {
            var from = pos.from();
            var to = pos.to();

            codeHighlight.append("\n| ");
            var row = from.row();
            var file = from.file();
            var rowBegin = file.findRow(row);
            var rowEnd = file.findRow(row + 1);
            if (rowEnd == -1) rowEnd = file.code().length() + 1;

            codeHighlight.append(file.code()
                    .substring(rowBegin, rowEnd - 1)
                    .replace("\t", "    "));
            codeHighlight.append("\n| ");
            codeHighlight.append(" ".repeat(from.column() - 1));
            codeHighlight.append("^".repeat(to.column() - from.column() + 1));
            codeHighlight.append(" here");
        }
        return codeHighlight;
    }

    /**
     * Returns a diagnostic id, usually in the form {@code STEP-XX},
     * where {@code STEP} is a short string for step in the toolchain
     * and {@code XX} is a number corresponding to the exact type of error.
     * Other formats are allowed and should be gracefully handled.
     */
    String id();

    /**
     * Returns the kind of the diagnostic
     * @see Kind
     */
    Kind kind();

    /**
     * Represents a diagnostic kind.
     */
    enum Kind {
        /**
         * Represents and internal error in a tool.
         * Internal errors should be displayed to users to help with debugging,
         * but probably aren't their fault.
         */
        INTERNAL_ERROR,
        /**
         * Errors are issues with the user code which prevented some step from completing normally.
         * Other steps may still be attempted for additional diagnostics,
         * but code with an error should never run.
         */
        ERROR,
        /**
         * A warning represents a potential issue with use code that won't prevent it from running,
         * but is still likely to cause issues.
         */
        WARNING,
        /**
         * Unused is a special case of {@link #WARNING} for unused members,
         * as they often require different display.
         */
        UNUSED
    }
}
