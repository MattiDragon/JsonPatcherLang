package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.event.WorkspaceEventBus;
import dev.mattidragon.jsonpatcher.server.event.context.WorkspaceEventContext;
import dev.mattidragon.jsonpatcher.server.event.workspace.BackgroundIndexUpdateEvent;
import dev.mattidragon.jsonpatcher.server.event.workspace.DocHolderRebuildEvent;
import dev.mattidragon.jsonpatcher.server.index.BackgroundIndex;
import dev.mattidragon.jsonpatcher.server.index.DynamicCombinedIndex;
import dev.mattidragon.jsonpatcher.server.index.Index;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Manages background indexing, which finds usages of symbols.
 * This is a separate pass done after docs indexing, as it relies on type checking
 */
public class BackgroundIndexManager {
    private final DynamicCombinedIndex combinedIndex = new DynamicCombinedIndex();
    private final WorkspaceEventBus eventBus;
    private volatile int revision = 0;

    public BackgroundIndexManager(WorkspaceEventBus eventBus) {
        this.eventBus = eventBus;

        eventBus.listen(DocHolderRebuildEvent.class, this::onDocHolderRebuild);
    }

    private void onDocHolderRebuild(DocHolderRebuildEvent event, WorkspaceEventContext context) {
        int currentRevision;
        synchronized (this) {
            currentRevision = ++revision;
            combinedIndex.clear();
        }
        var futures = context.manager().getDocFileManager().getSourceFiles();
        var docHolder = event.holder();

        var completionFutures = new ArrayList<CompletableFuture<Void>>();
        for (var future : futures) {
            completionFutures.add(future.thenAcceptAsync(file -> {
                if (file == null) return;
                var index = new BackgroundIndex(file.name());
                index.index(file, docHolder.getTypeConverter(), docHolder);
                synchronized (this) {
                    if (revision != currentRevision) return;
                    combinedIndex.addChild(index);
                }
                eventBus.fire(new BackgroundIndexUpdateEvent(combinedIndex, false));
            }, Util.EXECUTOR));
        }

        CompletableFuture.allOf(completionFutures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            synchronized (this) {
                if (revision == currentRevision) {
                    eventBus.fire(new BackgroundIndexUpdateEvent(combinedIndex, true));
                }
            }
        });
    }

    public Index getIndex() {
        return combinedIndex;
    }
}
