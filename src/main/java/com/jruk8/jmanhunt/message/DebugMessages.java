package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Debug channel lines. */
@SuppressWarnings("FieldMayBeFinal")
public class DebugMessages extends OkaeriConfig {

    @CustomKey("prefix")
    private String prefix = "<gray>[<bold>J</bold>ManhuntDebug]</gray> ";

    @CustomKey("cell-fetched")
    private String cellFetched = "{debug-prefix}Fetched cell <white>{index}</white> at " +
            "<white>{x}</white>, <white>{z}</white>.";

    @CustomKey("cell-buffer-add")
    private String cellBufferAdd = "{debug-prefix}Buffered cell <white>{index}</white> " +
            "(<white>{count}</white>/<white>{target}</white>).";

    @CustomKey("cell-fetch-failed")
    private String cellFetchFailed = "{debug-prefix}Cell fetch failed, retrying in " +
            "<white>{seconds}s</white>.";

    @CustomKey("match-start")
    private String matchStart = "{debug-prefix}Match started in lobby <white>{lobby}</white> on " +
            "cell <white>{index}</white>.";

    @CustomKey("match-end")
    private String matchEnd = "{debug-prefix}Match on cell <white>{index}</white> ended.";

    @CustomKey("end-cell-reserved")
    private String endCellReserved = "{debug-prefix}Reserved <white>{cell}</white> for instance " +
            "<white>{id}</white>.";

    @CustomKey("end-cell-created")
    private String endCellCreated = "{debug-prefix}Created end dimension <white>{cell}</white>.";

    @CustomKey("end-cell-reset")
    private String endCellReset = "{debug-prefix}Reset end dimension <white>{cell}</white>.";

    @CustomKey("end-cell-pruned")
    private String endCellPruned = "{debug-prefix}Pruned end dimension <white>{cell}</white>.";

    @CustomKey("end-pool-scan")
    private String endPoolScan = "{debug-prefix}End pool scan: container <white>{container}</white> " +
            "scan saw <white>{entries}</white> candidates; pool <white>{pool}</white> loaded " +
            "<white>{loaded}</white> free <white>{free}</white>/<white>{buffer}</white> assigned " +
            "<white>{assigned}</white>.";

    @CustomKey("end-cell-topup")
    private String endCellTopup = "{debug-prefix}End pool top-up: chose <white>{cell}</white> from " +
            "pool <white>{pool}</white>.";

    @CustomKey("end-cell-create-attempt")
    private String endCellCreateAttempt = "{debug-prefix}End dimension create: <white>{cell}</white> " +
            "already-loaded=<white>{loaded}</white> folder-exists=<white>{folder}</white>.";

    @CustomKey("portal-reroute")
    private String portalReroute = "{debug-prefix}Rerouted <white>{player}</white> to " +
            "<white>{cell}</white>.";

    @CustomKey("lobby-fallback")
    private String lobbyFallback = "{debug-prefix}Failed to fetch lobbytp for lobby " +
            "<white>{lobby}</white>, using fallback <white>{fallback}</white>.";

    @CustomKey("lobby-missing")
    private String lobbyMissing = "{debug-prefix}No lobbytp for lobby <white>{lobby}</white> " +
            "anywhere (or the lobby world is missing); targets were told to contact an " +
            "administrator.";

    @CustomKey("interval-skip")
    private String intervalSkip = "{debug-prefix}INTERVAL <white>{modifier}</white> skipped " +
            "<white>{player}</white> (<white>{why}</white>).";
}
