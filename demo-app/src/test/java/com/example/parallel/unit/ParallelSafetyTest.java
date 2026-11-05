package com.example.parallel.unit;

import com.hittech.paralleltest.guard.AbstractParallelSafetyTest;

/**
 * Static check of the whole test code base (runs with the unit tests, before any IT):
 * lists every parallel-safety violation at once, with rule id, location, why and fix.
 */
class ParallelSafetyTest extends AbstractParallelSafetyTest {
}
