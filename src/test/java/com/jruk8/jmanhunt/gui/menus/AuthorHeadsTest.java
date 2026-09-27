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
    void dashedUuidParsesWithSurroundingSpace() {
        assertEquals(Optional.of(UUID.fromString("5663d1ab-276f-4b49-ab18-b2e6d8baa919")),
                AuthorHeads.profileId("5663d1ab-276f-4b49-ab18-b2e6d8baa919"));
        assertEquals(Optional.of(UUID.fromString("5663d1ab-276f-4b49-ab18-b2e6d8baa919")),
                AuthorHeads.profileId("  5663d1ab-276f-4b49-ab18-b2e6d8baa919  "));
    }

    @Test
    void trimmedUuidParsesToDashedForm() {
        assertEquals(Optional.of(UUID.fromString("5663d1ab-276f-4b49-ab18-b2e6d8baa919")),
                AuthorHeads.profileId("5663d1ab276f4b49ab18b2e6d8baa919"));
        assertEquals(Optional.of(UUID.fromString("5663d1ab-276f-4b49-ab18-b2e6d8baa919")),
                AuthorHeads.profileId("5663D1AB276F4B49AB18B2E6D8BAA919"));
    }

    @Test
    void malformedUuidsKeepDefaultHead() {
        assertEquals(Optional.empty(), AuthorHeads.profileId("5663d1ab-276f-4b49-ab18"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("5663d1ab276f4b49ab18b2e6d8baa91"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
        assertEquals(Optional.empty(), AuthorHeads.profileId("not a uuid at all!"));
    }

    @Test
    void ownerConstantParsesAsUuid() {
        assertTrue(AuthorHeads.OWNER_PROFILE_ID
                .equalsIgnoreCase(UUID.fromString(AuthorHeads.OWNER_PROFILE_ID).toString()));
    }
}
