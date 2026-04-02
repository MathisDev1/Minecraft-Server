package de.citybuild.core.generator;

import de.citybuild.core.model.Plot;
import de.citybuild.core.model.RoadSegment;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for CityBuild city generation.
 *
 * <p>Executes all seven generation phases sequentially.  Computation-heavy
 * phases run asynchronously; every {@code world.getBlockAt().setType()} call
 * is dispatched to the main server thread via
 * {@link org.bukkit.scheduler.BukkitScheduler#runTask(JavaPlugin, Runnable)}.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * CityGenerator gen = new CityGenerator(plugin, seed);
 * gen.generate(world, (phase, total, msg) -> sender.sendMessage("[" + phase + "/" + total + "] " + msg),
 *              plots  -> db.savePlots(plots),
 *              roads  -> db.saveRoads(roads));
 * }</pre>
 */
public class CityGenerator {

    /** Default map radius in blocks (can be overridden by config). */
    private static final int DEFAULT_RADIUS = 1500;
    private static final int TOTAL_PHASES   = 7;

    private final JavaPlugin       plugin;
    private final long             seed;
    private final Logger           log;

    /**
     * Creates a new CityGenerator.
     *
     * @param plugin the owning plugin instance (used for scheduler access)
     * @param seed   master seed — all sub-generators derive their seeds from this
     */
    public CityGenerator(JavaPlugin plugin, long seed) {
        this.plugin = plugin;
        this.seed   = seed;
        this.log    = plugin.getLogger();
    }

    /**
     * Starts the full city generation pipeline asynchronously.
     *
     * <p>The method returns immediately; all work is done in a Bukkit async
     * task.  The {@code callback} receives progress events and is notified on
     * completion or error.  Block placements are automatically scheduled back
     * on the main thread.</p>
     *
     * @param world    the target world (must already exist and be loaded)
     * @param callback receiver for progress, completion, and error events
     */
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

    // =========================================================================
    // Internal pipeline
    // =========================================================================

    /**
     * Executes all seven phases in order.  Called from the async task started
     * by {@link #generate(World, GenerationProgressCallback)}.
     */
    private void runGeneration(World world, int radius,
                                GenerationProgressCallback callback) throws InterruptedException {

        // ── Instantiate sub-generators (cheap, no world access) ───────────────
        TerrainGenerator terrain = new TerrainGenerator(seed);
        WaterGenerator   water   = new WaterGenerator(seed ^ 0xBEEFCAFEL, terrain);
        RoadGenerator    roadGen = new RoadGenerator(seed ^ 0xDEADF00DL, terrain, water);
        PlotSubdivider   plotter = new PlotSubdivider(seed ^ 0xC0FFEE00L, roadGen, water);

        // ── Phase 1: Terrain (0–25%) ──────────────────────────────────────────
        progress(callback, 1, 0, "Pre-calculating heightmap...");
        terrain.precalculateHeightMap(radius); // Async compute

        progress(callback, 1, 5, "Forming terrain (batched)...");
        runBatched(terrain.getChunkTasks(world, radius), callback, 1, 0, 25);

        // ── Phase 2: Rivers (25–35%) ──────────────────────────────────────────
        progress(callback, 2, 25, "Planning and carving rivers...");
        runBatched(water.getRiverTasks(world, radius), callback, 2, 25, 35);

        // ── Phase 3: Lakes (35–42%) ───────────────────────────────────────────
        progress(callback, 3, 35, "Planning and digging lakes...");
        runBatched(water.getLakeTasks(world, radius), callback, 3, 35, 42);

        // ── Phase 4: Road layout (42–60%, compute, async) ───────────────────
        progress(callback, 4, 42, "Planning road network...");
        List<RoadSegment> roads = roadGen.generateRoads(radius);
        log.info("[CityGenerator] Road layout complete: " + roads.size() + " segments.");

        // ── Phase 5: Road blocks (60–75%, main thread) ───────────────────────
        progress(callback, 5, 60, "Paving roads (batched)...");
        runBatched(roadGen.getPlacementTasks(world, roads), callback, 5, 60, 75);

        // ── Phase 6: Plot subdivision (75–92%, compute, async) ───────────────
        progress(callback, 6, 75, "Subdividing plots...");
        List<Plot> plots = plotter.subdivide(world, radius);
        log.info("[CityGenerator] Plot layout complete: " + plots.size() + " plots.");

        // ── Phase 7: Plot borders (92–100%, main thread) ─────────────────────
        progress(callback, 7, 92, "Placing plot borders (batched)...");
        runBatched(plotter.getBorderTasks(world, plots), callback, 7, 92, 100);

        // ── Done ──────────────────────────────────────────────────────────────
        log.info("[CityGenerator] City generation complete.");
        callback.onComplete(plots, roads);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Sends a progress event to the callback. */
    private void progress(GenerationProgressCallback callback, int phase, int percentage, String message) {
        log.info("[CityGenerator] Phase " + phase + "/" + TOTAL_PHASES + " (" + percentage + "%) — " + message);
        callback.onProgress(phase, TOTAL_PHASES, message);
    }

    /**
     * Executes a list of tasks on the main thread in batches.
     * Processes 10 tasks per tick by default to keep the server responsive.
     */
    private void runBatched(List<Runnable> tasks, GenerationProgressCallback callback, 
                            int phase, int startPct, int endPct) throws InterruptedException {
        if (tasks.isEmpty()) return;

        int totalTasks = tasks.size();
        int tasksPerTick = 10; // Adjust based on performance
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

            if (completed[0] >= totalTasks) {
                synchronized (lock) {
                    finished[0] = true;
                    lock.notifyAll();
                }
                task.cancel();
            }
        }, 1L, 1L);

        synchronized (lock) {
            while (!finished[0]) {
                lock.wait();
            }
        }
    }
}
