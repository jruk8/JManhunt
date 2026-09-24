package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Debug channel lines. */
@SuppressWarnings("FieldMayBeFinal")
public class DebugMessages extends OkaeriConfig {

    @CustomKey("prefix")
    private String prefix = "<gray>[<bold>J</bold>ManhuntDebug]</gray> ";

    @CustomKey("cell-fetched")
    private String cellFetched = "{debug-prefix}<gray>Fetched cell <white>{index} <gray>at <white>{x}<gray>, " +
            "<white>{z}<gray>.";

    @CustomKey("cell-buffer-add")
    private String cellBufferAdd = "{debug-prefix}<gray>Buffered cell <white>{index} <gray>({count}/{target}).";

    @CustomKey("cell-fetch-failed")
    private String cellFetchFailed = "{debug-prefix}<gray>Cell fetch failed, retrying in <white>{seconds}s<gray>.";

    @CustomKey("match-start")
    private String matchStart = "{debug-prefix}<gray>Match started in lobby <white>{lobby} <gray>on cell " +
            "<white>{index}<gray>.";

    @CustomKey("match-end")
    private String matchEnd = "{debug-prefix}<gray>Match on cell <white>{index} <gray>ended.";

    @CustomKey("end-cell-reserved")
    private String endCellReserved = "{debug-prefix}<gray>Reserved <white>{cell} <gray>for instance " +
            "<white>{id}<gray>.";

    @CustomKey("end-cell-created")
    private String endCellCreated = "{debug-prefix}<gray>Created end dimension <white>{cell}<gray>.";

    @CustomKey("end-cell-reset")
    private String endCellReset = "{debug-prefix}<gray>Reset end dimension <white>{cell}<gray>.";

    @CustomKey("end-cell-pruned")
    private String endCellPruned = "{debug-prefix}<gray>Pruned end dimension <white>{cell}<gray>.";

    @CustomKey("border-mode")
    private String borderMode = "{debug-prefix}<gray>Border mode: <white>{mode}<gray>.";

    @CustomKey("portal-reroute")
    private String portalReroute = "{debug-prefix}<gray>Rerouted <white>{player} <gray>to <white>{cell}<gray>.";

    @CustomKey("lobby-fallback")
    private String lobbyFallback = "{debug-prefix}<gray>Failed to fetch lobbytp for lobby <white>{lobby}<gray>, " +
            "using fallback <white>{fallback}<gray>.";

    @CustomKey("lobby-missing")
    private String lobbyMissing = "{debug-prefix}<gray>No lobbytp for lobby <white>{lobby}<gray> anywhere (or " +
            "the lobby world is missing); targets were told to contact an administrator.";
}
