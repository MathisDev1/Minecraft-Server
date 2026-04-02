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

        // ── Phase 1: Terrain ──────────────────────────────────────────────────
        progress(callback, 1, "Forming terrain...");
        runOnMainThread(() -> terrain.generateTerrain(world, radius));

        // ── Phase 2: Rivers ───────────────────────────────────────────────────
        progress(callback, 2, "Carving rivers...");
        runOnMainThread(() -> water.generateRivers(world, radius));

        // ── Phase 3: Lakes ────────────────────────────────────────────────────
        progress(callback, 3, "Digging lakes...");
        runOnMainThread(() -> water.generateLakes(world, radius));

        // ── Phase 4: Road layout (pure computation, async) ───────────────────
        progress(callback, 4, "Planning road network...");
        List<RoadSegment> roads = roadGen.generateRoads(radius);
        log.info("[CityGenerator] Road layout complete: " + roads.size() + " segments.");

        // ── Phase 5: Road blocks (main thread) ───────────────────────────────
        progress(callback, 5, "Paving roads...");
        runOnMainThread(() -> roadGen.placeRoadBlocks(world, roads));

        // ── Phase 6: Plot subdivision (pure computation, async) ───────────────
        progress(callback, 6, "Subdividing plots...");
        List<Plot> plots = plotter.subdivide(world, radius);
        log.info("[CityGenerator] Plot layout complete: " + plots.size() + " plots.");

        // ── Phase 7: Plot borders (main thread) ──────────────────────────────
        progress(callback, 7, "Placing plot borders...");
        runOnMainThread(() -> plotter.placePlotBorders(world, plots));

        // ── Done ──────────────────────────────────────────────────────────────
        log.info("[CityGenerator] City generation complete.");
        callback.onComplete(plots, roads);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Dispatches a block-placement task to the main thread and blocks the
     * calling async thread until the task has finished.
     *
     * @param task block-placement logic to execute on the main thread
     * @throws InterruptedException if the async thread is interrupted while waiting
     */
    private void runOnMainThread(Runnable task) throws InterruptedException {
        Object lock  = new Object();
        boolean[] done = {false};

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
            } finally {
                synchronized (lock) {
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            while (!done[0]) {
                lock.wait();
            }
        }
    }

    /** Sends a progress event to the callback. */
    private void progress(GenerationProgressCallback callback, int phase, String message) {
        log.info("[CityGenerator] Phase " + phase + "/" + TOTAL_PHASES + " — " + message);
        callback.onProgress(phase, TOTAL_PHASES, message);
    }
}
