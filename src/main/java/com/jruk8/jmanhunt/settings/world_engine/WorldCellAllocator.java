package com.jruk8.jmanhunt.settings.world_engine;

import com.jruk8.jmanhunt.StatsRepository;

import java.sql.SQLException;
import java.util.OptionalLong;

public final class WorldCellAllocator {
    private final StatsRepository repository;
    private long fallbackIndex = 0L;

    public WorldCellAllocator(StatsRepository repository) {
        this.repository = repository;
    }

    public OptionalLong reserveStartIndex(int amount) {
        int size = Math.max(0, amount);
        if (size == 0) return OptionalLong.empty();
        if (repository == null) {
            long start = fallbackIndex;
            fallbackIndex += size;
            return OptionalLong.of(start);
        }
        try {
            return OptionalLong.of(repository.consumeWorldCellIndexes(size));
        } catch (SQLException ignored) {
            long start = fallbackIndex;
            fallbackIndex += size;
            return OptionalLong.of(start);
        }
    }
}
