package com.netherrack.server.world;

import java.util.Random;

/**
 * 2D Perlin noise in octaves, for terrain heights. One sample blends several octaves of
 * the same smooth field, each twice the frequency and a fraction of the size of the one
 * before, which turns plain noise into hills with valleys and little bumps on them.
 * <p>
 * The octave structure follows the layered noise generators of CloudburstMC Nukkit's
 * terrain generator, simplified to what a height map needs: one field, sampled wherever
 * the terrain wants a height. Every octave is derived from the same shuffled permutation
 * table, so one seed shapes the whole field.
 */
public final class PerlinNoise {

    private static final double[] GRADIENT_X = {1, -1, 1, -1, 0.7071, -0.7071, 0.7071, -0.7071};
    private static final double[] GRADIENT_Z = {0, 0, 1, -1, 0.7071, 0.7071, -0.7071, -0.7071};

    private final int[] permutation = new int[512];
    private final int octaves;
    private final double frequency;
    private final double persistence;
    private final double amplitude;

    /**
     * @param seed        what shapes the field; a different seed is a different world
     * @param octaves     how many layers are blended; more means finer bumps
     * @param frequency   the first octave's cycles per block - higher means smaller features
     * @param persistence how much smaller each octave is than the one before
     */
    public PerlinNoise(long seed, int octaves, double frequency, double persistence) {
        this.octaves = octaves;
        this.frequency = frequency;
        this.persistence = persistence;
        int[] source = new int[256];
        for (int i = 0; i < 256; i++) {
            source[i] = i;
        }
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int pick = random.nextInt(i + 1);
            int keep = source[i];
            source[i] = source[pick];
            source[pick] = keep;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = source[i & 255];
        }
        // The octaves' amplitudes together never pass 1, so the field stays roughly in
        // -1 to 1 whatever the octave count and persistence.
        double sum = 0;
        double octaveAmplitude = 1;
        for (int octave = 0; octave < octaves; octave++) {
            sum += octaveAmplitude;
            octaveAmplitude *= persistence;
        }
        this.amplitude = 1 / sum;
    }

    /** The noise at these world coordinates, roughly -1 to 1. */
    public double sample(double x, double z) {
        double sum = 0;
        double octaveAmplitude = 1;
        double octaveFrequency = frequency;
        for (int octave = 0; octave < octaves; octave++) {
            sum += octaveAmplitude * noise(x * octaveFrequency, z * octaveFrequency);
            octaveAmplitude *= persistence;
            octaveFrequency *= 2;
        }
        return sum * amplitude;
    }

    /** One octave of classic Perlin noise at these coordinates, roughly -1 to 1. */
    private double noise(double x, double z) {
        int cellX = floor(x);
        int cellZ = floor(z);
        double withinX = x - cellX;
        double withinZ = z - cellZ;
        double smoothX = fade(withinX);
        double smoothZ = fade(withinZ);
        double corner00 = gradient(cellX, cellZ, withinX, withinZ);
        double corner10 = gradient(cellX + 1, cellZ, withinX - 1, withinZ);
        double corner01 = gradient(cellX, cellZ + 1, withinX, withinZ - 1);
        double corner11 = gradient(cellX + 1, cellZ + 1, withinX - 1, withinZ - 1);
        return lerp(lerp(corner00, corner10, smoothX), lerp(corner01, corner11, smoothX), smoothZ) * 1.4;
    }

    /** The hashed gradient at a noise cell's corner, dotted with the offset towards the point. */
    private double gradient(int cellX, int cellZ, double offsetX, double offsetZ) {
        int gradient = permutation[(permutation[cellX & 255] + (cellZ & 255)) & 511] & 7;
        return GRADIENT_X[gradient] * offsetX + GRADIENT_Z[gradient] * offsetZ;
    }

    /** The curve Perlin noise follows across a cell, so gradients blend instead of cutting. */
    private static double fade(double offset) {
        return offset * offset * offset * (offset * (offset * 6 - 15) + 10);
    }

    private static double lerp(double from, double to, double by) {
        return from + (to - from) * by;
    }

    private static int floor(double value) {
        int whole = (int) value;
        return value < whole ? whole - 1 : whole;
    }
}
