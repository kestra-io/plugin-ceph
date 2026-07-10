package io.kestra.plugin.ceph;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared {@link FetchType} handling for the plugin's List tasks. {@code STORE} writes the listing to
 * Kestra internal storage as Ion instead of returning it inline, keeping large listings out of the
 * execution's outputs on big clusters.
 */
public final class CephFetch {

    private CephFetch() {
    }

    public static <T> void apply(
        RunContext runContext,
        FetchType fetchType,
        List<T> items,
        Consumer<List<T>> onFetch,
        Consumer<URI> onStore
    ) throws IOException {
        switch (fetchType) {
            case STORE -> {
                onFetch.accept(List.of());
                onStore.accept(store(runContext, items));
            }
            case FETCH_ONE -> {
                onFetch.accept(items.isEmpty() ? List.of() : List.of(items.getFirst()));
                onStore.accept(null);
            }
            case NONE -> {
                onFetch.accept(List.of());
                onStore.accept(null);
            }
            default -> {
                onFetch.accept(items);
                onStore.accept(null);
            }
        }
    }

    private static <T> URI store(RunContext runContext, List<T> items) throws IOException {
        var tempFile = runContext.workingDir().createTempFile(".ion");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            FileSerde.writeAll(writer, Flux.fromIterable(items)).block();
        }
        return runContext.storage().putFile(tempFile.toFile());
    }
}
