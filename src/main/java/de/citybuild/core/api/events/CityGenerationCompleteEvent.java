package de.citybuild.core.api.events;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CityGenerationCompleteEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final World world;
    private final int plotCount;
    private final long seed;
    private final long durationMillis;

    public CityGenerationCompleteEvent(World world, int plotCount, long seed, long durationMillis) {
        this.world = world;
        this.plotCount = plotCount;
        this.seed = seed;
        this.durationMillis = durationMillis;
    }

    public World getWorld() {
        return world;
    }

    public int getPlotCount() {
        return plotCount;
    }

    public long getSeed() {
        return seed;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
