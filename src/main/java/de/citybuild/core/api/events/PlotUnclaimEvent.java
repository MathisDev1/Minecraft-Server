package de.citybuild.core.api.events;

import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a player unclaims (gives back) their {@link Plot}.
 *
 * <p>Add-on plugins can listen to this event to cancel the unclaim.
 * The event is fired on the main server thread.</p>
 */
public class PlotUnclaimEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player  player;
    private final Plot    plot;
    private       boolean cancelled;

    /**
     * Creates a new PlotUnclaimEvent.
     *
     * @param player the player giving up the plot
     * @param plot   the plot being unclaimed
     */
    public PlotUnclaimEvent(Player player, Plot plot) {
        this.player = player;
        this.plot   = plot;
    }

    /** @return the player who is unclaiming the plot */
    public Player getPlayer() { return player; }

    /** @return the plot being unclaimed */
    public Plot getPlot() { return plot; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()        { return HANDLERS; }
    public static  HandlerList   getHandlerList()     { return HANDLERS; }
}
