package com.jruk8.jmanhunt.updatechecker.jmanhunt;

import com.jruk8.jmanhunt.updatechecker.ports.GitHubRelease;
import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckHttp;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/**
 * Fetches the latest GitHub release over HTTPS. Owner and repo are
 * constructor arguments so the adapter ports to other plugins.
 */
public final class JManhuntUpdateCheckHttp implements UpdateCheckHttp {
    private static final int TIMEOUT_MILLIS = 10_000;

    private final String owner;
    private final String repo;

    public JManhuntUpdateCheckHttp(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    @Override
    public Optional<GitHubRelease> fetchLatestRelease() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(
                "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest")
                .toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "JManhunt-UpdateChecker");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IOException("GitHub releases returned HTTP " + connection.getResponseCode());
            }
            try (InputStream body = connection.getInputStream()) {
                return parseRelease(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Minimal release parse: tag_name, html_url, prerelease. Empty when
     * any field is missing. Pure for tests.
     */
    static Optional<GitHubRelease> parseRelease(String json) {
        String tag = stringField(json, "tag_name");
        String htmlUrl = stringField(json, "html_url");
        Boolean prerelease = booleanField(json, "prerelease");
        if (tag == null || htmlUrl == null || prerelease == null) {
            return Optional.empty();
        }
        return Optional.of(new GitHubRelease(tag, htmlUrl, prerelease));
    }

    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Boolean booleanField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(true|false)").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }
}
