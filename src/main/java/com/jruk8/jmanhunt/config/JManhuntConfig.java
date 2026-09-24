package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;

/**
 * Typed root of config.yml. Every key keeps its current path except the
 * settings block (see {@link SettingsConfig}) and the former sounds block
 * (see {@link SoundsConfig}). Field names are camelCase and every kebab-case
 * key is pinned with {@link CustomKey}, so no naming strategy is trusted.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "      ██╗  ███╗   ███╗ █████╗ ███╗   ██╗██╗  ██╗██╗   ██╗███╗   ██╗████████╗",
        "      ██║  ████╗ ████║██╔══██╗████╗  ██║██║  ██║██║   ██║████╗  ██║╚══██╔══╝",
        "      ██║  ██╔████╔██║███████║██╔██╗ ██║███████║██║   ██║██╔██╗ ██║   ██║",
        "      ██║  ██║╚██╔╝██║██╔══██║██║╚██╗██║██╔══██║██║   ██║██║╚██╗██║   ██║",
        " ██████╔╝  ██║ ╚═╝ ██║██║  ██║██║ ╚████║██║  ██║╚██████╔╝██║ ╚████║   ██║",
        " ╚═════╝   ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝   ╚═╝",
        "A Deeply Configurable Manhunt plugin for modern servers.",
        "",
        "Homepage: https://modrinth.com/plugin/jmanhunt",
        "Challenges Addon: https://builtbybit.com/resources/jmanhunt-challenges.121574/",
        "",
        "Save and apply your changes with /manhunt reload.",
        "Stuck? Use CTRL+F and refer to the documentation:",
        "- https://jruk8.github.io/JManhunt/",
        "",
        "Issues? Report them on GitHub (star us!):",
        "- https://github.com/jruk8/JManhunt/issues",
        "For quick assistance and community modifiers, join our Discord server:",
        "- https://discord.gg/hkWmCVmWDC",
        "Support our development on Ko-fi:",
        "- https://ko-fi.com/jruk",
        "",
        "Built by Starburst Studios",
        "© 2026 jruk. Licensed under GNU AGPLv3.",
        "",
        "**--**--**--**--**--**--**--**--**--**--**--**--**--**--**",
        ""
})
public class JManhuntConfig extends OkaeriConfig {

    @CustomKey("config-version")
    @Comment("Used for config updates, don't change unless you know what you're doing.")
    private int configVersion = 6;

    @CustomKey("send-anonymous-statistics")
    @Comment({
            "Send anonymous statistics via bStats (open-source). We never collect personal data or IP addresses.",
            "This setting is used to improve development and plugin stability.",
            "Requires a server restart to take effect.",
            "Default: true"
    })
    private boolean sendAnonymousStatistics = true;

    @CustomKey("update-checker")
    @Comment({
            "Update checker: compares the running version against the latest GitHub",
            "release (pre-releases are skipped) and notifies holders of the",
            "jmanhunt.admin permission. The check runs once after enable and on admin",
            "join."
    })
    private UpdateCheckerConfig updateChecker = new UpdateCheckerConfig();

    @Comment({
            "Debug output for pinpointing laggy configuration, such as cell fetches and",
            "game starts. Toggled at runtime per player or for the console with",
            "/manhunt debug. This value is only the default applied on restart.",
            "Default: false"
    })
    private DebugConfig debug = new DebugConfig();

    @Comment({
            "Persistent career statistics. SQLite is local and requires no setup. Use",
            "PostgreSQL when several JManhunt servers should share the same statistics.",
            "If you are upgrading from an older version, rename your existing jmanhunt.db",
            "file to statistics.db to keep your stored statistics."
    })
    private StatisticsConfig statistics = new StatisticsConfig();

    @Comment({
            "Match lifecycle settings: end-of-match delay, pre-start reminders,",
            "disconnect rules, end-screen statistics, and game rules."
    })
    private MatchConfig match = new MatchConfig();

    @Comment({
            "Lobby queues. Each lobby runs its own queue, autostart countdown, and match.",
            "Players join the default lobby on login and keep it until they join another",
            "lobby or leave the server."
    })
    private LobbiesConfig lobbies = new LobbiesConfig();

    @Comment({
            "Settings that modify the game.",
            "These can be toggled in-game with the 'config' subcommand."
    })
    private SettingsConfig settings = new SettingsConfig();

    @CustomKey("world-engine")
    @Comment({
            "Grid-based single-world manhunt engine.",
            "Uses persistent square-spiral cell indexing and teleports participants to",
            "randomized points near each cell center.",
            "Avoids manual world restarting entirely.",
            "",
            "Setting this to true will additionally modify stronghold generation,",
            "such that to bypass the 128-per-world limit and spread them around",
            "like normal structures. You can tweak the rates in",
            "settings/world-engine/strongholds.json and run /mh reload afterward.",
            "Default: false"
    })
    private WorldEngineConfig worldEngine = new WorldEngineConfig();

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public boolean isSendAnonymousStatistics() {
        return sendAnonymousStatistics;
    }

    public void setSendAnonymousStatistics(boolean sendAnonymousStatistics) {
        this.sendAnonymousStatistics = sendAnonymousStatistics;
    }

    public UpdateCheckerConfig getUpdateChecker() {
        return updateChecker;
    }

    public void setUpdateChecker(UpdateCheckerConfig updateChecker) {
        this.updateChecker = updateChecker;
    }

    public DebugConfig getDebug() {
        return debug;
    }

    public void setDebug(DebugConfig debug) {
        this.debug = debug;
    }

    public StatisticsConfig getStatistics() {
        return statistics;
    }

    public void setStatistics(StatisticsConfig statistics) {
        this.statistics = statistics;
    }

    public MatchConfig getMatch() {
        return match;
    }

    public void setMatch(MatchConfig match) {
        this.match = match;
    }

    public LobbiesConfig getLobbies() {
        return lobbies;
    }

    public void setLobbies(LobbiesConfig lobbies) {
        this.lobbies = lobbies;
    }

    public SettingsConfig getSettings() {
        return settings;
    }

    public void setSettings(SettingsConfig settings) {
        this.settings = settings;
    }

    public WorldEngineConfig getWorldEngine() {
        return worldEngine;
    }

    public void setWorldEngine(WorldEngineConfig worldEngine) {
        this.worldEngine = worldEngine;
    }

}
