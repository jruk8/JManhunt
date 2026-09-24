package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.lobby.AnnounceMode;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Lobby queues. */
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
            "",
            "Default: SUBLOBBY"
    })
    private MidMatchPolicy midMatchSetplayer = MidMatchPolicy.SUBLOBBY;

    @CustomKey("queue-caps")
    @Comment({
            "Per-role queue caps for each lobby. Only enforced on the queue before a",
            "match starts, and bypassed with -force. Lowering a cap below the current",
            "queue blocks new entries of that role but never kicks queued players."
    })
    private QueueCaps queueCaps = new QueueCaps();

    public int getDefaultLobbyId() {
        return defaultLobbyId;
    }

    public void setDefaultLobbyId(int defaultLobbyId) {
        this.defaultLobbyId = defaultLobbyId;
    }

    public boolean isJoinTeleportsToLobby() {
        return joinTeleportsToLobby;
    }

    public void setJoinTeleportsToLobby(boolean joinTeleportsToLobby) {
        this.joinTeleportsToLobby = joinTeleportsToLobby;
    }

    public AnnounceMode getAnnounceLobbyChanges() {
        return announceLobbyChanges;
    }

    public void setAnnounceLobbyChanges(AnnounceMode announceLobbyChanges) {
        this.announceLobbyChanges = announceLobbyChanges;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }

    public MidMatchPolicy getMidMatchSetplayer() {
        return midMatchSetplayer;
    }

    public void setMidMatchSetplayer(MidMatchPolicy midMatchSetplayer) {
        this.midMatchSetplayer = midMatchSetplayer;
    }

    public QueueCaps getQueueCaps() {
        return queueCaps;
    }

    public void setQueueCaps(QueueCaps queueCaps) {
        this.queueCaps = queueCaps;
    }

    /** Lobby bounds exit routing. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Bounds extends OkaeriConfig {
        @CustomKey("exit-lobby-id")
        private int exitLobbyId = -1;

        public int getExitLobbyId() {
            return exitLobbyId;
        }

        public void setExitLobbyId(int exitLobbyId) {
            this.exitLobbyId = exitLobbyId;
        }
    }

    /** Per-role queue caps. */
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

        public int getSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(int speedrunner) {
            this.speedrunner = speedrunner;
        }

        public int getHunter() {
            return hunter;
        }

        public void setHunter(int hunter) {
            this.hunter = hunter;
        }
    }
}