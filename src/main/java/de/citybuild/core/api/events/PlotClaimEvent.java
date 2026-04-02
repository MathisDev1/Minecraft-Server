package de.citybuild.core.api.events;

import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a player claims a {@link Plot}.
 *
 * <p>Add-on plugins can listen to this event to cancel the claim or adjust the
 * price.  The event is fired on the main server thread.</p>
 */
public class PlotClaimEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Plot   plot;
    private       double price;
    private       boolean cancelled;

    /**
     * Creates a new PlotClaimEvent.
     *
     * @param player the player attempting to claim the plot
     * @param plot   the plot being claimed
     * @param price  the calculated purchase price
     */
    public PlotClaimEvent(Player player, Plot plot, double price) {
        this.player = player;
        this.plot   = plot;
        this.price  = price;
    }

    /** @return the player who is claiming the plot */
    public Player getPlayer() { return player; }

    /** @return the plot being claimed */
    public Plot getPlot() { return plot; }

    /** @return the current purchase price (may be modified by listeners) */
    public double getPrice() { return price; }

    /**
     * Overrides the purchase price.
     *
     * @param price the new price
     */
    public void setPrice(double price) { this.price = price; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()              { return HANDLERS; }
    public static  HandlerList   getHandlerList()           { return HANDLERS; }
}
