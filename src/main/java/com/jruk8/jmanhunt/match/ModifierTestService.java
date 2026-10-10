package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.EngineEscapes;
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
import com.jruk8.jmanhunt.command.TagLocations;
import com.jruk8.jmanhunt.command.TagMath;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * One test run: elapsed ms plus captured warnings and sink output,
     * plus the 1-based failing line and restored exception text when a
     * line threw (both null on a clean run).
     */
    public record TestResult(long elapsedMs, List<String> warnings, List<String> messages,
            List<CapturedSound> sounds, Integer errorLine, String errorText) {
    }

    private final GameStateCommandManager commands;
    private final PlayerStateStore playerStates;
    private final MessageService messages;
    private final ModifiersMessages texts;
    private final SoundService sounds;

    public ModifierTestService(GameStateCommandManager commands, PlayerStateStore playerStates,
            MessageService messages, ModifiersMessages texts, SoundService sounds) {
        this.commands = commands;
        this.playerStates = playerStates;
        this.messages = messages;
        this.texts = texts;
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
        TagContext context = testContext(sender, role, warnings, capturedMessages,
                capturedSounds);
        long start = System.nanoTime();
        try {
            commands.runCommandList(lines, sender, context,
                    TagContext.Provenance.of("modifiers-test", -1, "test"));
        } catch (Throwable thrown) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int line = Math.max(1, context.provenance().lineIndex() + 1);
            return new TestResult(elapsedMs, List.copyOf(warnings),
                    List.copyOf(capturedMessages), List.copyOf(capturedSounds), line,
                    EngineEscapes.restore(String.valueOf(thrown)));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new TestResult(elapsedMs, List.copyOf(warnings), List.copyOf(capturedMessages),
                List.copyOf(capturedSounds), null, null);
    }

    /** Capturing tag context for one test run. */
    private TagContext testContext(Player sender, String role, List<String> warnings,
            List<String> capturedMessages, List<CapturedSound> capturedSounds) {
        ModifierTagScope scope = ModifierTagScope.executor(sender.getName(), warnings::add);
        TagBackends backends = new TagBackends(testStats(), new FlagStore(),
                PlaceholderResolver.inert(), testRoster(sender, role),
                testPlayerSinks(sender.getName(), capturedMessages, capturedSounds));
        return TagContext.run(new TagContext.TagIdentity(scope, "modifiers-test"),
                new TagContext.TagSinks(capturedMessages::add, capturedMessages::add,
                        (id, pitch, volume) -> capturedSounds.add(
                                new CapturedSound(id, pitch, volume)),
                        (id, pitch, volume) -> capturedSounds.add(
                                new CapturedSound(id, pitch, volume)),
                        (line, provenance) -> commands.runTagCommand(line, provenance),
                        target -> capturedMessages.add(
                                "Would teleport everyone to " + target.format())),
                new TagContext.TagRole(
                        (wonRole, text) -> capturedMessages.add(
                                "[" + wonRole + "] " + text),
                        (wonRole, id, pitch, volume) -> capturedSounds.add(
                                new CapturedSound(id, pitch, volume)),
                        (wonRole, target) -> capturedMessages.add(
                                "Would teleport " + wonRole + " to " + target.format())),
                new TagContext.TagMatch(TagContext.NO_MATCH, backends, List.of(),
                        warnings::add,
                        (target, reason) -> capturedMessages.add(
                                "Would eliminate " + target + ": " + reason),
                        (wonRole, reason) -> capturedMessages.add(
                                "Would end the match for " + wonRole + ": " + reason),
                        (target, switchedRole) -> capturedMessages.add(
                                "Would switch " + target + " to " + switchedRole)));
    }

    /**
     * Reports one run to the sender: a throwing line prints the error
     * line plus the failed-in-ms line and stops there; otherwise
     * captured messages replay through the engine format, valid
     * captured sounds play, then the result line. Any warning fails
     * the run with the joined warnings.
     */
    public void report(Player sender, TestResult result) {
        if (result.errorLine() != null) {
            messages.messageRaw(sender, texts.getTestErrorLine(), Map.of("line",
                    String.valueOf(result.errorLine()), "exception", result.errorText()));
            messages.messageRaw(sender, texts.getTestError(),
                    Map.of("time", String.valueOf(result.elapsedMs())));
            return;
        }
        for (String text : result.messages()) {
            messages.sendText(sender,
                    commands.formatEngineMessage(EngineEscapes.restore(text)));
        }
        for (CapturedSound sound : result.sounds()) {
            if (sounds.isValidSound(sound.soundId())) {
                sounds.playCustomSound(sender, sound.soundId(), sound.pitch(), sound.volume());
            }
        }
        if (!result.warnings().isEmpty()) {
            messages.messageRaw(sender, texts.getTestFailure(),
                    Map.of("error", EngineEscapes.restore(
                            String.join("; ", result.warnings()))));
            return;
        }
        messages.messageRaw(sender, texts.getTestSuccess(),
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
        return new TestRoster(sender, sender.getName(), role);
    }

    /**
     * Named-player sinks for dry runs: only the sender resolves (the
     * roster holds nobody else), capturing into the result instead
     * of delivering. Anything else misses so the tags keep their
     * offline warning for genuinely unknown names. Max-health ids
     * live in a run-local map so the tags exercise fully.
     */
    private static PlayerSinks testPlayerSinks(String senderName, List<String> capturedMessages,
            List<CapturedSound> capturedSounds) {
        return new TestPlayerSinks(senderName, capturedMessages, capturedSounds,
                new HashMap<>());
    }

    /** Capturing named-player sinks for one dry run. */
    private record TestPlayerSinks(String senderName, List<String> capturedMessages,
            List<CapturedSound> capturedSounds, Map<String, Double> maxHealth)
            implements PlayerSinks {
        @Override
        public boolean message(String playerName, String text) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            capturedMessages.add("[" + playerName + "] " + text);
            return true;
        }

        @Override
        public boolean sound(String playerName, String soundId, float pitch, float volume) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            capturedSounds.add(new CapturedSound(soundId, pitch, volume));
            return true;
        }

        @Override
        public boolean teleport(String playerName,
                TagLocations.TeleportRequest target) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            capturedMessages.add(
                    "Would teleport " + playerName + " to " + target.format());
            return true;
        }

        @Override
        public boolean title(String playerName, String title, String subtitle,
                double staySeconds, double inSeconds, double outSeconds) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            capturedMessages.add("[" + playerName + "] " + title + " / " + subtitle);
            return true;
        }

        @Override
        public boolean setSlot(String playerName, RosterValues.InventorySlot slot,
                String materialKey, int qty) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            capturedMessages.add("[" + playerName + "] slot " + slot + ": "
                    + materialKey + " x" + qty);
            return true;
        }

        @Override
        public boolean setMaxHealth(String playerName, String id, double amount) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            maxHealth.put(id, amount);
            return true;
        }

        @Override
        public boolean modifyMaxHealth(String playerName, String id, double amount) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            maxHealth.put(id, maxHealth.getOrDefault(id, 0.0) + amount);
            return true;
        }

        @Override
        public Optional<Double> getMaxHealth(String playerName, String id) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return Optional.empty();
            }
            return Optional.of(maxHealth.getOrDefault(id, 0.0));
        }

        @Override
        public boolean clearMaxHealth(String playerName, String idOrNull) {
            if (!playerName.equalsIgnoreCase(senderName)) {
                return false;
            }
            if (idOrNull == null) {
                maxHealth.clear();
            } else {
                maxHealth.remove(idOrNull);
            }
            return true;
        }
    }

    /** Single-sender roster behind dry runs. */
    private record TestRoster(Player sender, String name, String role) implements RosterValues {
        @Override
        public Optional<String> roleOf(String playerName) {
            return playerName.equalsIgnoreCase(name) ? Optional.of(role) : Optional.empty();
        }

        @Override
        public List<String> activePlayers(String roleName) {
            return roleName.equalsIgnoreCase(role) ? List.of(name) : List.of();
        }

        @Override
        public boolean eliminated(String playerName) {
            return !playerName.equalsIgnoreCase(name);
        }

        @Override
        public Optional<Location> locationOf(String playerName) {
            return playerName.equalsIgnoreCase(name)
                    ? Optional.ofNullable(sender.getLocation()) : Optional.empty();
        }

        @Override
        public List<RosterValues.NearbyParticipant> nearbyParticipants() {
            Location spot = sender.getLocation();
            if (spot == null || spot.getWorld() == null) {
                return List.of();
            }
            return List.of(new RosterValues.NearbyParticipant(name, role,
                    spot.getX(), spot.getY(), spot.getZ(),
                    spot.getWorld().getEnvironment().name(), spot.getWorld().getName()));
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

        @Override
        public Optional<String> heldItem(String playerName) {
            if (!playerName.equalsIgnoreCase(name) || sender.getInventory() == null) {
                return Optional.empty();
            }
            ItemStack held = sender.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                return Optional.empty();
            }
            return Optional.of(held.getType().name());
        }

        @Override
        public Optional<Vector> lookDirection(String playerName) {
            if (!playerName.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            Location eye = sender.getEyeLocation();
            if (eye == null) {
                return Optional.empty();
            }
            return Optional.of(eye.getDirection());
        }

        @Override
        public Optional<Boolean> playerState(String playerName, String state) {
            if (!playerName.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            return Optional.of(switch (state) {
                case "SNEAK" -> sender.isSneaking();
                case "SPRINT" -> sender.isSprinting();
                case "GLIDE" -> sender.isGliding();
                case "SWIM" -> sender.isSwimming();
                default -> sender.isOnGround();
            });
        }

        @Override
        public Optional<String> standingOn(String playerName) {
            if (!playerName.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            Location feet = sender.getLocation();
            if (feet == null) {
                return Optional.empty();
            }
            return Optional.of(feet.getBlock().getRelative(BlockFace.DOWN).getType().name());
        }

        @Override
        public Optional<RosterValues.SlotContent> slotItem(String playerName,
                RosterValues.InventorySlot slot) {
            if (!playerName.equalsIgnoreCase(name) || sender.getInventory() == null) {
                return Optional.empty();
            }
            ItemStack stack = NamedPlayerSinks.slotStack(sender.getInventory(), slot);
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                return Optional.empty();
            }
            return Optional.of(new RosterValues.SlotContent(stack.getType().name(),
                    stack.getAmount()));
        }
    }
}
