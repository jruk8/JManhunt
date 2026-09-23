package com.jruk8.jmanhunt.updatechecker;

import com.jruk8.jmanhunt.updatechecker.ports.GitHubRelease;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckHttp;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckLogger;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckNotifier;
import java.util.Optional;

/**
 * Update check engine: fetches the latest GitHub release and notifies
 * admins when a newer enabled-severity version exists. Only Bukkit and
 * JDK types are referenced, never host plugin classes, so the engine
 * lifts out into another plugin with fresh port adapters.
 */
public final class UpdateCheckService {
    private final UpdateCheckHttp http;
    private final UpdateCheckNotifier notifier;
    private final UpdateCheckLogger logger;
    private volatile UpdateAvailable pending;

    public UpdateCheckService(UpdateCheckHttp http, UpdateCheckNotifier notifier, UpdateCheckLogger logger) {
        this.http = http;
        this.notifier = notifier;
        this.logger = logger;
    }

    /** Last found update, if any. */
    public Optional<UpdateAvailable> pendingUpdate() {
        return Optional.ofNullable(pending);
    }

    /**
     * Runs one check against the current version. Blocking: call off the
     * main thread. Failures log once and leave no pending update.
     */
    public void checkNow(UpdateCheckSettings settings, String currentVersion) {
        pending = null;
        if (!settings.enabled()) {
            return;
        }
        GitHubRelease release;
        try {
            Optional<GitHubRelease> fetched = http.fetchLatestRelease();
            if (fetched.isEmpty() || fetched.get().prerelease()) {
                return;
            }
            release = fetched.get();
        } catch (Exception exception) {
            logger.warning("Update check failed: " + exception.getMessage());
            return;
        }
        UpdateVersion current = UpdateVersion.parse(currentVersion, logger);
        UpdateVersion latest = UpdateVersion.parse(release.tagName(), logger);
        if (!latest.isNewerThan(current)) {
            return;
        }
        String severity = latest.severityOver(current);
        if (severity == null || !settings.allows(severity)) {
            return;
        }
        String message = "JManhunt " + latest + " is available (running " + current + "): "
                + release.htmlUrl();
        pending = new UpdateAvailable(message, latest.toString());
        logger.info(message);
        notifier.notifyAdmins(message);
    }
}
