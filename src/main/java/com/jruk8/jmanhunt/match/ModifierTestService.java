package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.PlaceholderResolver;
import com.jruk8.jmanhunt.command.PlayerSinks;
import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagItems;
import com.jruk8.jmanhunt.command.TagLists;
import com.jruk8.jmanhunt.command.TagMath;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dry-run pipeline for modifier commands: the chat test command and
 * the editor GUI both run lines through the real per-line path with
 * mock stats and capturing sinks. Gameplay lines (give, effect)
 * still dispatch for real through the quiet console dispatch, so
 * tests need a player subject with an inventory and a location.
 */
public final class ModifierTestService {

    /** One captured sound with its pitch and volume. */
    public record CapturedSound(String soundId, float pitch, float volume) {
    }

    /** One test run: elapsed ms plus captured warnings and sink output. */
    public record TestResult(long elapsedMs, List<String> warnings, List<String> messages,
            List<CapturedSound> sounds) {
    }

    private final GameStateCommandManager commands;
    private final PlayerStateStore playerStates;
    private final MessageService messages;
    private final SoundService sounds;

    public ModifierTestService(GameStateCommandManager commands, PlayerStateStore playerStates,
            MessageService messages, SoundService sounds) {
        this.commands = commands;
        this.playerStates = playerStates;
        this.messages = messages;
        this.sounds = sounds;
    }

    /**
     * Splits joined chat input into command lines: bracketed input
     * parses as an array with one layer of quotes stripped per
     * element, anything else runs as one line. Blank input yields no
     * lines.
     */
    public static List<String> parseCommandLines(String joined) {
        if (!TagLists.isList(joined)) {
            return joined.isBlank() ? List.of() : List.of(joined.strip());
        }
        List<String> lines = new ArrayList<>();
        for (String item : TagLists.parse(joined)) {
            String line = TagMath.unquote(item).strip();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Test role for one command list: role-scoped lists use their
     * audience, other lists use the viewer when they participate,
     * else HUNTER.
     */
    public String roleFor(Player viewer, String list) {
        if (list.equalsIgnoreCase("hunter")) {
            return "HUNTER";
        }
        if (list.equalsIgnoreCase("speedrunner")) {
            return "SPEEDRUNNER";
        }
        Role role = playerStates.role(viewer);
        return role.isParticipant() ? role.name() : "HUNTER";
    }

    /**
     * Runs lines for one sender under the given upper-case role with
     * mock stats and capturing sinks. Gameplay lines dispatch for
     * real; eliminate and end-match tags only capture notes.
     */
    public TestResult run(Player sender, String role, List<String> lines) {
        List<String> warnings = new ArrayList<>();
        List<String> capturedMessages = new ArrayList<>();
        List<CapturedSound> capturedSounds = new ArrayList<>();
        ModifierTagScope scope = ModifierTagScope.executor(sender.getName(), warnings::add);
        TagBackends backends = new TagBackends(testStats(), new FlagStore(),
                PlaceholderResolver.inert(), testRoster(sender, role), PlayerSinks.inert());
        TagContext context = TagContext.run(scope, "modifiers-test",
                capturedMessages::add, capturedMessages::add,
                (id, pitch, volume) -> capturedSounds.add(new CapturedSound(id, pitch, volume)),
                (id, pitch, volume) -> capturedSounds.add(new CapturedSound(id, pitch, volume)),
                (target, reason) -> capturedMessages.add("Would eliminate " + target + ": "
                        + reason),
                (wonRole, reason) -> capturedMessages.add("Would end the match for " + wonRole
                        + ": " + reason),
                TagContext.NO_MATCH, backends, List.of(), warnings::add,
                (wonRole, text) -> capturedMessages.add("[" + wonRole + "] " + text),
                (wonRole, id, pitch, volume) -> capturedSounds.add(
                        new CapturedSound(id, pitch, volume)),
                (line, provenance) -> commands.runTagCommand(line, provenance));
        long start = System.nanoTime();
        commands.runCommandList(lines, sender, context,
                TagContext.Provenance.of("modifiers-test", -1, "test"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new TestResult(elapsedMs, List.copyOf(warnings), List.copyOf(capturedMessages),
                List.copyOf(capturedSounds));
    }

    /**
     * Reports one run to the sender: captured messages replay through
     * the engine format, valid captured sounds play, then the result
     * line. Any warning fails the run with the joined warnings.
     */
    public void report(Player sender, TestResult result) {
        for (String text : result.messages()) {
            messages.sendText(sender, commands.formatEngineMessage(text));
        }
        for (CapturedSound sound : result.sounds()) {
            if (sounds.isValidSound(sound.soundId())) {
                sounds.playCustomSound(sender, sound.soundId(), sound.pitch(), sound.volume());
            }
        }
        if (!result.warnings().isEmpty()) {
            messages.message(sender, "modifiers.test-failure",
                    Map.of("error", String.join("; ", result.warnings())));
            return;
        }
        messages.message(sender, "modifiers.test-success",
                Map.of("elapsed", String.valueOf(result.elapsedMs())));
    }

    /** Mock vitals: full health and hunger, fresh counters and clocks. */
    private static StatValues testStats() {
        return new StatValues() {
            @Override
            public Optional<String> player(String playerName, String key) {
                return switch (key) {
                    case "health", "max-health" -> Optional.of("20.0");
                    case "hunger" -> Optional.of("20");
                    case "mobs-killed", "achievements-gained" -> Optional.of("0");
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<String> global(String key) {
                return switch (key) {
                    case "duration", "daytime" -> Optional.of("0");
                    default -> Optional.empty();
                };
            }
        };
    }

    /**
     * Mock roster for one sender: the sender carries the test role,
     * locations and item counts read live from the sender.
     */
    private static RosterValues testRoster(Player sender, String role) {
        String name = sender.getName();
        return new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return playerName.equalsIgnoreCase(name) ? Optional.of(role) : Optional.empty();
            }

            @Override
            public List<String> activePlayers(String roleName) {
                return roleName.equalsIgnoreCase(role) ? List.of(name) : List.of();
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                return playerName.equalsIgnoreCase(name)
                        ? Optional.ofNullable(sender.getLocation()) : Optional.empty();
            }

            @Override
            public Optional<Integer> countItem(String playerName, String materialKey) {
                if (!playerName.equalsIgnoreCase(name) || sender.getInventory() == null) {
                    return Optional.empty();
                }
                Material material =
                        Material.matchMaterial(TagItems.normalizeMaterialKey(materialKey));
                if (material == null) {
                    return Optional.empty();
                }
                ItemStack[] contents = sender.getInventory().getStorageContents();
                if (contents == null) {
                    return Optional.of(0);
                }
                int count = 0;
                for (ItemStack stack : contents) {
                    if (stack != null && stack.getType() == material) {
                        count += stack.getAmount();
                    }
                }
                return Optional.of(count);
            }
        };
    }
}
