package de.citybuild.core.generator;

import de.citybuild.core.model.Plot;
import de.citybuild.core.model.RoadSegment;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for CityBuild 2.0 generation.
 */
public class CityGenerator {

    private static final int DEFAULT_RADIUS = 250;
    private static final int TOTAL_PHASES   = 4; // Simplified: Terrain, Layout, Roads, Plots

    private final JavaPlugin plugin;
    private final long       seed;
    private final Logger     log;

    public CityGenerator(JavaPlugin plugin, long seed) {
        this.plugin = plugin;
        this.seed   = seed;
        this.log    = plugin.getLogger();
    }

    public void generate(World world, GenerationProgressCallback callback) {
        int radius = plugin.getConfig().getInt("generator.map-radius", DEFAULT_RADIUS);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                runGeneration(world, radius, callback);
            } catch (Exception e) {
                log.severe("[CityGenerator] Unhandled exception: " + e.getMessage());
                callback.onError("Internal error: " + e.getMessage());
            }
        });
    }

    private void runGeneration(World world, int radius, GenerationProgressCallback callback) throws InterruptedException {

        // 1. Instantiate sub-generators
        TerrainGenerator terrain = new TerrainGenerator(seed);
        RoadGenerator    roadGen = new RoadGenerator(seed ^ 0xDEADF00DL, terrain);
        PlotSubdivider   plotter = new PlotSubdivider(seed ^ 0xC0FFEE00L, roadGen);

        // Phase 1: Terrain (0-30%)
        progress(callback, 1, 0, "Generiere flaches Terrain...");
        runBatched(terrain.getChunkTasks(world, radius), callback, 1, 0, 30);

        // Phase 2: Planning (30-45%)
        progress(callback, 2, 30, "Plane Straßennetz und Grundstücke...");
        List<RoadSegment> roads = roadGen.generateRoads(radius);
        List<Plot> plots = plotter.subdivide(world, radius);
        progress(callback, 2, 45, "Planung abgeschlossen.");

        // Phase 3: Roads (45-75%)
        progress(callback, 3, 45, "Baue Straßen...");
        runBatched(roadGen.getPlacementTasks(world, roads), callback, 3, 45, 75);

        // Phase 4: Plots (75-100%)
        progress(callback, 4, 75, "Setze Grundstückszäune und Verkaufsschilder...");
        runBatched(plotter.getBorderTasks(world, plots), callback, 4, 75, 100);

        log.info("[CityGenerator] City generation complete.");
        callback.onComplete(plots, roads);
    }

    private void progress(GenerationProgressCallback callback, int phase, int percentage, String message) {
        log.info("[CityGenerator] Phase " + phase + "/" + TOTAL_PHASES + " (" + percentage + "%) — " + message);
        callback.onProgress(phase, TOTAL_PHASES, message);
    }

    private void runBatched(List<Runnable> tasks, GenerationProgressCallback callback, 
                            int phase, int startPct, int endPct) throws InterruptedException {
        if (tasks.isEmpty()) return;

        int totalTasks = tasks.size();
        int tasksPerTick = 12; // Flat world is faster, can do more tasks
        int[] completed = {0};
        Object lock = new Object();
        boolean[] finished = {false};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            for (int i = 0; i < tasksPerTick && completed[0] < totalTasks; i++) {
                try {
                    tasks.get(completed[0]).run();
                } catch (Exception e) {
                    log.severe("[CityGenerator] Error in batched task: " + e.getMessage());
                }
                completed[0]++;
            }

            int currentPct = startPct + (int) ((endPct - startPct) * ((double) completed[0] / totalTasks));
            if (completed[0] % 20 == 0 || completed[0] >= totalTasks) {
                callback.onProgress(phase, TOTAL_PHASES, "Fortschritt: " + currentPct + "% (" + completed[0] + "/" + totalTasks + ")");
            }

            if (completed[0] >= totalTasks) {
                synchronized (lock) {
                    finished[0] = true;
                    lock.notifyAll();
                }
                task.cancel();
            }
        }, 1L, 1L); // Run every tick for speed

        synchronized (lock) {
            while (!finished[0]) {
                lock.wait();
            }
        }
    }
}
