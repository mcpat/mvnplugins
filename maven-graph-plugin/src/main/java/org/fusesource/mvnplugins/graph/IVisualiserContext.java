/*
 * $Id$
 */
package org.fusesource.mvnplugins.graph;

import org.apache.maven.artifact.Artifact;

/**
 * @author Marcel Patzlaff
 * @version $Revision:$
 */
public interface IVisualiserContext {
    String getURL(Artifact artifact);
}
