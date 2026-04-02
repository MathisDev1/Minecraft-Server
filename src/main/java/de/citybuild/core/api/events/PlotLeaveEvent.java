package de.citybuild.core.api.events;

import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotLeaveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final Plot plot;
    private final Plot nextPlot;

    public PlotLeaveEvent(Player player, Plot plot, Plot nextPlot) {
        this.player = player;
        this.plot = plot;
        this.nextPlot = nextPlot;
    }

    public Player getPlayer() {
        return player;
    }

    public Plot getPlot() {
        return plot;
    }

    public Plot getNextPlot() {
        return nextPlot;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
