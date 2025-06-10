package org.lrdm.topologies.exceptions;

/**
 * Basis-Exception für alle Snowflake-Topologie-Validierungsfehler
 */
public class SnowflakeTopologyException extends RuntimeException {
    
    public SnowflakeTopologyException(String message) {
        super(message);
    }
    
    public SnowflakeTopologyException(String message, Throwable cause) {
        super(message, cause);
    }
}