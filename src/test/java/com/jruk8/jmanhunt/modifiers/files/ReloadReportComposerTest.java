package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.DuplicateId;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.FailedFile;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.UnknownFile;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadReportComposerTest {

    private static final ReloadWords WORDS = new ReloadWords(
            "<yellow><white>{total}</white> new {items}",
            "<yellow>removed <white>{total}</white> {items}",
            ", ",
            "<yellow>{label}: <white>{filename}</white>{extra}",
            "<yellow>Failed to parse {items}: <white>{filename}</white>{extra}",
            "<yellow>{label}: <white>{id}</white>{extra}",
            "modifier", "modifiers", "preset", "presets", "modifiers/presets",
            "Unknown file", "Unknown files",
            "Duplicate id found", "Duplicate ids found",
            " and <white>{n}</white> more");

    @Test
    void emptyEverythingYieldsNoLines() {
        assertTrue(ReloadReportComposer.compose(emptyDiff(), ModLoadResult.empty(), WORDS).isEmpty());
    }

    @Test
    void newOnlySingularModifier() {
        ReloadDiff diff = new ReloadDiff(List.of("a"), List.of(), List.of(), List.of());

        assertEquals(List.of("<yellow><white>1</white> new modifier"),
                ReloadReportComposer.compose(diff, ModLoadResult.empty(), WORDS));
    }

    @Test
    void newMixedKindsUseBothLabel() {
        ReloadDiff diff = new ReloadDiff(List.of("a", "b"), List.of(), List.of("p"), List.of());

        assertEquals(List.of("<yellow><white>3</white> new modifiers/presets"),
                ReloadReportComposer.compose(diff, ModLoadResult.empty(), WORDS));
    }

    @Test
    void removedOnlyPluralPresets() {
        ReloadDiff diff = new ReloadDiff(List.of(), List.of(), List.of(), List.of("p", "q"));

        assertEquals(List.of("<yellow>removed <white>2</white> presets"),
                ReloadReportComposer.compose(diff, ModLoadResult.empty(), WORDS));
    }

    @Test
    void newPartComesFirst() {
        ReloadDiff diff = new ReloadDiff(List.of("a"), List.of("b"), List.of(), List.of());

        String expected = "<yellow><white>1</white> new modifier"
                + ", <yellow>removed <white>1</white> modifier";
        assertEquals(List.of(expected),
                ReloadReportComposer.compose(diff, ModLoadResult.empty(), WORDS));
    }

    @Test
    void unknownSingularHasNoExtra() {
        ModLoadResult result = withUnknowns(List.of("index.html"));

        assertEquals(List.of("<yellow>Unknown file: <white>index.html</white>"),
                ReloadReportComposer.compose(emptyDiff(), result, WORDS));
    }

    @Test
    void unknownPluralShowsFirstPlusMore() {
        ModLoadResult result = withUnknowns(List.of("a.txt", "b.txt", "c.txt"));

        String expected = "<yellow>Unknown files: <white>a.txt</white> and <white>2</white> more";
        assertEquals(List.of(expected),
                ReloadReportComposer.compose(emptyDiff(), result, WORDS));
    }

    @Test
    void failedUsesItemsAndFirstFilename() {
        ModLoadResult single = withFailed(
                List.of(new FailedFile("x.yml", ModFileKind.MODIFIER, "boom", Path.of("x"))));

        assertEquals(List.of("<yellow>Failed to parse modifier: <white>x.yml</white>"),
                ReloadReportComposer.compose(emptyDiff(), single, WORDS));

        ModLoadResult mixed = withFailed(List.of(
                new FailedFile("m.yml", ModFileKind.MODIFIER, "boom", Path.of("m")),
                new FailedFile("p.yml", ModFileKind.PRESET, "boom", Path.of("p"))));
        String expected = "<yellow>Failed to parse modifiers/presets: <white>m.yml</white>"
                + " and <white>1</white> more";
        assertEquals(List.of(expected),
                ReloadReportComposer.compose(emptyDiff(), mixed, WORDS));
    }

    @Test
    void duplicateUsesFirstIdInLoadOrder() {
        ModLoadResult single = withDuplicates(List.of(
                new DuplicateId("x", ModFileKind.MODIFIER, Path.of("w"), List.of(Path.of("s")))));

        assertEquals(List.of("<yellow>Duplicate id found: <white>x</white>"),
                ReloadReportComposer.compose(emptyDiff(), single, WORDS));

        ModLoadResult pair = withDuplicates(List.of(
                new DuplicateId("x", ModFileKind.MODIFIER, Path.of("w"), List.of(Path.of("s"))),
                new DuplicateId("y", ModFileKind.PRESET, Path.of("w"), List.of(Path.of("s")))));
        String expected = "<yellow>Duplicate ids found: <white>x</white> and <white>1</white> more";
        assertEquals(List.of(expected),
                ReloadReportComposer.compose(emptyDiff(), pair, WORDS));
    }

    @Test
    void fullStackKeepsFixedOrder() {
        ReloadDiff diff = new ReloadDiff(List.of("a"), List.of(), List.of(), List.of());
        ModLoadResult result = new ModLoadResult(Map.of(), Map.of(),
                List.of(new UnknownFile("stray.txt", Path.of("stray"))),
                List.of(new FailedFile("bad.yml", ModFileKind.MODIFIER, "boom", Path.of("bad"))),
                List.of(new DuplicateId("x", ModFileKind.MODIFIER, Path.of("w"),
                        List.of(Path.of("s")))),
                List.of());

        List<String> lines = ReloadReportComposer.compose(diff, result, WORDS);

        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("new modifier"));
        assertTrue(lines.get(1).contains("Unknown file"));
        assertTrue(lines.get(2).contains("Failed to parse"));
        assertTrue(lines.get(3).contains("Duplicate id found"));
    }

    private static ReloadDiff emptyDiff() {
        return new ReloadDiff(List.of(), List.of(), List.of(), List.of());
    }

    private static ModLoadResult withUnknowns(List<String> filenames) {
        List<UnknownFile> unknowns = filenames.stream()
                .map(name -> new UnknownFile(name, Path.of(name)))
                .toList();
        return new ModLoadResult(Map.of(), Map.of(), unknowns, List.of(), List.of(), List.of());
    }

    private static ModLoadResult withFailed(List<FailedFile> failed) {
        return new ModLoadResult(Map.of(), Map.of(), List.of(), failed, List.of(), List.of());
    }

    private static ModLoadResult withDuplicates(List<DuplicateId> duplicates) {
        return new ModLoadResult(Map.of(), Map.of(), List.of(), List.of(), duplicates, List.of());
    }
}
