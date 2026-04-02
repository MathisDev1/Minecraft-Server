package de.citybuild.core.commands;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.generator.CityGenerator;
import de.citybuild.core.generator.GenerationProgressCallback;
import de.citybuild.core.model.Plot;
import de.citybuild.core.model.RoadSegment;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;

/**
 * Handles the {@code /cbsetup generate [seed]} command.
 *
 * <p>Creates the city world with an empty chunk generator, then delegates to
 * {@link CityGenerator} which runs asynchronously and reports progress
 * via action-bar messages.</p>
 */
public class SetupCommand implements CommandExecutor {

    private final CityBuildCore    plugin;
    private volatile boolean       generating = false;

    /**
     * Creates a new SetupCommand.
     *
     * @param plugin the owning plugin instance
     */
    public SetupCommand(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("citybuild.admin")) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + plugin.getCoreConfig().msg("no-permission"));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("generate")) {
            sender.sendMessage("§6Verwendung: §e/cbsetup generate [seed]");
            return true;
        }

        if (generating) {
            sender.sendMessage("§cEine Generierung läuft bereits.");
            return true;
        }

        long seed;
        if (args.length >= 2) {
            try {
                seed = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                seed = args[1].hashCode();
            }
        } else {
            long cfgSeed = plugin.getCoreConfig().getWorldSeed();
            seed = cfgSeed != 0 ? cfgSeed : System.currentTimeMillis();
        }

        final long finalSeed = seed;
        sender.sendMessage("§6Erstelle Welt mit Seed §e" + finalSeed + "§6...");
        generating = true;

        // World creation must happen on the main thread
        WorldCreator creator = new WorldCreator(plugin.getCoreConfig().getWorldName());
        creator.seed(finalSeed);
        creator.generator(new EmptyChunkGenerator());
        World world = creator.createWorld();

        if (world == null) {
            sender.sendMessage("§cFehler: Welt konnte nicht erstellt werden.");
            generating = false;
            return true;
        }

        world.setAutoSave(true);

        CityGenerator generator = new CityGenerator(plugin, finalSeed);
        generator.generate(world, new GenerationProgressCallback() {

            @Override
            public void onProgress(int phase, int totalPhases, String message) {
                int    percent = (int) ((phase / (double) totalPhases) * 100);
                String bar     = "§6[Phase " + phase + "/" + totalPhases + "] §e"
                    + message + " §7(" + percent + "%)";
                if (sender instanceof Player p) {
                    p.sendActionBar(Component.text(bar));
                } else {
                    sender.sendMessage(bar);
                }
            }

            @Override
            public void onComplete(List<Plot> plots, List<RoadSegment> roads) {
                generating = false;

                plugin.getPlotManager().bulkRegisterPlots(plots);
                plugin.getPlotRepository().bulkInsertPlots(plots);

                World w = Bukkit.getWorld(plugin.getCoreConfig().getWorldName());
                if (w != null) {
                    w.setSpawnLocation(0, 65, 0);
                }

                String msg = "§aGenerierung abgeschlossen! §e" + plots.size()
                    + " §aGrundstücke erstellt.";

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(msg);
                    if (sender instanceof Player p && w != null) {
                        p.teleport(new Location(w, 0.5, 66, 0.5));
                    }
                });
            }

            @Override
            public void onError(String reason) {
                generating = false;
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage("§cFehler bei der Generierung: " + reason));
            }
        });

        return true;
    }

    // =========================================================================
    // Empty chunk generator
    // =========================================================================

    /**
     * Provides empty (air-only) chunks so that {@link CityGenerator} has a clean slate
     * when placing terrain, roads and plot borders.
     */
    public static class EmptyChunkGenerator extends ChunkGenerator {

        @Override
        public boolean shouldGenerateNoise()       { return false; }

        @Override
        public boolean shouldGenerateSurface()     { return false; }

        @Override
        public boolean shouldGenerateBedrock()     { return false; }

        @Override
        public boolean shouldGenerateDecorations() { return false; }

        @Override
        public boolean shouldGenerateMobs()        { return false; }

        @Override
        public boolean shouldGenerateCaves()       { return false; }

        @Override
        public boolean shouldGenerateStructures()  { return false; }
    }
}
