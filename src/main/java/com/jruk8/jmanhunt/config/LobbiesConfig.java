package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.lobby.AnnounceMode;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Lobby queues. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class LobbiesConfig extends OkaeriConfig {

    @CustomKey("default-lobby-id")
    @Comment({
            "Lobby id assigned on join. Use -1 (or any negative value) to leave players",
            "without a lobby until they join one manually.",
            "Default: 0"
    })
    private int defaultLobbyId = 0;

    @CustomKey("join-teleports-to-lobby")
    @Comment({
            "When true, joining a lobby also teleports the player to that lobby's",
            "location. When no location exists for the lobby, the player is told to",
            "create one instead of being teleported. The -notp join flag skips the",
            "teleport for one invocation.",
            "Default: true"
    })
    private boolean joinTeleportsToLobby = true;

    @CustomKey("announce-lobby-changes")
    @Comment({
            "Who hears lobby leave and join lines. Only positive lobbies announce,",
            "and only to the subject plus members of the affected lobbies.",
            "- ALL tells the subject and the other members",
            "- SELF tells only the subject",
            "- MEMBERS tells only the other members",
            "- NONE stays silent",
            "Default: ALL"
    })
    private AnnounceMode announceLobbyChanges = AnnounceMode.ALL;

    @Comment({
            "Where lobby members go when they leave their lobby's bounds box.",
            "Only fires for members whose lobby has complete bounds, and never",
            "for players in a live match. Use -1 (or any negative value) to",
            "leave them without a lobby.",
            "Default: -1"
    })
    private Bounds bounds = new Bounds();

    @CustomKey("mid-match-setplayer")
    @Comment({
            "What /manhunt setplayer does when the target's lobby has a live match.",
            "Only applies with the world engine on; otherwise the in-match block",
            "stays since only one match can run without the engine.",
            "- HOLD queues roles for the next game",
            "- JOIN_ANY joins any role mid-match",
            "- JOIN_SPECTATORS joins spectators and holds everyone else,",
            "- SUBLOBBY queues roles for the next sublobby (theoretically infinite matches",
            "on one lobby).",
            "- SUBLOBBY_WITH_SPECTATORS queues like SUBLOBBY, but spectators always",
            "join the oldest running sublobby.",
            "",
            "Default: SUBLOBBY_WITH_SPECTATORS"
    })
    private MidMatchPolicy midMatchSetplayer = MidMatchPolicy.SUBLOBBY_WITH_SPECTATORS;

    @CustomKey("queue-caps")
    @Comment({
            "Per-role queue caps for each lobby. Only enforced on the queue before a",
            "match starts, and bypassed with -force. Lowering a cap below the current",
            "queue blocks new entries of that role but never kicks queued players."
    })
    private QueueCaps queueCaps = new QueueCaps();

    @CustomKey("lobby-world-name")
    @Comment({
            "Name of the lobby world used by /manhunt worldengine tpto lobbyworld.",
            "When no world with this name exists, the plugin generates a void world",
            "filled by the tpto preset (DEFAULT unless the command names one).",
            "Point this at your own world to use it instead.",
            "Default: jmh_lobby"
    })
    private String lobbyWorldName = "jmh_lobby";

    @CustomKey("disable-player-collisions")
    @Comment({
            "When true, lobby members pass through each other instead of",
            "colliding. Match members always collide; players outside any",
            "lobby are never affected.",
            "Default: true"
    })
    private boolean disablePlayerCollisions = true;

    /** Lobby bounds exit routing. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Bounds extends OkaeriConfig {
        @CustomKey("exit-lobby-id")
        private int exitLobbyId = -1;

        @CustomKey("exit-behavior")
        @Comment({
                "What walking out of every bounds box does: KEEP_IN_LOBBY",
                "keeps the lobby membership, EXIT_LOBBY leaves the lobby",
                "for exit-lobby-id (unless the destination lands straight",
                "inside another lobby's box).",
                "Default: KEEP_IN_LOBBY"
        })
        private String exitBehavior = "KEEP_IN_LOBBY";

    }

    /** Per-role queue caps. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class QueueCaps extends OkaeriConfig {
        @Comment({
                "Max queued speedrunners per lobby. -1 means no limit. Minimum: 1.",
                "Default: -1"
        })
        private int speedrunner = -1;

        @Comment({
                "Max queued hunters per lobby. -1 means no limit. Minimum: 1.",
                "Default: -1"
        })
        private int hunter = -1;

    }

    @CustomKey("role-pads")
    @Comment({
            "Stand-on role pads. A player in the lobby world inside a listed block's",
            "XZ cell and at most 4 blocks above it is assigned the mapped role,",
            "like setplayer. Material names are Bukkit Material names.",
            "Default: true",
            "Lobby-world blocks that assign roles when stood on."
    })
    private RolePads rolePads = new RolePads();

    /** Stand-on role pads. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class RolePads extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("silent-role-assignment")
        @Comment({
                "When true, pads assign roles quietly: no role message, no sound,",
                "and no role-change announcement. Teleports still happen.",
                "Default: false"
        })
        private boolean silentRoleAssignment = false;

        @CustomKey("blocks")
        @Comment("Concrete block per role.")
        private RolePadBlocks blocks = new RolePadBlocks();

        /** Block per role, as Bukkit Material names. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class RolePadBlocks extends OkaeriConfig {
            private String speedrunner = "LIME_CONCRETE";
            private String hunter = "RED_CONCRETE";
            private String afk = "YELLOW_CONCRETE";
            private String spectator = "LIGHT_GRAY_CONCRETE";
            private String none = "GRAY_CONCRETE";

        }
    }

}
