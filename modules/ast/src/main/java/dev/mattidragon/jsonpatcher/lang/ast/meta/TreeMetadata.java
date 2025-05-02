package dev.mattidragon.jsonpatcher.lang.ast.meta;

import java.util.*;

public class TreeMetadata {
    private final Map<MetadataHolder, Map<MetadataKey<?>, Object>> values = new IdentityHashMap<>();
    
    public <T> void put(MetadataHolder node, MetadataKey<T> key, T value) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        
        values.computeIfAbsent(node, __ -> new HashMap<>())
                .put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(MetadataHolder node, MetadataKey<T> key) {
        var nodeMap = values.get(node);
        if (nodeMap == null) return Optional.empty();
        
        return Optional.ofNullable((T) nodeMap.get(key))
                .or(() -> {
                    for (var parent : key.getParents()) {
                        var value = get(node, parent);
                        if (value.isPresent()) {
                            return value;
                        }
                    }
                    return Optional.empty();
                });
    }

    public Map<MetadataKey<?>, Object> getAll(MetadataHolder node) {
        var nodeMap = values.get(node);
        if (nodeMap == null) return Map.of();

        return Map.copyOf(nodeMap);
    }

    /**
     * Copies a key from one node to another.
     * This method is different from a simple implementation using {@link #get} and {@link #put} 
     * because it copies the root key providing the value instead of simply coping the value outright.
     * @param from The node to copy the key from.
     * @param to The node to copy the key to.
     * @param key The key to copy.
     */
    public void copy(MetadataHolder from, MetadataHolder to, MetadataKey<?> key) {
        var nodeMap = values.get(from);
        if (nodeMap == null) return;

        MetadataKey<?> chosenKey;
        var keyStack = new ArrayDeque<MetadataKey<?>>();
        keyStack.addLast(key);
        do {
            if (keyStack.isEmpty()) return;
            chosenKey = keyStack.removeLast();
            if (nodeMap.containsKey(chosenKey)) break;
            keyStack.addAll(chosenKey.getParents());
        } while (true);
        
        values.computeIfAbsent(to, __ -> new HashMap<>())
                .put(chosenKey, nodeMap.get(chosenKey));
    }
}
