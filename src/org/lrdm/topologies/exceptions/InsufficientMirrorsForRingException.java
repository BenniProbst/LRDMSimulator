package org.lrdm.topologies.exceptions;

/**
 * Exception für insufficient Mirrors für Ring-Konstruktion
 */
public class InsufficientMirrorsForRingException extends SnowflakeTopologyException {
    
    private final int availableMirrors;
    private final int requiredMirrors;
    private final int ringCount;
    
    public InsufficientMirrorsForRingException(int availableMirrors, int requiredMirrors, int ringCount) {
        super(String.format("Nicht genügend Mirrors für Ring-Konstruktion: Verfügbar=%d, Benötigt=%d für %d Ringe", 
              availableMirrors, requiredMirrors, ringCount));
        this.availableMirrors = availableMirrors;
        this.requiredMirrors = requiredMirrors;
        this.ringCount = ringCount;
    }
    
    public int getAvailableMirrors() { return availableMirrors; }
    public int getRequiredMirrors() { return requiredMirrors; }
    public int getRingCount() { return ringCount; }
}