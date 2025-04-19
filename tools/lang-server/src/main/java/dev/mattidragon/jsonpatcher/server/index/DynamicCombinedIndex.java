package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Thread safe index combining multiple children into one.
 * Uses a read-write lock and copies a list for every read call.
 * @see StaticCombinedIndex
 */
public final class DynamicCombinedIndex implements Index {
    private final Set<Index> children = new HashSet<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public void addChild(Index child) {
        writeLock.lock();
        try {
            children.add(child);
        } finally {
            writeLock.unlock();
        }
    }

    public void removeChild(Index child) {
        writeLock.lock();
        try {
            children.remove(child);
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            children.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Stream<SourceSpan> find(IndexEntry entry) {
        readLock.lock();
        try {
            return children.stream()
                    .flatMap(index -> index.find(entry))
                    .distinct()
                    .toList()
                    .stream();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Stream<IndexEntry> lookupEntries(SourcePos pos) {
        readLock.lock();
        try {
            return children.stream()
                    .flatMap(index -> index.lookupEntries(pos))
                    .distinct()
                    .toList()
                    .stream();
        } finally {
            readLock.unlock();
        }
    }
}
