package org.lrdm.topologies.validators;

import org.lrdm.topologies.exceptions.*;

/**
 * Validator für Snowflake-Topologie-Parameter
 */
public class SnowflakeTopologyValidator {
    
    /**
     * Validiert alle Ring-Parameter
     */
    public static void validateRingParameters(/*int ringBridgeStepOnRing,
                                            int ringBridgeOffset, 
                                            int ringBridgeMirrorNumHeight, 
                                            int maxRingLayers, */
                                            int minimalRingMirrorCount) {
        
        /*validateRingBridgeStepOnRing(ringBridgeStepOnRing);
        validateRingBridgeOffset(ringBridgeOffset);
        validateRingBridgeMirrorNumHeight(ringBridgeMirrorNumHeight);
        validateMaxRingLayers(maxRingLayers);*/
        validateMinimalRingMirrorCount(minimalRingMirrorCount);
    }

    /**
     * Validiert alle Stern-Parameter
     */
    /*
    public static void validateStarParameters(int externStarMaxTreeDepth, 
                                            int bridgeToExternStarDistance, 
                                            double externStarRatio) {
        
        validateExternStarMaxTreeDepth(externStarMaxTreeDepth);
        validateBridgeToExternStarDistance(bridgeToExternStarDistance);
        validateExternStarRatio(externStarRatio);
    }
    */

    /**
     * Validiert Mirror-Verteilung
     */
    public static void validateMirrorDistribution(int totalMirrors, 
                                                int ringMirrors, 
                                                int starMirrors, 
                                                int minimalRingMirrorCount, 
                                                int maxRingLayers) {
        
        if (totalMirrors <= 0) {
            throw new InvalidMirrorDistributionException(totalMirrors, ringMirrors, starMirrors, 
                "Gesamtanzahl der Mirrors muss größer als 0 sein");
        }
        
        if (ringMirrors + starMirrors != totalMirrors) {
            throw new InvalidMirrorDistributionException(totalMirrors, ringMirrors, starMirrors, 
                "Summe der Ring- und Stern-Mirrors entspricht nicht der Gesamtanzahl");
        }
        
        if (ringMirrors < 0 || starMirrors < 0) {
            throw new InvalidMirrorDistributionException(totalMirrors, ringMirrors, starMirrors, 
                "Ring- und Stern-Mirrors müssen nicht-negativ sein");
        }
        
        // Prüfe minimale Requirements für Ringe
        int minRequiredForRings = minimalRingMirrorCount * maxRingLayers;
        if (ringMirrors > 0 && ringMirrors < minimalRingMirrorCount) {
            throw new InsufficientMirrorsForRingException(ringMirrors, minimalRingMirrorCount, 1);
        }
    }
    
    /**
     * Validiert Ring-Konstruktion
     */
    public static void validateRingConstruction(int numMirrorsToRings, 
                                              int minimalRingMirrorCount, 
                                              int maxRingLayers) {
        
        if (numMirrorsToRings < minimalRingMirrorCount && numMirrorsToRings > 0) {
            throw new InsufficientMirrorsForRingException(numMirrorsToRings, minimalRingMirrorCount, 1);
        }
        
        int safeRingCount = Math.max(1, numMirrorsToRings / minimalRingMirrorCount);
        if (safeRingCount > maxRingLayers) {
            throw new InvalidMirrorDistributionException(numMirrorsToRings, numMirrorsToRings, 0, 
                String.format("Benötigte Ring-Anzahl (%d) überschreitet Maximum (%d)", safeRingCount, maxRingLayers));
        }
    }
    
    // Private Validierungsmethoden für einzelne Parameter
    /*
    private static void validateRingBridgeStepOnRing(int value) {
        if (value < 0) {
            throw new InvalidRingParameterException("RING_BRIDGE_STEP_ON_RING", value, "muss mindestens 0 sein");
        }
    }
    
    private static void validateRingBridgeOffset(int value) {
        if (value < 0) {
            throw new InvalidRingParameterException("RING_BRIDGE_OFFSET", value, "muss mindestens 0 sein");
        }
    }
    
    private static void validateRingBridgeMirrorNumHeight(int value) {
        if (value < 1) {
            throw new InvalidRingParameterException("RING_BRIDGE_MIRROR_NUM_HEIGHT", value, "muss mindestens 1 sein");
        }
    }
    
    private static void validateMaxRingLayers(int value) {
        if (value < 1) {
            throw new InvalidRingParameterException("MAX_RING_LAYERS", value, "muss mindestens 1 sein");
        }
    }
     */
    
    private static void validateMinimalRingMirrorCount(int value) {
        if (value < 3) {
            throw new InvalidRingParameterException("MINIMAL_RING_MIRROR_COUNT", value, "muss mindestens 3 sein (Dreieck-Minimum)");
        }
    }

    /*
    private static void validateExternStarMaxTreeDepth(int value) {
        if (value < 1) {
            throw new InvalidStarParameterException("EXTERN_STAR_MAX_TREE_DEPTH", value, "muss mindestens 1 sein");
        }
    }

    private static void validateBridgeToExternStarDistance(int value) {
        if (value < 0) {
            throw new InvalidStarParameterException("BRIDGE_TO_EXTERN_STAR_DISTANCE", value, "muss mindestens 0 sein");
        }
    }

    private static void validateExternStarRatio(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new InvalidStarParameterException("EXTERN_STAR_RATIO", value, "muss zwischen 0.0 und 1.0 liegen");
        }
    }
    */
}