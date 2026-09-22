package com.jruk8.jmanhunt.lobby.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lobby teleport points and boundary boxes, stored per lobby id. The world
 * is never stored here: every lobbytp resolves in the configured lobby
 * world at teleport time. Generated with defaults on first load and
 * reloaded with /manhunt reload; not editable through
 * /manhunt configuration (use the lobbyconfig commands instead).
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt lobby teleport points and boundary boxes.",
        "",
        "Each lobby id maps to a lobbytp (where its players land) and an",
        "optional bounds box (which auto-joins walkers to that lobby).",
        "Manage both in-game: /manhunt worldengine lobbyconfig",
        "setlobbytp|setbounds|deletelobby <id>.",
        ""
})
public class LobbyConfig extends OkaeriConfig {

    @Comment({
            "Lobbies by id. Unknown ids are created by the worldengine",
            "commands as needed; entries without a lobbytp never teleport",
            "anyone, and entries without bounds never auto-join anyone."
    })
    private Map<String, LobbyEntry> lobbies = defaultLobbies();

    @Comment({
            "Lobby-world upkeep: arrivals are healed and fed at once, and",
            "everyone inside is topped up every interval seconds."
    })
    private CareData care = new CareData();

    public Map<String, LobbyEntry> getLobbies() {
        return lobbies;
    }

    public void setLobbies(Map<String, LobbyEntry> lobbies) {
        this.lobbies = lobbies;
    }

    public CareData getCare() {
        return care;
    }

    public void setCare(CareData care) {
        this.care = care;
    }

    private static Map<String, LobbyEntry> defaultLobbies() {
        LobbyEntry zero = new LobbyEntry();
        zero.setLobbytp(LobbyTp.of(0.0, 65.0, 0.0, 0.0f, 0.0f));
        Map<String, LobbyEntry> lobbies = new LinkedHashMap<>();
        lobbies.put("0", zero);
        return lobbies;
    }

    /** One lobby's teleport point plus its optional boundary box. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class LobbyEntry extends OkaeriConfig {

        @Comment({
                "Where this lobby's players land. Coordinates only: the",
                "world is always the configured lobby world. Unset until",
                "lobbyconfig setlobbytp <id> runs (lobby 0 ships",
                "with a default)."
        })
        private LobbyTp lobbytp;

        @Comment({
                "Boundary box that auto-joins walkers to this lobby with",
                "role none. Empty until lobbyconfig setbounds <id> runs;",
                "both corners are required for the box to apply."
        })
        private BoundsData bounds = new BoundsData();

        public LobbyTp getLobbytp() {
            return lobbytp;
        }

        public void setLobbytp(LobbyTp lobbytp) {
            this.lobbytp = lobbytp;
        }

        public BoundsData getBounds() {
            return bounds;
        }

        public void setBounds(BoundsData bounds) {
            this.bounds = bounds;
        }
    }

    /** A lobby teleport point: coordinates plus look direction. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class LobbyTp extends OkaeriConfig {

        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public static LobbyTp of(double x, double y, double z, float yaw, float pitch) {
            LobbyTp point = new LobbyTp();
            point.setX(x);
            point.setY(y);
            point.setZ(z);
            point.setYaw(yaw);
            point.setPitch(pitch);
            return point;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }
    }

    /** A lobby boundary box: two opposite corners, block coordinates. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class BoundsData extends OkaeriConfig {

        private Position pos1;
        private Position pos2;

        public Position getPos1() {
            return pos1;
        }

        public void setPos1(Position pos1) {
            this.pos1 = pos1;
        }

        public Position getPos2() {
            return pos2;
        }

        public void setPos2(Position pos2) {
            this.pos2 = pos2;
        }
    }

    /** Lobby-world upkeep: instant heal and feed toggles plus the repeat. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class CareData extends OkaeriConfig {

        @Comment("Restore full health on arrival and on every repeat.")
        private CareToggle heal = new CareToggle();

        @Comment("Restore full hunger on arrival and on every repeat.")
        private CareToggle saturate = new CareToggle();

        @Comment("Seconds between top-ups for everyone in the lobby world.")
        private int interval = 15;

        public CareToggle getHeal() {
            return heal;
        }

        public void setHeal(CareToggle heal) {
            this.heal = heal;
        }

        public CareToggle getSaturate() {
            return saturate;
        }

        public void setSaturate(CareToggle saturate) {
            this.saturate = saturate;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }
    }

    /** One upkeep switch. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class CareToggle extends OkaeriConfig {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** One box corner: x, y, and z block coordinates, nothing else. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Position extends OkaeriConfig {

        private double x;
        private double y;
        private double z;

        public static Position of(double x, double y, double z) {
            Position position = new Position();
            position.setX(x);
            position.setY(y);
            position.setZ(z);
            return position;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }
    }
}
