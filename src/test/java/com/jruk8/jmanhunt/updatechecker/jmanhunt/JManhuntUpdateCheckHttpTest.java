package com.jruk8.jmanhunt.updatechecker.jmanhunt;

import com.jruk8.jmanhunt.updatechecker.ports.GitHubRelease;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JManhuntUpdateCheckHttpTest {

    private static final String RELEASE_JSON = """
            {"tag_name": "v1.3.0",
             "html_url": "https://github.com/jruk8/JManhunt/releases/tag/v1.3.0",
             "prerelease": false}""";

    @Test
    void parseReleaseReadsFields() {
        GitHubRelease release = JManhuntUpdateCheckHttp.parseRelease(RELEASE_JSON).orElseThrow();

        assertEquals("v1.3.0", release.tagName());
        assertEquals("https://github.com/jruk8/JManhunt/releases/tag/v1.3.0", release.htmlUrl());
        assertFalse(release.prerelease());
    }

    @Test
    void parseReleaseReadsPrereleaseFlag() {
        String json = RELEASE_JSON.replace("\"prerelease\": false", "\"prerelease\":true");

        assertTrue(JManhuntUpdateCheckHttp.parseRelease(json).orElseThrow().prerelease());
    }

    @Test
    void parseReleaseEmptyWhenFieldsMissing() {
        assertTrue(JManhuntUpdateCheckHttp.parseRelease("{}").isEmpty());
        assertTrue(JManhuntUpdateCheckHttp.parseRelease("not json").isEmpty());
    }
}
