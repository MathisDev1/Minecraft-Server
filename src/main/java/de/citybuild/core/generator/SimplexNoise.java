package de.citybuild.core.generator;

/**
 * A seeded 2D Simplex Noise implementation.
 *
 * <p>Based on Stefan Gustavson's public-domain reference implementation.
 * Returns values in the range [-1.0, 1.0].</p>
 */
public class SimplexNoise {

    /** Gradient vectors for 2D simplex corners. */
    private static final int[][] GRAD2 = {
        { 1, 1}, {-1, 1}, { 1,-1}, {-1,-1},
        { 1, 0}, {-1, 0}, { 0, 1}, { 0,-1}
    };

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    /** Permutation table (512 entries = two copies of 0-255). */
    private final short[] perm = new short[512];
    private final short[] permMod8 = new short[512];

    /**
     * Creates a new SimplexNoise with the given seed.
     *
     * @param seed random seed controlling the noise pattern
     */
    public SimplexNoise(long seed) {
        short[] source = new short[256];
        for (short i = 0; i < 256; i++) {
            source[i] = i;
        }
        // Fisher-Yates shuffle driven by the seed
        long s = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 255; i > 0; i--) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((s >>> 32) & 0xFFFFFFFFL) % (i + 1);
            if (j < 0) j += (i + 1);
            short tmp = source[i];
            source[i] = source[j];
            source[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = source[i & 255];
            permMod8[i] = (short) (perm[i] % 8);
        }
    }

    /**
     * Computes 2D simplex noise at coordinates (x, y).
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return noise value in the range [-1.0, 1.0]
     */
    public double noise(double x, double y) {
        // Skew the input space to determine which simplex cell we're in
        double s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);

        double t = (i + j) * G2;
        double x0 = x - (i - t);   // unskewed distances from cell origin
        double y0 = y - (j - t);

        // Determine which simplex triangle we're in
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else          { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        // Hashed gradient indices of the three simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod8[ii       + perm[jj      ]];
        int gi1 = permMod8[ii + i1  + perm[jj + j1 ]];
        int gi2 = permMod8[ii + 1   + perm[jj + 1  ]];

        // Contribution from corner 0
        double n0 = cornerContribution(gi0, x0, y0);
        // Contribution from corner 1
        double n1 = cornerContribution(gi1, x1, y1);
        // Contribution from corner 2
        double n2 = cornerContribution(gi2, x2, y2);

        // Scale to [-1, 1] — empirically derived constant for 2D simplex
        return 70.0 * (n0 + n1 + n2);
    }

    private double cornerContribution(int gi, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) return 0.0;
        t *= t;
        return t * t * dot(GRAD2[gi], x, y);
    }

    private static double dot(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
