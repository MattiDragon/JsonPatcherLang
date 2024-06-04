package io.github.mattidragon.jsonpatcher.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Util {
    public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Util() {}
    
    @SafeVarargs
    public static <T> CompletableFuture<List<T>> combineLists(CompletableFuture<? extends List<? extends T>>... futures) {
        var marker = CompletableFuture.allOf(futures);
        return marker.thenApply(unit -> {
            var list = new ArrayList<T>();
            for (var future : futures) {
                list.addAll(future.join());
            }
            return list;
        });
    }
}
