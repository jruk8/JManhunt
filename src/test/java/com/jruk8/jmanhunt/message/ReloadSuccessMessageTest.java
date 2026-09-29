package com.jruk8.jmanhunt.message;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import org.junit.jupiter.api.Test;

class ReloadSuccessMessageTest {

    @Test
    void reloadSuccessReportsElapsedMilliseconds() {
        Object template = ConfigPathMapper.get(new MessagesConfig(), "manhunt.reload-success");

        assertTrue(template instanceof String, "reload-success must be a string template");
        assertTrue(((String) template).contains("{elapsed}"),
                "reload-success must name the {elapsed} placeholder");
        assertTrue(((String) template).contains("{elapsed}ms"),
                "reload-success must report milliseconds");
    }
}
