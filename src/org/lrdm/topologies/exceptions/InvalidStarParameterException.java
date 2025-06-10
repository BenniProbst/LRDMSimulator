package org.lrdm.topologies.exceptions;

/**
 * Exception für ungültige Stern-Parameter
 */
public class InvalidStarParameterException extends SnowflakeTopologyException {
    
    private final String parameterName;
    private final Object actualValue;
    private final String constraint;
    
    public InvalidStarParameterException(String parameterName, Object actualValue, String constraint) {
        super(String.format("Ungültiger Stern-Parameter '%s': Wert '%s' verletzt Bedingung '%s'", 
              parameterName, actualValue, constraint));
        this.parameterName = parameterName;
        this.actualValue = actualValue;
        this.constraint = constraint;
    }
    
    public String getParameterName() { return parameterName; }
    public Object getActualValue() { return actualValue; }
    public String getConstraint() { return constraint; }
}