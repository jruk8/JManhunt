package com.jruk8.jmanhunt.updatechecker.ports;

/** One GitHub release: tag, page URL, and pre-release flag. */
public record GitHubRelease(String tagName, String htmlUrl, boolean prerelease) {
}
