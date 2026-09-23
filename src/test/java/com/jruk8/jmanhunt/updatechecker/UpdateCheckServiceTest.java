package com.jruk8.jmanhunt.updatechecker;

import com.jruk8.jmanhunt.updatechecker.ports.GitHubRelease;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckHttp;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckLogger;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckNotifier;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckServiceTest {

    private static final class FakeHttp implements UpdateCheckHttp {
        private final GitHubRelease release;
        private final boolean fail;

        FakeHttp(GitHubRelease release, boolean fail) {
            this.release = release;
            this.fail = fail;
        }

        @Override
        public Optional<GitHubRelease> fetchLatestRelease() throws Exception {
            if (fail) {
                throw new java.io.IOException("offline");
            }
            return Optional.ofNullable(release);
        }
    }

    private static final class FakeNotifier implements UpdateCheckNotifier {
        final List<String> adminNotices = new ArrayList<>();

        @Override
        public void notifyAdmins(String message) {
            adminNotices.add(message);
        }

        @Override
        public void notifyPlayer(Player player, String message) {
            adminNotices.add(message);
        }
    }

    private static final class FakeLogger implements UpdateCheckLogger {
        final List<String> infos = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void warning(String message) {
            warnings.add(message);
        }

        @Override
        public void severe(String message) {
        }
    }

    private static GitHubRelease release(String tag) {
        return new GitHubRelease(tag, "https://example.invalid/r", false);
    }

    @Test
    void newerReleaseNotifiesAndPends() {
        FakeNotifier notifier = new FakeNotifier();
        FakeLogger logger = new FakeLogger();
        UpdateCheckService service = new UpdateCheckService(
                new FakeHttp(release("v1.3.0"), false), notifier, logger);

        service.checkNow(new UpdateCheckSettings(true, true, true, false), "1.2.3");

        assertEquals(1, notifier.adminNotices.size());
        assertTrue(notifier.adminNotices.get(0).contains("1.3.0"));
        assertTrue(service.pendingUpdate().isPresent());
        assertEquals(1, logger.infos.size());
    }

    @Test
    void disabledSeverityStaysSilent() {
        FakeNotifier notifier = new FakeNotifier();
        UpdateCheckService service = new UpdateCheckService(
                new FakeHttp(release("v1.2.4"), false), notifier, new FakeLogger());

        service.checkNow(new UpdateCheckSettings(true, true, true, false), "1.2.3");

        assertTrue(notifier.adminNotices.isEmpty());
        assertTrue(service.pendingUpdate().isEmpty());
    }

    @Test
    void currentOrNewerStaysSilent() {
        FakeNotifier notifier = new FakeNotifier();
        UpdateCheckService same = new UpdateCheckService(
                new FakeHttp(release("v1.2.3"), false), notifier, new FakeLogger());
        UpdateCheckService older = new UpdateCheckService(
                new FakeHttp(release("v1.2.2"), false), notifier, new FakeLogger());

        same.checkNow(new UpdateCheckSettings(true, true, true, true), "1.2.3");
        older.checkNow(new UpdateCheckSettings(true, true, true, true), "1.2.3");

        assertTrue(notifier.adminNotices.isEmpty());
    }

    @Test
    void prereleaseIsSkipped() {
        FakeNotifier notifier = new FakeNotifier();
        GitHubRelease prerelease = new GitHubRelease("v2.0.0", "https://example.invalid/r", true);
        UpdateCheckService service = new UpdateCheckService(
                new FakeHttp(prerelease, false), notifier, new FakeLogger());

        service.checkNow(new UpdateCheckSettings(true, true, true, true), "1.2.3");

        assertTrue(notifier.adminNotices.isEmpty());
        assertTrue(service.pendingUpdate().isEmpty());
    }

    @Test
    void fetchFailureWarnsOnce() {
        FakeNotifier notifier = new FakeNotifier();
        FakeLogger logger = new FakeLogger();
        UpdateCheckService service = new UpdateCheckService(
                new FakeHttp(null, true), notifier, logger);

        service.checkNow(new UpdateCheckSettings(true, true, true, true), "1.2.3");

        assertTrue(notifier.adminNotices.isEmpty());
        assertEquals(1, logger.warnings.size());
    }

    @Test
    void disabledCheckerDoesNothing() {
        FakeNotifier notifier = new FakeNotifier();
        UpdateCheckService service = new UpdateCheckService(
                new FakeHttp(release("v2.0.0"), false), notifier, new FakeLogger());

        service.checkNow(new UpdateCheckSettings(false, true, true, true), "1.2.3");

        assertTrue(notifier.adminNotices.isEmpty());
        assertTrue(service.pendingUpdate().isEmpty());
    }
}
