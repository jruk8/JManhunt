package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;

/** Team chat format and usage hint. */
@Getter
@SuppressWarnings("FieldMayBeFinal")
public class ChatMessages extends OkaeriConfig {

    @CustomKey("team-chat-format")
    @Comment({
            "Team chat line. {role} is the colored role name, {rolecolor}",
            "the raw role color tag, {player} the sender, {message} the",
            "escaped text. No {prefix} on purpose: it stays readable."
    })
    private String teamChatFormat =
            "<gray>@team [{role}<gray>] <white>{player}: {rolecolor}{message}";

    @CustomKey("team-chat-usage")
    private String teamChatUsage =
            "{prefix}<yellow>Usage: @team <message> to chat with your team.";
}
