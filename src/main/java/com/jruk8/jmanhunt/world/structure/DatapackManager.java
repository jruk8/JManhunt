package com.jruk8.jmanhunt.world.structure;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.world.FileUtils;
import org.bukkit.Server;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/** Base class for datapacks that override vanilla structure sets. */
public abstract class DatapackManager {
    protected final Server server;
    protected final Path dataFolder;
    protected final JManhuntLogger log;
    protected final Consumer<String> saveResource;

    protected DatapackManager(Server server, Path dataFolder, JManhuntLogger log,
            Consumer<String> saveResource) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.log = log;
        this.saveResource = saveResource;
    }

    public void apply(String worldName, boolean enabled) {
        if (!enabled) {
            return;
        }
        File worldFolder = new File(server.getWorldContainer(), worldName);
        File datapackRoot = new File(worldFolder, "datapacks/" + datapackFolderName());
        File mcMeta = new File(datapackRoot, "pack.mcmeta");
        try {
            writeIfChanged(mcMeta, packMeta());
            boolean changed = false;
            for (Map.Entry<String, String> entry : structureSetFiles().entrySet()) {
                String targetPath = entry.getKey();
                String resource = entry.getValue();
                File structureSet = new File(datapackRoot, targetPath);
                if (structureSet.getParentFile() != null) {
                    structureSet.getParentFile().mkdirs();
                }
                File source = dataFolder.resolve(resource).toFile();
                if (!source.exists()) {
                    saveResource.accept(resource);
                }
                String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
                changed |= writeIfChanged(structureSet, content);
            }
            if (changed) {
                reloadDataPacks();
            }
        } catch (IOException exception) {
            log.warning(
                    "Failed to apply " + datapackFolderName() + " datapack: " + exception.getMessage());
        }
    }

    public void remove(String worldName, boolean enabled) {
        if (enabled) {
            log.warning("Attempted remove datapack with feature enabled. " +
                    "This message should not happen. Contact an admin.");
            return;
        }
        File worldFolder = new File(server.getWorldContainer(), worldName);
        File datapackRoot = new File(worldFolder, "datapacks/" + datapackFolderName());
        if (datapackRoot.exists()) {
            try {
                FileUtils.deleteRecursively(datapackRoot);
                reloadDataPacks();
            } catch (IOException e) {
                log.warning("Failed to delete datapack folder: " + e.getMessage());
            }
        }
    }

    /**
     * Returns map of datapack target path (e.g.
     * {@code data/minecraft/worldgen/structure_set/villages.json}) to plugin
     * resource path (e.g. {@code settings/world-engine/villages.json}).
     * Defaults to the single {@link #structureSetPath()}/{@link #resourcePath()}
     * pair; subclasses managing multiple structure sets may override.
     */
    protected Map<String, String> structureSetFiles() {
        return Map.of(structureSetPath(), resourcePath());
    }

    protected abstract String datapackFolderName();
    protected abstract String structureSetPath();
    protected abstract String resourcePath();
    protected abstract String packDescription();

    private boolean writeIfChanged(File target, String content) throws IOException {
        if (target.exists()) {
            String existing = Files.readString(target.toPath(), StandardCharsets.UTF_8);
            if (existing.equals(content)) {
                return false;
            }
        } else if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        return true;
    }

    private void reloadDataPacks() {
        server.reloadData();
    }

    private String packMeta() {
        return """
                {
                  "pack": {
                    "description": "%s",
                    "min_format": 83,
                    "max_format": 255
                  }
                }
                """.formatted(packDescription());
    }
}