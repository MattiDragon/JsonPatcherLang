package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class PosLookup<T> {
    private final Map<Integer, List<Entry<T>>> entries = new HashMap<>();
    private final Map<T, List<SourceSpan>> positions = new HashMap<>();
    
    public void add(SourceSpan pos, T value) {
        if (pos.from().row() != pos.to().row()) throw new IllegalStateException("Multiline elements not allowed in pos lookup");
        entries.computeIfAbsent(pos.from().row(), row -> new ArrayList<>())
                .add(new Entry<>(pos.from().column(), pos.to().column(), value));
        positions.computeIfAbsent(value, __ -> new ArrayList<>())
                .add(pos);
    }
    
    public List<SourceSpan> getPositions(T value) {
        return Collections.unmodifiableList(positions.getOrDefault(value, List.of()));
    }
    
    @Nullable
    public T getFirstAt(SourcePos pos) {
        var entries = this.entries.get(pos.row());
        if (entries == null) return null;

        for (var entry : entries) {
            if (pos.column() >= entry.from && pos.column() <= entry.to) {
                return entry.val;
            }
        }
        
        return null;
    }
    
    public Stream<T> getAllAt(SourcePos pos) {
        var entries = this.entries.get(pos.row());
        if (entries == null) return Stream.empty();
        return entries.stream()
                .filter(entry -> pos.column() >= entry.from && pos.column() <= entry.to)
                .map(Entry::val);
    }
    
    private record Entry<T>(int from, int to, T val) {}
}
