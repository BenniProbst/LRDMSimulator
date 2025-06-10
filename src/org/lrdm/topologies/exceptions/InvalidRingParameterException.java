package org.lrdm.topologies.exceptions;

/**
 * Exception für ungültige Ring-Parameter
 */
public class InvalidRingParameterException extends SnowflakeTopologyException {
    
    private final String parameterName;
    private final Object actualValue;
    private final String constraint;
    
    public InvalidRingParameterException(String parameterName, Object actualValue, String constraint) {
        super(String.format("Ungültiger Ring-Parameter '%s': Wert '%s' verletzt Bedingung '%s'", 
              parameterName, actualValue, constraint));
        this.parameterName = parameterName;
        this.actualValue = actualValue;
        this.constraint = constraint;
    }
    
    public String getParameterName() { return parameterName; }
    public Object getActualValue() { return actualValue; }
    public String getConstraint() { return constraint; }
}