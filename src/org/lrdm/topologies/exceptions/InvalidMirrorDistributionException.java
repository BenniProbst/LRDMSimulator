package org.lrdm.topologies.exceptions;

/**
 * Exception für ungültige Mirror-Verteilungen
 */
public class InvalidMirrorDistributionException extends SnowflakeTopologyException {
    
    private final int totalMirrors;
    private final int ringMirrors;
    private final int starMirrors;
    private final String reason;
    
    public InvalidMirrorDistributionException(int totalMirrors, int ringMirrors, int starMirrors, String reason) {
        super(String.format("Ungültige Mirror-Verteilung: Total=%d, Ringe=%d, Sterne=%d. Grund: %s", 
              totalMirrors, ringMirrors, starMirrors, reason));
        this.totalMirrors = totalMirrors;
        this.ringMirrors = ringMirrors;
        this.starMirrors = starMirrors;
        this.reason = reason;
    }
    
    public int getTotalMirrors() { return totalMirrors; }
    public int getRingMirrors() { return ringMirrors; }
    public int getStarMirrors() { return starMirrors; }
    public String getReason() { return reason; }
}