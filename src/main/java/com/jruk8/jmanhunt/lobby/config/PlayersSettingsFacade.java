package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.config.PlayerSettings;

/**
 * Typed lobby-aware reads for the settings.players subtree. Each method
 * resolves one override path: the lobby value wins, the typed global
 * section is the fallback. Callers take this facade, never raw paths.
 */
public final class PlayersSettingsFacade {
    private static final String BASE = "settings.players.";

    private final OverrideService overrides;
    private final PlayerSettings global;

    public PlayersSettingsFacade(OverrideService overrides, PlayerSettings global) {
        this.overrides = overrides;
        this.global = global;
    }

    /** Spectator toolbar hotbar layout, one character per slot. */
    public String toolbarLayout(Integer lobby) {
        return overrides.getString(lobby, BASE + "spectator.toolbar.layout",
                global.getSpectator().getToolbar().getLayout());
    }

    /** True when spectators lock onto teleported-to players. */
    public boolean toolbarLockOn(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "spectator.toolbar.lock-on",
                global.getSpectator().getToolbar().isLockOn());
    }

    /** Locked-follow teleport distance in blocks. */
    public int toolbarTpDistance(Integer lobby) {
        return overrides.getInt(lobby, BASE + "spectator.toolbar.tp-distance",
                global.getSpectator().getToolbar().getTpDistance());
    }

    /** True when the spectator snowball deploys. */
    public boolean snowballEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "spectator.toolbar.snowball.enabled",
                global.getSpectator().getToolbar().snowball.isEnabled());
    }

    /** Recharge time between snowball throws, in seconds. */
    public int snowballCooldownSeconds(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "spectator.toolbar.snowball.cooldown-seconds",
                global.getSpectator().getToolbar().snowball.getCooldownSeconds());
    }

    /** True when players turn invulnerable on game end. */
    public boolean invulnerabilityOnGameEnd(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "invulnerability.on-game-end.enabled",
                global.getInvulnerability().getOnGameEnd().isEnabled());
    }

    /** True when roles reset on game end. */
    public boolean rolesResetOnGameEnd(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "roles.reset-on-game-end.enabled",
                global.getRoles().getResetOnGameEnd().isEnabled());
    }

    /** Configured hunter lives, -1 means unlimited. */
    public int hunterLives(Integer lobby) {
        return overrides.getInt(lobby, BASE + "respawn.hunter.lives",
                global.getRespawn().getHunter().getLives());
    }

    /** Global respawn section, mirroring the engine revive path (no lobby override). */
    public PlayerSettings.Respawn getRespawn() {
        return global.getRespawn();
    }

    /** Configured speedrunner lives, -1 means unlimited. */
    public int speedrunnerLives(Integer lobby) {
        return overrides.getInt(lobby, BASE + "respawn.speedrunner.lives",
                global.getRespawn().getSpeedrunner().getLives());
    }

    /** True when role announcements chat. */
    public boolean announceRolesChat(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "announce-roles.chat.enabled",
                global.getAnnounceRoles().getChat().isEnabled());
    }

    /** True when role announcements show a title. */
    public boolean announceRolesTitle(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "announce-roles.title.enabled",
                global.getAnnounceRoles().getTitle().isEnabled());
    }

    /** True when role announcements play sounds. */
    public boolean announceRolesSounds(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "announce-roles.sounds.enabled",
                global.getAnnounceRoles().getSounds().isEnabled());
    }

    /** Role title fade-in seconds. */
    public double announceTitleFadeInSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "announce-roles.title.fade-in-seconds",
                global.getAnnounceRoles().getTitle().getFadeInSeconds());
    }

    /** Role title stay seconds. */
    public double announceTitleStaySeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "announce-roles.title.stay-seconds",
                global.getAnnounceRoles().getTitle().getStaySeconds());
    }

    /** Role title fade-out seconds. */
    public double announceTitleFadeOutSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "announce-roles.title.fade-out-seconds",
                global.getAnnounceRoles().getTitle().getFadeOutSeconds());
    }
}
