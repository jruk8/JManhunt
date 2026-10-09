package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * One death, one line: friendly-fire kills announce only the
 * teamkill line, eliminations only the out-of-lives line, and
 * survived deaths only the died line. Killer and victim roles are
 * read before ON_DEATH converters rewrite them.
 */
public final class DeathMessageService {
    /** Mocking friendly fire lines the broadcast picks between. */
    static final int FRIENDLY_FIRE_LINES = 3;

    /** Messaging plus game texts. */
    public record DeathTexts(MatchMessaging messaging, GameMessages gameTexts) {
    }

    private final DeathTexts texts;
    private final PlayerStateStore states;
    private final BooleanSupplier friendlyFireBroadcasts;

    public DeathMessageService(DeathTexts texts, PlayerStateStore states,
            BooleanSupplier friendlyFireBroadcasts) {
        this.texts = texts;
        this.states = states;
        this.friendlyFireBroadcasts = friendlyFireBroadcasts;
    }

    /**
     * Announces one death: the victim role is the pre-elimination
     * role, runnersRemaining feeds the survived-runner tally, and
     * quiet (spawncamp punishment) stays silent.
     */
    public void announceDeath(GameInstance instance, Player victim, Role victimRole,
            boolean eliminated, int runnersRemaining, boolean quiet) {
        if (quiet) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer != null && friendlyFireBroadcasts.getAsBoolean()
                && isFriendlyFireKill(states.role(killer), victimRole,
                        killer.getUniqueId().equals(victim.getUniqueId()))) {
            int roll = ThreadLocalRandom.current().nextInt(FRIENDLY_FIRE_LINES);
            texts.messaging().sendToInstance(instance,
                    friendlyFireTemplate(texts.gameTexts(), roll),
                    Map.of("dead", victim.getName(), "killer", killer.getName()));
            return;
        }
        if (eliminated) {
            texts.messaging().sendToInstance(instance,
                    victimRole == Role.HUNTER ? texts.gameTexts().getHunterOutOfLives()
                            : texts.gameTexts().getSpeedrunnerOutOfLives(),
                    Map.of("remaining", remainingText(runnersRemaining)));
            return;
        }
        if (victimRole == Role.HUNTER) {
            texts.messaging().sendToInstance(instance,
                    texts.gameTexts().getHunterDeath(), Map.of());
            return;
        }
        texts.messaging().sendToInstance(instance, texts.gameTexts().getSpeedrunnerDeath(),
                Map.of("value", Integer.toString(runnersRemaining)));
    }

    /**
     * Remaining-runners phrase for the elimination line: a lone callout
     * at one, a plain count otherwise. Pure for tests.
     */
    static String remainingText(int runnersRemaining) {
        if (runnersRemaining == 1) {
            return "only 1 remains";
        }
        return runnersRemaining + " remain";
    }

    /** True for a kill of a teammate: same participant role, no suicides. Pure for tests. */
    static boolean isFriendlyFireKill(Role killerRole, Role victimRole, boolean selfKill) {
        return !selfKill && killerRole.isParticipant() && killerRole == victimRole;
    }

    /** Friendly fire line key for a roll in [0, 3). Pure for tests. */
    static String friendlyFireTemplate(GameMessages texts, int roll) {
        return switch (Math.floorMod(roll, FRIENDLY_FIRE_LINES)) {
            case 0 -> texts.getFriendlyFire1();
            case 1 -> texts.getFriendlyFire2();
            default -> texts.getFriendlyFire3();
        };
    }
}
