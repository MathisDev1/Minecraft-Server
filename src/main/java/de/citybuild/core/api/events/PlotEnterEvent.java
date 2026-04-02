package de.citybuild.core.api.events;

import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotEnterEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final Plot plot;
    private final Plot previousPlot;

    public PlotEnterEvent(Player player, Plot plot, Plot previousPlot) {
        this.player = player;
        this.plot = plot;
        this.previousPlot = previousPlot;
    }

    public Player getPlayer() {
        return player;
    }

    public Plot getPlot() {
        return plot;
    }

    public Plot getPreviousPlot() {
        return previousPlot;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
