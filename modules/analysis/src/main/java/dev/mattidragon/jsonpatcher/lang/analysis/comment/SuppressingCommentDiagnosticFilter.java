package dev.mattidragon.jsonpatcher.lang.analysis.comment;

import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticFilter;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A {@link DiagnosticFilter} that filters diagnostics based on comments in the source code.
 * Must be used as a {@link CommentHandler} before use as a filter.
 */
public class SuppressingCommentDiagnosticFilter implements DiagnosticFilter, CommentHandler {
    private static final Pattern PATTERN = Pattern.compile(
            "@@suppress +(?<diagnostic>[\\w-]+)(?: +(?:(?<lines>\\d)+ lines?|(?<file>file)|(?<beginRegion>begin)|(?<endRegion>end)))?.*"
    );

    private final Set<String> globallyDisabledInspections = new HashSet<>();
    private final Map<Integer, Set<String>> disabledInspections = new HashMap<>();

    private final Map<String, List<Integer>> activeRegions = new HashMap<>();

    @Override
    public void acceptBlock(List<Comment> comments) {
        for (var comment : comments) {
            var matcher = PATTERN.matcher(comment.text().strip());
            if (!matcher.matches()) continue;
            var diagnostic = matcher.group("diagnostic");
            var startLine = comment.start().row() + 1;

            if (matcher.group("lines") instanceof String lines) {
                var lineCount = Integer.parseInt(lines);
                for (int i = 0; i < lineCount; i++) {
                    disabledInspections.computeIfAbsent(startLine + i, l -> new HashSet<>())
                            .add(diagnostic);
                }
            } else if (matcher.group("file") != null) {
                globallyDisabledInspections.add(diagnostic);
            } else if (matcher.group("beginRegion") != null) {
                activeRegions.computeIfAbsent(diagnostic, d -> new ArrayList<>())
                        .add(startLine);
            } else if (matcher.group("endRegion") != null) {
                var list = activeRegions.get(diagnostic);
                if (list == null || list.isEmpty()) continue;
                var actualStartLine = (int) list.removeLast();
                var lineCount = startLine - actualStartLine;
                for (int i = 0; i < lineCount; i++) {
                    disabledInspections.computeIfAbsent(actualStartLine + i, l -> new HashSet<>())
                            .add(diagnostic);
                }
            } else {
                disabledInspections.computeIfAbsent(startLine, l -> new HashSet<>())
                        .add(diagnostic);
            }
        }
    }

    @Override
    public boolean shouldBlock(Diagnostic diagnostic) {
        var id = diagnostic.id();
        if (globallyDisabledInspections.contains(id)) {
            return true;
        }

        var pos = diagnostic.pos();
        if (pos == null) return false;
        var line = pos.from().row();

        return disabledInspections.getOrDefault(line, Set.of()).contains(id);
    }
}
