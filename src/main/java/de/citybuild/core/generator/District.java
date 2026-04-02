package de.citybuild.core.generator;

/**
 * Represents a city district center used for city planning and plot classification.
 */
public class District {
    private final int id;
    private final int x;
    private final int z;
    private final String name;

    public District(int id, int x, int z, String name) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getName() {
        return name;
    }
}
