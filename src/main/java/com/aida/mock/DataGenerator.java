package com.aida.mock;

import java.time.LocalDate;

/** Builds the seeded demo dataset. Pure: same asOf and seed give the same data. Signature FROZEN; implemented in M1 (A1). */
public final class DataGenerator {

    private DataGenerator() {
    }

    public static Dataset generate(LocalDate asOf, long seed) {
        throw new UnsupportedOperationException("DataGenerator.generate: not implemented yet (M1, Person A)");
    }
}
