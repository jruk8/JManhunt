package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ReloadDiff;
import com.jruk8.jmanhunt.modifiers.files.ReloadReportComposer;
import com.jruk8.jmanhunt.modifiers.files.ReloadWords;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The reload verb: sync config reload plus async mods reload report. */
public final class ReloadUnit implements SubcommandUnit {
    private final JManhuntPlugin plugin;
    private final GameManager game;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public ReloadUnit(JManhuntPlugin plugin, GameManager game, ManhuntMessages texts,
            CommandSupport support) {
        this.plugin = plugin;
        this.game = game;
        this.texts = texts;
        this.support = support;
    }

    /** Reload takes no args; the record keeps the seam uniform. */
    public record ReloadArgs() {
    }

    public static ReloadArgs parse(String[] args) {
        return new ReloadArgs();
    }

    @Override public String primaryName() {
        return "reload";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        return executeParsed(sender, parse(args));
    }

    public boolean executeParsed(CommandSender sender, ReloadArgs args) {
        long started = System.nanoTime();
        plugin.reloadExceptModifiers();
        game.validateLobbyWorldName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ModLoadResult fresh = plugin.loadModsSnapshot();
            Bukkit.getScheduler().runTask(plugin, () -> applyReload(sender, fresh, started));
        });
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        return null;
    }

    /** Applies a background mods load on the main thread, then reports it. */
    private void applyReload(CommandSender sender, ModLoadResult fresh, long started) {
        if (!plugin.isEnabled()) {
            plugin.logger().info("Skipping mods reload: JManhunt is disabled.");
            return;
        }
        ReloadDiff diff = plugin.applyModifierReload(fresh);
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        support.message(sender, texts.getReloadSuccess(), Map.of("elapsed", String.valueOf(elapsed)));
        String bullet = texts.getReloadBullet();
        for (String line : ReloadReportComposer.compose(diff, fresh, reloadWords())) {
            sender.sendMessage(support.renderLiteral(bullet + line, Map.of()));
        }
        plugin.logger().info("JManhunt has been reloaded.");
        support.neutralSound(sender);
    }

    /** Resolves every reload report template with its schema default behind it. */
    private ReloadWords reloadWords() {
        return new ReloadWords(
                texts.getReloadChangesNew(),
                texts.getReloadChangesRemoved(),
                texts.getReloadChangesJoin(),
                texts.getReloadUnknown(),
                texts.getReloadFailed(),
                texts.getReloadDuplicate(),
                texts.getReloadItemModifier(),
                texts.getReloadItemModifiers(),
                texts.getReloadItemPreset(),
                texts.getReloadItemPresets(),
                texts.getReloadItemBoth(),
                texts.getReloadLabelUnknownFile(),
                texts.getReloadLabelUnknownFiles(),
                texts.getReloadLabelDuplicateId(),
                texts.getReloadLabelDuplicateIds(),
                texts.getReloadExtra());
    }
}
