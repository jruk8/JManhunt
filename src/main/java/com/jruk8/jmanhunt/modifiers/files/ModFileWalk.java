package com.jruk8.jmanhunt.modifiers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Recursive load-order traversal of one mods dir. At every level,
 * subdirectories come first (natural-sorted, each processed with
 * this same rule), then the directory's own files natural-sorted,
 * so root files load after all subdirs. Dotfiles and temp files
 * are skipped silently; symlinked directories are never followed.
 */
public final class ModFileWalk {

    private ModFileWalk() {
    }

    /**
     * Load-ordered regular files under {@code root}, deepest
     * subdirs first. Directories themselves are not returned.
     */
    public static List<Path> walk(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        collect(root, files);
        return files;
    }

    private static void collect(Path dir, List<Path> files) throws IOException {
        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream.toList();
        }
        List<Path> subdirs = new ArrayList<>();
        List<Path> own = new ArrayList<>();
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (ignored(name)) {
                continue;
            }
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                subdirs.add(child);
            } else if (Files.isRegularFile(child)) {
                own.add(child);
            }
        }
        subdirs.sort(byName());
        own.sort(byName());
        for (Path subdir : subdirs) {
            collect(subdir, files);
        }
        files.addAll(own);
    }

    private static Comparator<Path> byName() {
        return Comparator.comparing(path -> path.getFileName().toString(), NaturalOrder.comparator());
    }

    private static boolean ignored(String name) {
        return name.startsWith(".") || name.toLowerCase(Locale.ROOT).endsWith(".tmp");
    }
}
