package de.citybuild.core.api.events;

import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class PlotTrustEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    
    private final Player owner;
    private final Plot plot;
    private final UUID trustedPlayer;
    private final String trustedPlayerName;

    public PlotTrustEvent(Player owner, Plot plot, UUID trustedPlayer, String trustedPlayerName) {
        this.owner = owner;
        this.plot = plot;
        this.trustedPlayer = trustedPlayer;
        this.trustedPlayerName = trustedPlayerName;
    }

    public Player getOwner() {
        return owner;
    }

    public Plot getPlot() {
        return plot;
    }

    public UUID getTrustedPlayer() {
        return trustedPlayer;
    }

    public String getTrustedPlayerName() {
        return trustedPlayerName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
