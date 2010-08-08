/**
 *  Copyright (C) 2009, Progress Software Corporation and/or its
 * subsidiaries or affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.mvnplugins.graph;

import java.awt.RadialGradientPaint;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.sink.SinkEventAttributeSet;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringInputStream;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 *
 * @goal project-report
 * @requiresDependencyResolution compile|test|runtime
 */
public class ProjectReportMojo extends ProjectMojo implements MavenReport {


    /**
     * Output folder where the main page of the report will be generated. Note that this parameter is only relevant if
     * the goal is run directly from the command line or from the default lifecycle. If the goal is run indirectly as
     * part of a site generation, the output directory configured in the Maven Site Plugin will be used instead.
     *
     * @parameter expression="${project.reporting.outputDirectory}"
     * @required
     */
    private File outputDirectory;

    /**
     * @parameter default-value="true"
     * @required
     */
    private boolean generateMap;
    
    /**
     * @readonly
     * @parameter default-value="${reactorProjects}"
     * @since 1.0
     */
    private List<MavenProject> reactorProjects;


    public boolean canGenerateReport() {
        return true;
    }

    public String getOutputName() {
        return "dependency-graph";
    }

    public String getName(Locale locale) {
        return "Dependency Graph";
//        return getBundle( locale ).getString( "report.graph.name" );
    }

    public String getDescription(Locale locale) {
        return "Visual graph of the maven dependencies";
//        return getBundle( locale ).getString( "report.graph.description" );
    }

    public String getCategoryName() {
        return CATEGORY_PROJECT_INFORMATION;
    }

    public void setReportOutputDirectory(File file) {
        outputDirectory = file;
    }

    public File getReportOutputDirectory() {
        return outputDirectory;
    }

    public boolean isExternalReport() {
        return false;
    }

    public void generate(Sink sink, Locale locale) throws MavenReportException {
        try {
            getLog().info(project.getModules().toString() );
            if( project.getModules().size() > 1 && reactorProjects != null ) {
                hideExternal = true;
            }
            execute();
            
//            sink.figure();
            
            SinkEventAttributeSet atts= new SinkEventAttributeSet(1);
            if(isGenerateMap()) {
                atts.addAttribute(SinkEventAttributes.USEMAP, "#dependencies");
            }
            sink.figureGraphics(getOutputName()+".png", atts);
            
            if(isGenerateMap()) {
                File mapFile= getMapFile();
                StringBuilder map= new StringBuilder();
                try {
                    BufferedReader reader= new BufferedReader(new InputStreamReader(new FileInputStream(mapFile)));
                    
                    while(reader.ready()) {
                        map.append(reader.readLine());
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
                
                sink.rawText(map.toString());
            }
            
//            sink.figure_();
            
        } catch (MojoExecutionException e) {
            throw new MavenReportException("Could not generate graph.", e);
        }
    }

    @Override
    protected void collectProjects(ArrayList<MavenProject> projects) {
        super.collectProjects(projects);
        if ( project.getModules().size() > 1 && reactorProjects != null) {
            projects.addAll(reactorProjects);
        }
    }
    
    protected boolean isGenerateMap() {
        return generateMap && reactorProjects != null;
    }
    
    @Override
    protected URLCreator getURLCreator() {
        if(!isGenerateMap()) {
            return null;
        }
        
        final HashMap<String, MavenProject> reactorMap= new HashMap<String, MavenProject>();
        for(MavenProject mp : reactorProjects) {
            String key= mp.getGroupId()+":"+mp.getArtifactId()+":"+mp.getVersion();
            reactorMap.put(key, mp);
        }
        
        return new URLCreator() {
            public String getURL(Artifact artifact) {
                String key= artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion();
                MavenProject mp= reactorMap.get(key);
                
                if(mp != null) {
                    return mp.getFile().getAbsolutePath();
                }
                
                return null;
            }
        };
    }

    @Override
    public File getTarget() {
        return new File(outputDirectory, getOutputName()+".png");
    }

    protected File getMapFile() {
        File target= getTarget();
        return new File(target.getParentFile(), target.getName() + ".map");
    }
}
