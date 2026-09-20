package com.jruk8.jmanhunt.settings.world_engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferRefillPolicyTest {

    @Test
    void parseAcceptsBothPoliciesCaseInsensitively() {
        assertEquals(BufferRefillPolicy.ALWAYS, BufferRefillPolicy.parse("ALWAYS"));
        assertEquals(BufferRefillPolicy.ALWAYS, BufferRefillPolicy.parse(" always "));
        assertEquals(BufferRefillPolicy.NO_MATCH_RUNNING, BufferRefillPolicy.parse("NO_MATCH_RUNNING"));
        assertEquals(BufferRefillPolicy.NO_MATCH_RUNNING, BufferRefillPolicy.parse("no_match_running"));
    }

    @Test
    void parseDefaultsToAlways() {
        assertEquals(BufferRefillPolicy.ALWAYS, BufferRefillPolicy.parse(null));
        assertEquals(BufferRefillPolicy.ALWAYS, BufferRefillPolicy.parse(""));
        assertEquals(BufferRefillPolicy.parse("sometimes"), BufferRefillPolicy.ALWAYS);
    }
}
