package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

/** Records Adventure messages; permission checks follow the factory used. */
@SuppressWarnings("deprecation")
final class FakeSender implements CommandSender {

    private final List<Component> received = new ArrayList<>();
    private final boolean permitted;

    private FakeSender(boolean permitted) {
        this.permitted = permitted;
    }

    /** Sender that fails every permission check. */
    static FakeSender denied() {
        return new FakeSender(false);
    }

    /** Sender that passes every permission check. */
    static FakeSender permitted() {
        return new FakeSender(true);
    }

    List<Component> received() {
        return received;
    }

    @Override
    public void sendMessage(String message) {
    }

    @Override
    public void sendMessage(String[] messages) {
    }

    @Override
    public void sendMessage(UUID sender, String message) {
    }

    @Override
    public void sendMessage(UUID sender, String[] messages) {
    }

    @Override
    public Server getServer() {
        return null;
    }

    @Override
    public String getName() {
        return "tester";
    }

    @Override
    public Spigot spigot() {
        return null;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return false;
    }

    @Override
    public boolean hasPermission(String name) {
        return permitted;
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return permitted;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return null;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return null;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return null;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return null;
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
    }

    @Override
    public void recalculatePermissions() {
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return Set.of();
    }

    @Override
    public boolean isOp() {
        return false;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public void sendMessage(Component message) {
        received.add(message);
    }

    @Override
    public Component name() {
        return Component.text("tester");
    }

    @Override
    public Audience filterAudience(Predicate<? super Audience> filter) {
        return this;
    }

    @Override
    public void forEachAudience(Consumer<? super Audience> action) {
        action.accept(this);
    }
}
