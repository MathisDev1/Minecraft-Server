package de.citybuild.core.generator;

import de.citybuild.core.model.Plot;
import de.citybuild.core.model.RoadSegment;

import java.util.List;

/**
 * Callback interface for receiving progress events during city generation.
 *
 * <p>Implementations are typically responsible for relaying status messages to
 * the command sender who triggered {@code /cbsetup generate}.  All callback
 * methods are invoked from the async generation task; do <em>not</em> perform
 * any direct world access inside an implementation.</p>
 */
public interface GenerationProgressCallback {

    /**
     * Called at the start of each generation phase to provide live feedback.
     *
     * @param phase       the current phase number (1-based)
     * @param totalPhases total number of phases in this generation run
     * @param message     human-readable description of the current phase
     */
    void onProgress(int phase, int totalPhases, String message);

    /**
     * Called when the entire city generation has finished successfully.
     *
     * @param plots the list of all generated plots, ready for database persistence
     * @param roads the list of all road segments
     */
    void onComplete(List<Plot> plots, List<RoadSegment> roads);

    /**
     * Called when an unrecoverable error occurs during generation, aborting
     * the process.
     *
     * @param reason a human-readable description of the error
     */
    void onError(String reason);
}
