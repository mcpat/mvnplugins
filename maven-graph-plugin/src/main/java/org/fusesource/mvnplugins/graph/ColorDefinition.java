/*
 * $Id$ 
 */
package org.fusesource.mvnplugins.graph;


/**
 * @author Marcel Patzlaff
 * @version $Revision:$
 */
public class ColorDefinition {
    public String key;
    public String value;
    public String color;
    
    public boolean isFullySpecified() {
        return key != null && value != null && color != null;
    }
}
