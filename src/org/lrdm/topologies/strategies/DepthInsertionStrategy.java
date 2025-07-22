package org.lrdm.topologies.strategies;

/**
 * Enum für verschiedene Einfügungsstrategien bei tiefen-beschränkten Bäumen.
 */
public enum DepthInsertionStrategy {
    /** Bevorzugt tiefere Positionen zuerst (Depth-First) */
    DEPTH_FIRST,

    /** Bevorzugt breitere Verteilung (Breadth-First) */
    BREADTH_FIRST,

    /** Versucht eine ausgewogene Verteilung */
    BALANCED
}
