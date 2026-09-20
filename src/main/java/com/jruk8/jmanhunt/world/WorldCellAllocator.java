package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.config.EngineStateRepository;
import java.sql.SQLException;
import java.util.OptionalLong;

public final class WorldCellAllocator {
    private final EngineStateRepository repository;
    private long fallbackIndex = 0L;

    public WorldCellAllocator(EngineStateRepository repository) {
        this.repository = repository;
    }

    public OptionalLong currentStartIndex() {
        if (repository == null) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(repository.getWorldCellIndex());
        } catch (SQLException ignored) {
            return OptionalLong.empty();
        }
    }

    public boolean setStartIndex(long value) {
        if (repository == null) {
            return false;
        }
        try {
            repository.setWorldCellIndex(value);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public OptionalLong reserveStartIndex(int amount) {
        int size = Math.max(0, amount);
        if (size == 0) {
            return OptionalLong.empty();
        }
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
