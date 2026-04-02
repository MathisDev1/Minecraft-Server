package de.citybuild.core.model;

/**
 * Represents one rectangular road segment on the city map.
 *
 * <p>Coordinates describe the axis-aligned bounding box of the road surface.
 * The {@code type} field determines block materials and visual style.</p>
 *
 * @param minX  minimum world X coordinate (inclusive)
 * @param minZ  minimum world Z coordinate (inclusive)
 * @param maxX  maximum world X coordinate (inclusive)
 * @param maxZ  maximum world Z coordinate (inclusive)
 * @param width road width in blocks (used for lamp / crossing calculations)
 * @param type  classification of this road segment
 */
public record RoadSegment(
    int minX,
    int minZ,
    int maxX,
    int maxZ,
    int width,
    RoadType type
) {

    /**
     * Classifies road segments by hierarchy, which controls block materials and width.
     */
    public enum RoadType {
        /** Primary arterial road connecting district centres (widest, concrete). */
        MAIN,
        /** Secondary road branching from main roads (medium width, stone bricks). */
        SECONDARY,
        /** Tertiary connector between secondary endpoints (narrow, cobblestone). */
        TERTIARY
    }

    /**
     * Returns the centre X coordinate of this segment.
     *
     * @return centre X
     */
    public int centerX() {
        return (minX + maxX) / 2;
    }

    /**
     * Returns the centre Z coordinate of this segment.
     *
     * @return centre Z
     */
    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    /**
     * Returns {@code true} when the given world position falls inside this
     * segment's bounding box.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return whether (x, z) is within this road segment
     */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
