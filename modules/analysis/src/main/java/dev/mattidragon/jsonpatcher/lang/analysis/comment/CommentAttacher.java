package dev.mattidragon.jsonpatcher.lang.analysis.comment;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Attaches comments to AST nodes. Must first be used as comment handler before {@link #process} is called.
 */
public class CommentAttacher implements CommentHandler {
    /**
     * Metadata key for attached comments. Attached comments are comments that appear before a program node intended
     * to attach information to it.
     */
    public static final MetadataKey<Block> ATTACHED_COMMENT = new MetadataKey<>("CommentAttacher/ATTACHED_COMMENT");
    /**
     * Metadata key for contained comments. Contained comments are comments that appear within a program node without
     * being to any child node.
     */
    public static final MetadataKey<List<Block>> CONTAINED_COMMENTS = new MetadataKey<>("CommentAttacher/CONTAINED_COMMENTS");

    // Map of owning line to comment block
    private final SequencedMap<Integer, Block> blocks = new LinkedHashMap<>();

    @Override
    public void acceptBlock(List<Comment> comments) {
        var block = new Block(comments);
        var owningRow = comments.getLast().start().row() + 1;
        blocks.put(owningRow, block);
    }

    public void process(Program program, TreeMetadata metadata) {
        for (var child : program.getChildren()) {
            processAttached(child, metadata);
        }

        processContained(program, metadata);

        // In case we missed something add to root
        if (!blocks.isEmpty()) {
            var previousContained = metadata.get(program, CONTAINED_COMMENTS).orElse(List.of());
            var newContained = new ArrayList<Block>(previousContained.size() + blocks.size());
            newContained.addAll(previousContained);
            newContained.addAll(blocks.values());
            metadata.put(program, CONTAINED_COMMENTS, Collections.unmodifiableList(newContained));
        }
    }

    private void processAttached(ProgramNode node, TreeMetadata metadata) {
        metadata.get(node, MetadataKey.FULL_POS)
                .map(SourceSpan::from)
                .map(SourcePos::row)
                .map(blocks::remove) // if missing this returns null and the optional becomes empty
                .ifPresent(block -> metadata.put(node, ATTACHED_COMMENT, block));

        for (var child : node.getChildren()) {
            processAttached(child, metadata);
        }
    }

    private void processContained(Program program, TreeMetadata metadata) {
        enum RelativePosition {
            BEFORE, CONTAINS, AFTER
        }

        record NodeIterator(ProgramNode node, SourceSpan fullPos, Iterator<? extends ProgramNode> iterator) {
            static @Nullable NodeIterator of(ProgramNode node, TreeMetadata metadata) {
                var pos = metadata.get(node, MetadataKey.FULL_POS).orElse(null);
                if (pos == null) return null;
                return new NodeIterator(node, pos, node.getChildren().iterator());
            }

            RelativePosition compare(int row) {
                if (row < fullPos.from().row()) return RelativePosition.BEFORE;
                if (row > fullPos.to().row()) return RelativePosition.AFTER;
                return RelativePosition.CONTAINS;
            }
        }

        var nodeStack = new ArrayDeque<NodeIterator>();
        {
            var iterator = NodeIterator.of(program, metadata);
            if (iterator == null) return;
            nodeStack.push(iterator);
        }

        var processedKeys = new HashSet<Integer>();
        var blockLists = new HashMap<ProgramNode, ArrayList<Block>>();

        main:
        for (var entry : blocks.sequencedEntrySet()) {
            var lastRow = entry.getKey() - 1;
            var block = entry.getValue();

            walker:
            while (!nodeStack.isEmpty()) {
                switch (nodeStack.peek().compare(lastRow)) {
                    // Comment is after node, continue on
                    case AFTER -> nodeStack.pop();
                    // Node contains comment, enter child or pick this
                    case CONTAINS -> {
                        var containing = Objects.requireNonNull(nodeStack.peek(), "This should already have been checked");
                        var iterator = containing.iterator;

                        // If we have children, we push the next one and loop again
                        // If we don't, we're at the innermost node
                        if (iterator.hasNext()) {
                            var nodeIterator = NodeIterator.of(iterator.next(), metadata);
                            if (nodeIterator == null) continue;
                            nodeStack.push(nodeIterator);
                        } else {
                            blockLists.computeIfAbsent(containing.node, n -> new ArrayList<>())
                                    .add(block);
                            processedKeys.add(entry.getKey());
                            continue main;
                        }
                    }
                    // Comment is before the current node, but inside the parent since we managed to get here
                    // so we attach to parent
                    case BEFORE -> {
                        var incorrectNode = nodeStack.pop();
                        var correctNode = nodeStack.peek();
                        if (correctNode == null) {
                            // We're somehow before the root node, so we give up
                            break walker;
                        }
                        if (correctNode.compare(lastRow) != RelativePosition.CONTAINS) {
                            throw new IllegalStateException("Misordered comments");
                        }
                        blockLists.computeIfAbsent(correctNode.node, n -> new ArrayList<>())
                                        .add(block);
                        processedKeys.add(entry.getKey());
                        nodeStack.push(incorrectNode);
                        continue main;
                    }
                }
            }
            // If we end up here, we're outside the root node so we attach to it anyway
            blockLists.computeIfAbsent(program, n -> new ArrayList<>())
                    .add(block);
            processedKeys.add(entry.getKey());
        }

        for (var entry : blockLists.entrySet()) {
            var node = entry.getKey();
            var blocks = entry.getValue();
            metadata.put(node, CONTAINED_COMMENTS, Collections.unmodifiableList(blocks));
        }

        for (var processedKey : processedKeys) {
            blocks.remove(processedKey);
        }
    }

    public record Block(List<Comment> comments) {
    }
}
