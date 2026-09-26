package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorHeadsTest {

    @Test
    void unsetAndNoneAuthorsUseDefaultHead() {
        assertEquals(Optional.empty(), AuthorHeads.profileId(null));
        assertEquals(Optional.empty(), AuthorHeads.profileId(""));
        assertEquals(Optional.empty(), AuthorHeads.profileId("   "));
        assertEquals(Optional.empty(), AuthorHeads.profileId("none"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("None"));
    }

    @Test
    void pluginAuthorUsesOwnerUuid() {
        assertEquals(Optional.of(UUID.fromString("8786a40f-8856-4d0e-8ad5-0c9d7a13d2e9")),
                AuthorHeads.profileId("JManhunt"));
    }

    @Test
    void otherAuthorsKeepDefaultHead() {
        assertEquals(Optional.empty(), AuthorHeads.profileId("DaVeltto"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("jmanhunt"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("JMANHUNT"));
    }

    @Test
    void ownerConstantParsesAsUuid() {
        assertTrue(AuthorHeads.OWNER_PROFILE_ID
                .equalsIgnoreCase(UUID.fromString(AuthorHeads.OWNER_PROFILE_ID).toString()));
    }
}
