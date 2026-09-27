package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.world.cell.BufferRefillPolicy;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Grid-based single-world manhunt engine. */
@SuppressWarnings("FieldMayBeFinal")
public class WorldEngineConfig extends OkaeriConfig {

    private boolean enabled = false;

    @CustomKey("world-name")
    private String worldName = "world";

    @CustomKey("role-pads")
    @Comment({
            "Stand-on role pads. A player in the lobby world inside a listed block's",
            "XZ cell and at most 4 blocks above it is assigned the mapped role,",
            "like setplayer. Material names are Bukkit Material names.",
            "Default: true",
            "Lobby-world blocks that assign roles when stood on."
    })
    private RolePads rolePads = new RolePads();

    @CustomKey("cell-size")
    @Comment({
            "Lobby teleport points and boundary boxes live in",
            "settings/world-engine/lobby-config.yml, managed in-game with",
            "'/mh worldengine lobbyconfig'.",
            "They are not editable through /manhunt config.",
            "Size of one spiral cell in blocks. Hard-capped at 50,000.",
            "Too low values may cause issues.",
            "Default: 10,000"
    })
    private int cellSize = 10000;

    @CustomKey("tp-spread-radius")
    @Comment({
            "Radius from each cell center used when selecting player's spawn points.",
            "Hard-capped at cell-size / 2.",
            "Default: 5"
    })
    private int tpSpreadRadius = 5;

    @CustomKey("spawnpoint-algorithm")
    @Comment({
            "Spawnpoint algorithm: validated per-player spawns that bypass tree",
            "leaves, require an air gap at the feet and head blocks, never",
            "land in fluids, and level heights around the group median. It also",
            "skips ocean and lava cells when fetching. This can sometimes cause",
            "server lag when many checks are required."
    })
    private SpawnpointAlgorithm spawnpointAlgorithm = new SpawnpointAlgorithm();

    @Comment({
            "Cell preloading. Console commands run whenever new cells are fetched, and",
            "a buffer of ready cells is kept so matches start without lag."
    })
    private Preloading preloading = new Preloading();

    @CustomKey("world-border")
    @Comment("World border configuration for cell confinement.")
    private WorldBorder worldBorder = new WorldBorder();

    @Comment("Shared pool of reusable end dimensions.")
    private End end = new End();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public RolePads getRolePads() {
        return rolePads;
    }

    public void setRolePads(RolePads rolePads) {
        this.rolePads = rolePads;
    }

    public int getCellSize() {
        return cellSize;
    }

    public void setCellSize(int cellSize) {
        this.cellSize = cellSize;
    }

    public int getTpSpreadRadius() {
        return tpSpreadRadius;
    }

    public void setTpSpreadRadius(int tpSpreadRadius) {
        this.tpSpreadRadius = tpSpreadRadius;
    }

    public SpawnpointAlgorithm getSpawnpointAlgorithm() {
        return spawnpointAlgorithm;
    }

    public void setSpawnpointAlgorithm(SpawnpointAlgorithm spawnpointAlgorithm) {
        this.spawnpointAlgorithm = spawnpointAlgorithm;
    }

    public Preloading getPreloading() {
        return preloading;
    }

    public void setPreloading(Preloading preloading) {
        this.preloading = preloading;
    }

    public WorldBorder getWorldBorder() {
        return worldBorder;
    }

    public void setWorldBorder(WorldBorder worldBorder) {
        this.worldBorder = worldBorder;
    }

    public End getEnd() {
        return end;
    }

    public void setEnd(End end) {
        this.end = end;
    }

