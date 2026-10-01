package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SpectatorMessages;
import com.jruk8.jmanhunt.message.SoundService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;

/**
 * Rechargeable spectator snowball: a fun projectile that never leaves
 * its toolbar slot, never deals damage, and never applies knockback.
 * The thrown snowball is a plain projectile entity, so participants
 * and spectators all see it.
 */
public class SpectatorSnowballService {

    /** Projectile metadata tagging spectator snowballs. */
    public static final String TAG = "jmanhunt-spectator-snowball";

    private final TaskScheduler tasks;
    private final SpectatorToolbarService toolbar;
    private final MessageService messages;
    private final SpectatorMessages spectator;
    private final SoundService sounds;

    public SpectatorSnowballService(TaskScheduler tasks, SpectatorToolbarService toolbar,
            MessageService messages, SpectatorMessages spectator, SoundService sounds) {
        this.tasks = tasks;
        this.toolbar = toolbar;
        this.messages = messages;
        this.spectator = spectator;
        this.sounds = sounds;
    }

    /** True for projectiles thrown from the spectator snowball slot. */
    public static boolean isTaggedSnowball(Projectile projectile) {
        return projectile != null && projectile.hasMetadata(TAG);
    }

    /** Recharge time in ticks, from configured seconds. Pure for tests. */
    static int cooldownTicks(int cooldownSeconds) {
        return Math.max(0, cooldownSeconds) * 20;
    }

    /**
     * Handles a launched projectile: spectator snowball throws are
     * tagged and restored to their layout slot. The item-native use
     * cooldown blocks honest clients before a launch exists; the
     * material gate below is the server-side backstop. Everything else
     * passes through untouched.
     */
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        if (!(snowball.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!toolbar.isDeployed(shooter)) {
            return;
        }
        if (!toolbar.snowballEnabled(shooter)) {
            return;
        }
        restoreSnowball(shooter);
        snowball.setMetadata(TAG, new FixedMetadataValue(tasks.plugin(), true));
        int ticks = cooldownTicks(toolbar.snowballCooldownSeconds(shooter));
        if (ticks <= 0) {
            return;
        }
        if (shooter.hasCooldown(Material.SNOWBALL)) {
            event.setCancelled(true);
            int seconds = Math.max(1, (shooter.getCooldown(Material.SNOWBALL) + 19) / 20);
            messages.messageRaw(shooter, spectator.getSnowballCooldown(),
                    Map.of("seconds", String.valueOf(seconds)));
            sounds.playNeutralSound(shooter);
            return;
        }
        shooter.setCooldown(Material.SNOWBALL, ticks);
    }

    /**
     * Cancels all damage from tagged spectator snowballs: players, mobs,
     * end crystals, armor stands, item frames, vehicles, everything.
     * Cancelling the damage event also cancels the knockback vanilla
     * applies through it, so no velocity is ever written.
     */
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball snowball && isTaggedSnowball(snowball)) {
            event.setCancelled(true);
        }
    }

    /**
     * Restores the snowball to its layout slot one tick after the throw:
     * vanilla consumes the item after the launch event, so a synchronous
     * write would be eaten. Shooters who logged out or undeployed before
     * the tick get nothing back.
     */
    private void restoreSnowball(Player shooter) {
        int slot = SpectatorToolbarService.snowballSlot(toolbar.layout(shooter));
        if (slot < 0) {
            return;
        }
        int seconds = toolbar.snowballCooldownSeconds(shooter);
        tasks.run(() -> {
            if (!shooter.isOnline() || !toolbar.isDeployed(shooter)) {
                return;
            }
            shooter.getInventory().setItem(slot, toolbar.snowballItem(seconds));
        });
    }
}
