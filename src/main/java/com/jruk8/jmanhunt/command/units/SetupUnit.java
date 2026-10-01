package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.menus.ManhuntMenus;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.setup.SetupService;
import com.jruk8.jmanhunt.tutorial.TutorialService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** The setup verb plus bare-command GUI routing. */
public final class SetupUnit implements SubcommandUnit {
    public record SetupDeps(GuiService gui, TutorialService tutorial, SetupService setupService,
            ManhuntMenus menus, BooleanSupplier setupDone, Runnable markSetupDone) {
    }

    public record SetupTexts(CommandMessages command, CommandSupport support) {
    }

    private final GuiService gui;
    private final TutorialService tutorial;
    private final SetupService setupService;
    private final ManhuntMenus menus;
    private final BooleanSupplier setupDone;
    private final Runnable markSetupDone;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public SetupUnit(SetupDeps deps, SetupTexts texts) {
        this.gui = deps.gui();
        this.tutorial = deps.tutorial();
        this.setupService = deps.setupService();
        this.menus = deps.menus();
        this.setupDone = deps.setupDone();
        this.markSetupDone = deps.markSetupDone();
        this.commandTexts = texts.command();
        this.support = texts.support();
    }

    /** Setup takes no args; the record keeps the seam uniform. */
    public record SetupArgs() {
    }

    public static SetupArgs parse(String[] args) {
        return new SetupArgs();
    }

    @Override public String primaryName() {
        return "setup";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        return executeParsed(sender, parse(args));
    }

    public boolean executeParsed(CommandSender sender, SetupArgs args) {
        if (!(sender instanceof Player player)) {
            return support.message(sender, commandTexts.getPlayerOnly());
        }
        markSetupDone.run();
        tutorial.start(player);
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        return null;
    }

    /**
     * Bare-command GUI routing for the router pre-dispatch: opens the
     * GUI and returns true, or returns false to continue dispatch.
     */
    public boolean routeGui(CommandSender sender, String[] args) {
        if (!opensGui(args, sender)) {
            return false;
        }
        openGui((Player) sender);
        return true;
    }

    /**
     * Bare /manhunt opens the admin GUI, but only for players holding
     * the GUI node. Everyone else, including the console, gets status.
     */
    public static boolean opensGui(String[] args, CommandSender sender) {
        return args.length == 0 && sender instanceof Player
                && sender.hasPermission("jmanhunt.gui");
    }

    /**
     * Opens the root GUI, showing the Setup First panel once when the
     * global flag is unset. Confirm runs the one-click recommended
     * setup; Cancel closes the GUI and runs /mh setup through the
     * same permission and player-only checks as the command.
     */
    private boolean openGui(Player player) {
        gui.clearOverrideLobby(player);
        if (!setupDone.getAsBoolean()) {
            gui.open(player, menus.setupFirstMenu(
                    confirmed -> {
                        confirmed.closeInventory();
                        setupService.recommendedSetup(confirmed);
                    },
                    skipped -> {
                        skipped.closeInventory();
                        if (!ManhuntCommand.canUseSubcommand(skipped, "setup")) {
                            support.message(skipped, commandTexts.getNoPermission());
                            return;
                        }
                        executeParsed(skipped, new SetupArgs());
                    }));
            support.neutralSound(player);
            return true;
        }
        gui.open(player, menus.rootMenu(player));
        support.neutralSound(player);
        return true;
    }
}