    /** Stand-on role pads. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSilentRoleAssignment() {
            return silentRoleAssignment;
        }

        public void setSilentRoleAssignment(boolean silentRoleAssignment) {
            this.silentRoleAssignment = silentRoleAssignment;
        }

        public RolePadBlocks getBlocks() {
            return blocks;
        }

        public void setBlocks(RolePadBlocks blocks) {
            this.blocks = blocks;
        }

        /** Block per role, as Bukkit Material names. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class RolePadBlocks extends OkaeriConfig {
            private String speedrunner = "LIME_CONCRETE";
            private String hunter = "RED_CONCRETE";
            private String afk = "YELLOW_CONCRETE";
            private String spectator = "LIGHT_GRAY_CONCRETE";
            private String none = "GRAY_CONCRETE";

            public String getSpeedrunner() {
                return speedrunner;
            }

            public void setSpeedrunner(String speedrunner) {
                this.speedrunner = speedrunner;
            }

            public String getHunter() {
                return hunter;
            }

            public void setHunter(String hunter) {
                this.hunter = hunter;
            }

            public String getAfk() {
                return afk;
            }

            public void setAfk(String afk) {
                this.afk = afk;
            }

            public String getSpectator() {
                return spectator;
            }

            public void setSpectator(String spectator) {
                this.spectator = spectator;
            }

            public String getNone() {
                return none;
            }

            public void setNone(String none) {
                this.none = none;
            }
        }
    }

    /** Validated spawnpoint algorithm. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class SpawnpointAlgorithm extends OkaeriConfig {

        @Comment({
                "When true, validated spawns are used; when false, plain",
                "highest-block spawns are used everywhere.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("max-retries")
        @Comment({
                "Re-rolls per player whose first spawn misses the median height",
                "band. Minimum 0 (keep the first spawn).",
                "Default: 8"
        })
        private int maxRetries = 8;

        @CustomKey("y-tolerance")
        @Comment({
                "Plus-or-minus blocks around the median spawn height that",
                "count as level. Outliers are re-rolled.",
                "Default: 7"
        })
        private int yTolerance = 7;

        @CustomKey("spawn-close-to-structure")
        @Comment({
                "Spawns near a listed structure when one is within",
                "max-distance of a raw-passing cell. Checked after the",
                "raw terrain checks on each cell fetch, with a medium",
                "performance cost per lookup.",
                "Highly recommended: enable overworld structure boosts",
                "alongside this setting."
        })
        private SpawnCloseToStructure spawnCloseToStructure = new SpawnCloseToStructure();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getYTolerance() {
            return yTolerance;
        }

        public void setYTolerance(int yTolerance) {
            this.yTolerance = yTolerance;
        }

        public SpawnCloseToStructure getSpawnCloseToStructure() {
            return spawnCloseToStructure;
        }

        public void setSpawnCloseToStructure(SpawnCloseToStructure spawnCloseToStructure) {
            this.spawnCloseToStructure = spawnCloseToStructure;
        }

        /** Spawn cells near a listed structure. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class SpawnCloseToStructure extends OkaeriConfig {

            @Comment({
                    "When true, raw-passing cells are kept only when a",
                    "listed structure is near, up to attempts lookups per",
                    "fetch. On exhaustion the last raw-passing spawn wins.",
                    "Default: false"
            })
            private boolean enabled = false;

            @Comment({
                    "Structure words to look for. Allowed: VILLAGE (any",
                    "village type), TEMPLE (desert or jungle pyramid),",
                    "SHIPWRECK, RUINED_PORTAL, BURIED_TREASURE, MINESHAFT",
                    "(regular and mesa), OCEAN_MONUMENT, MANSION,",
                    "PILLAGER_OUTPOST. Unknown words are skipped. The more",
                    "entries, the higher the chance of a hit.",
                    "Default: [VILLAGE, TEMPLE, SHIPWRECK, RUINED_PORTAL]"
            })
            private List<String> structures = new ArrayList<>(
                    List.of("VILLAGE", "TEMPLE", "SHIPWRECK", "RUINED_PORTAL"));

            @Comment({
                    "Structure lookups per fetch before falling back to the",
                    "last raw-passing spawn. Minimum 1, maximum 5.",
                    "Default: 3"
            })
            private int attempts = 3;

            @CustomKey("max-distance")
            @Comment({
                    "How close a structure must be, in blocks. Minimum 50,",
                    "maximum 200.",
                    "Default: 125"
            })
            private int maxDistance = 125;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getStructures() {
                return structures;
            }

            public void setStructures(List<String> structures) {
                this.structures = structures;
            }

            public int getAttempts() {
                return attempts;
            }

            public void setAttempts(int attempts) {
                this.attempts = attempts;
            }

            public int getMaxDistance() {
                return maxDistance;
            }

            public void setMaxDistance(int maxDistance) {
                this.maxDistance = maxDistance;
            }
        }
    }

    /** Cell preloading commands and ready-cell buffer. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Preloading extends OkaeriConfig {

        @Comment({
                "Optional console commands run whenever a new cell is fetched for a",
                "match. <cellX> and <cellZ> are replaced with the cell's block",
                "coordinates. This is useful for chunk-generation plugins such as Chunky",
                "to pre-generate the cell area before players teleport in.",
                "Try something like:",
                "     - chunky world world",
                "     - chunky center <cellX> <cellZ>",
                "     - chunky radius 120",
                "     - chunky start",
                "     - chunky confirm"
        })
        private List<String> commands = new ArrayList<>();

        @CustomKey("cell-buffer")
        @Comment("Ready-cell buffer kept ahead of match starts.")
        private CellBuffer cellBuffer = new CellBuffer();

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public CellBuffer getCellBuffer() {
            return cellBuffer;
        }

        public void setCellBuffer(CellBuffer cellBuffer) {
            this.cellBuffer = cellBuffer;
        }

        /** Ready-cell buffer size and refill policy. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class CellBuffer extends OkaeriConfig {

            @CustomKey("stored-cells-buffer")
            @Comment({
                    "How many pregenerated cells to keep ready. Minimum: 1.",
                    "Default: 3"
            })
            private int storedCellsBuffer = 3;

            @CustomKey("increment-when")
            @Comment({
                    "When the buffer refills: ALWAYS refills whenever a slot is free,",
                    "NO_MATCH_RUNNING only refills while no match is running.",
                    "Default: ALWAYS"
            })
            private BufferRefillPolicy incrementWhen = BufferRefillPolicy.ALWAYS;

            public int getStoredCellsBuffer() {
                return storedCellsBuffer;
            }

            public void setStoredCellsBuffer(int storedCellsBuffer) {
                this.storedCellsBuffer = storedCellsBuffer;
            }

            public BufferRefillPolicy getIncrementWhen() {
                return incrementWhen;
            }

            public void setIncrementWhen(BufferRefillPolicy incrementWhen) {
                this.incrementWhen = incrementWhen;
            }
        }
    }

    /** World border cell confinement. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class WorldBorder extends OkaeriConfig {

        @Comment({
                "When true, the world border is used to limit the spiral cell area.",
                "It enforces the boundary between cells and prevents entering",
                "unused or already used cells. It works in both overworld and",
                "nether.",
                "",
                "This setting should only be used when the game world is different",
                "from the lobby world. Otherwise lobby players may suffocate.",
                "Default: true"
        })
        private boolean enabled = true;

        @Comment("The buffer (in blocks), after which the player is dealt damage.")
        private BorderDamage damage = new BorderDamage();

        @CustomKey("start-border")
        @Comment({
                "A smaller initial border that expands to cell size when the game",
                "begins. Only active when world-border.enabled is true AND",
                "start-on-speedrunner-damage.enabled is true."
        })
        private StartBorder startBorder = new StartBorder();

        @Comment("Pseudoborder wall particles for concurrent matches.")
        private WorldEngineParticles particles = new WorldEngineParticles();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BorderDamage getDamage() {
            return damage;
        }

        public void setDamage(BorderDamage damage) {
            this.damage = damage;
        }

        public StartBorder getStartBorder() {
            return startBorder;
        }

        public void setStartBorder(StartBorder startBorder) {
            this.startBorder = startBorder;
        }

        public WorldEngineParticles getParticles() {
            return particles;
        }

        public void setParticles(WorldEngineParticles particles) {
            this.particles = particles;
        }

        /** Border damage buffer and amount. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class BorderDamage extends OkaeriConfig {

            @Comment({
                    "The size of the world border buffer in blocks.",
                    "Default: 5.0"
            })
            private double buffer = 5.0;

            @Comment({
                    "Damage dealt per block per second when outside the buffer.",
                    "Default: 1.0"
            })
            private double amount = 1.0;

            public double getBuffer() {
                return buffer;
            }

            public void setBuffer(double buffer) {
                this.buffer = buffer;
            }

            public double getAmount() {
                return amount;
            }

            public void setAmount(double amount) {
                this.amount = amount;
            }
        }

        /** Smaller initial border that expands on game begin. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class StartBorder extends OkaeriConfig {

            @Comment({
                    "Enables the start border feature.",
                    "Default: true"
            })
            private boolean enabled = true;

            @Comment({
                    "Initial border radius in blocks. The actual diameter used is",
                    "max(this, tp-spread-radius + 1) * 2, ensuring players never",
                    "spawn outside the border.",
                    "Set to -1 to use tp-spread-radius + 1 only.",
                    "Default: 10"
            })
            private int radius = 10;

            @CustomKey("fadeout-time")
            @Comment({
                    "Time in seconds for the start border to animate expanding to",
                    "cell size when the game begins.",
                    "Set to 0 or -1 to skip the animation and snap to cell size",
                    "immediately.",
                    "Default: 5"
            })
            private int fadeoutTime = 5;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getRadius() {
                return radius;
            }

            public void setRadius(int radius) {
                this.radius = radius;
            }

            public int getFadeoutTime() {
                return fadeoutTime;
            }

            public void setFadeoutTime(int fadeoutTime) {
                this.fadeoutTime = fadeoutTime;
            }
        }

    }

    /** Shared pool of reusable end dimensions. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class End extends OkaeriConfig {

        @CustomKey("base-name")
        @Comment({
                "Base name for pooled end dimensions. Pool members are",
                "created as <base-name>_<n> starting at 1, for example",
                "jmh_end_1. Renaming strands existing pool folders: delete",
                "them by hand or keep the old name.",
                "Default: jmh_end"
        })
        private String baseName = "jmh_end";

        @Comment({
                "Free end dimensions kept ready for portal assignment.",
                "Minimum: 1.",
                "Default: 3"
        })
        private int buffer = 3;

        public String getBaseName() {
            return baseName;
        }

        public void setBaseName(String baseName) {
            this.baseName = baseName;
        }

        public int getBuffer() {
            return buffer;
        }

        public void setBuffer(int buffer) {
            this.buffer = buffer;
        }
    }
}