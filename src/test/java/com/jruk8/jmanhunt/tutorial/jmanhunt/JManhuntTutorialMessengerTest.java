package com.jruk8.jmanhunt.tutorial.jmanhunt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JManhuntTutorialMessengerTest {

    @Test
    void logoStripsFirstBracketPair() {
        assertEquals("<#a6a6a6><gradient:#e66550:#de7766><bold>J</bold>Manhunt</gradient></#a6a6a6> ",
                JManhuntTutorialMessenger.logo(
                        "<#a6a6a6>[<gradient:#e66550:#de7766><bold>J</bold>Manhunt</gradient>]</#a6a6a6> "));
    }

    @Test
    void logoHandlesNullAndPlain() {
        assertEquals("", JManhuntTutorialMessenger.logo(null));
        assertEquals("Hi", JManhuntTutorialMessenger.logo("Hi"));
    }
}
