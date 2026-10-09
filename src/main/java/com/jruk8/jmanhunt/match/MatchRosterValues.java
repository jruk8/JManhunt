package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.command.TagItems;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Live roster reads for one match: assignee roles, INTERVAL-eligible
 * names per role, and online locations. Tags call it through
 * {@link RosterValues}; all Bukkit reads stay here.
 */
public final class MatchRosterValues implements RosterValues {
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakeSpectators;
    private final long matchId;

    public MatchRosterValues(GameManager game, PlayerStateStore playerStates,
            FakeSpectatorService fakeSpectators, long matchId) {
        this.game = game;
        this.playerStates = playerStates;
        this.fakeSpectators = fakeSpectators;
        this.matchId = matchId;
    }

    @Override
    public Optional<String> roleOf(String playerName) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        return assignedId(match.get(), playerName).map(playerStates::role).map(Role::name);
    }

    @Override
    public boolean eliminated(String playerName) {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return true;
        }
        return assignedId(match.get(), playerName)
                .map(id -> !match.get().isActive(id)).orElse(true);
    }

    /** Assigned id behind a case-blind name, online or offline. */
    private static Optional<UUID> assignedId(GameInstance match, String playerName) {
        for (UUID id : match.assignedPlayerIds()) {
            Player online = Bukkit.getPlayer(id);
            String name = online != null ? online.getName()
                    : Bukkit.getOfflinePlayer(id).getName();
            if (name != null && name.equalsIgnoreCase(playerName)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<String> activePlayers(String role) {
        Role want;
        try {
            want = Role.valueOf(role);
        } catch (IllegalArgumentException unmatched) {
            return List.of();
        }
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID id : match.get().assignedPlayerIds()) {
            if (playerStates.role(id) != want) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (IntervalDispatcher.intervalSkipWhy(playerStates.role(player),
                    game.isActiveInInstance(matchId, id),
                    player.isDead(), fakeSpectators.isFakeSpectator(player)).isPresent()) {
                continue;
            }
            names.add(player.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Optional<Location> locationOf(String playerName) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        return found == null ? Optional.empty() : Optional.of(found.getLocation());
    }

    @Override
    public List<RosterValues.NearbyParticipant> nearbyParticipants() {
        Optional<GameInstance> match = game.instance(matchId);
        if (match.isEmpty()) {
            return List.of();
        }
        List<RosterValues.NearbyParticipant> entries = new ArrayList<>();
        for (UUID id : match.get().assignedPlayerIds()) {
            Role role = playerStates.role(id);
            if (role != Role.HUNTER && role != Role.SPEEDRUNNER) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null || player.getLocation().getWorld() == null) {
                continue;
            }
            if (IntervalDispatcher.intervalSkipWhy(playerStates.role(player),
                    game.isActiveInInstance(matchId, id),
                    player.isDead(), fakeSpectators.isFakeSpectator(player)).isPresent()) {
                continue;
            }
            Location spot = player.getLocation();
            entries.add(new RosterValues.NearbyParticipant(player.getName(), role.name(),
                    spot.getX(), spot.getY(), spot.getZ(),
                    spot.getWorld().getEnvironment().name(), spot.getWorld().getName()));
        }
        return entries;
    }

    @Override
    public Optional<Integer> countItem(String playerName, String materialKey) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(TagItems.normalizeMaterialKey(materialKey));
        if (material == null) {
            return Optional.empty();
        }
        int count = 0;
        for (ItemStack stack : found.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return Optional.of(count);
    }

    @Override
    public Optional<String> heldItem(String playerName) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null || found.getInventory() == null) {
            return Optional.empty();
        }
        ItemStack held = found.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            return Optional.empty();
        }
        return Optional.of(held.getType().name());
    }

    @Override
    public Optional<Vector> lookDirection(String playerName) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null) {
            return Optional.empty();
        }
        Location eye = found.getEyeLocation();
        if (eye == null) {
            return Optional.empty();
        }
        return Optional.of(eye.getDirection());
    }

    @Override
    public Optional<Boolean> playerState(String playerName, String state) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null) {
            return Optional.empty();
        }
        return Optional.of(switch (state) {
            case "SNEAK" -> found.isSneaking();
            case "SPRINT" -> found.isSprinting();
            case "GLIDE" -> found.isGliding();
            case "SWIM" -> found.isSwimming();
            default -> found.isOnGround();
        });
    }

    @Override
    public Optional<String> standingOn(String playerName) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null) {
            return Optional.empty();
        }
        Location feet = found.getLocation();
        if (feet == null) {
            return Optional.empty();
        }
        return Optional.of(feet.getBlock().getRelative(BlockFace.DOWN).getType().name());
    }

    @Override
    public Optional<RosterValues.SlotContent> slotItem(String playerName,
            RosterValues.InventorySlot slot) {
        Player found = NamedPlayerSinks.onlinePlayer(playerName);
        if (found == null || found.getInventory() == null) {
            return Optional.empty();
        }
        ItemStack stack = NamedPlayerSinks.slotStack(found.getInventory(), slot);
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RosterValues.SlotContent(stack.getType().name(),
                stack.getAmount()));
    }
}
