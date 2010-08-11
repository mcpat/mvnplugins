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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.doxia.sink.SinkEventAttributeSet;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.ModelInheritanceAssembler;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.sink.Sink;

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
     * @readonly
     * @parameter default-value="${reactorProjects}"
     * @since 1.0
     */
    private List<MavenProject> reactorProjects;
    
    /**
     * The Maven project builder.
     * 
     * @component
     * @required
     */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * The reference to the default model inheritance assembler.
     * 
     * @component
     * @required
     */
    private ModelInheritanceAssembler modelInheritanceAssembler;
    
    /**
     * The reference to the default model interpolator.
     * 
     * @component
     * @required
     */
    private ModelInterpolator modelInterpolator;
    
    /**
     * The list of remote artifact repositories.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List<ArtifactRepository> remoteRepositories;
    
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
            
            SinkEventAttributeSet atts= new SinkEventAttributeSet(1);
            atts.addAttribute(SinkEventAttributes.USEMAP, "#dependencies");
            sink.figureGraphics(getOutputName()+".png", atts);
            attachMapToSink(sink);
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
    
    @Override
    protected IVisualiserContext createVisualiserContext() {
        return new IVisualiserContext() {
            private final HashMap<String, String> _siteUrls= new HashMap<String, String>();
            
            public String getURL(Artifact artifact) {
                String key= artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion();
                if(!_siteUrls.containsKey(key)) {
                    Model model;
                    try {
                        model= getModelForArtifact(artifact);
                    } catch (Exception e) {
                        model= null;
                        e.printStackTrace();
                    }
                    
                    String url= model != null ? model.getUrl() : (String) null;
                    _siteUrls.put(key, url);
                }
                
                return _siteUrls.get(key);
            }
        };
    }

    @Override
    public File getTarget() {
        return new File(outputDirectory, getOutputName()+".png");
    }

    protected File getMapFile() {
        File tf= getTarget();
        return new File(tf.getParentFile(), tf.getName() + ".map");
    }
    
    private void attachMapToSink(Sink sink) throws MavenReportException {
        File mapFile= getMapFile();
        StringBuilder map= new StringBuilder();
        BufferedReader reader= null;
        try {
            reader= new BufferedReader(new InputStreamReader(new FileInputStream(mapFile)));
            while(reader.ready()) {
                map.append(reader.readLine());
            }
        } catch(IOException ioe) {
            throw new MavenReportException("could not attach image map", ioe);
        } finally {
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        
        sink.rawText(map.toString());
    }
    
    protected Model getModelForArtifact(Artifact artifact) throws ModelInterpolationException, ProjectBuildingException  {
        MavenProject mavenProject= mavenProjectBuilder.buildFromRepository(artifact, remoteRepositories, localRepository);
        return checkModel(mavenProject.getModel());
    }
    
    private Model checkModel(Model model) throws ModelInterpolationException, ProjectBuildingException {
        if(model.getParent() != null) {
            Parent parent= model.getParent();
            Artifact parentArt= artifactFactory.createArtifact(parent.getGroupId(), parent.getArtifactId(), parent.getVersion(), "compile", "pom");
            MavenProject parentProj= mavenProjectBuilder.buildFromRepository(parentArt, remoteRepositories, localRepository);
            Model parentModel= parentProj.getModel();
            
            if(parentModel.getParent() != null)
                parentModel= checkModel(parentModel);
            
            modelInheritanceAssembler.assembleModelInheritance(model, parentModel);
        }
        
        DefaultProjectBuilderConfiguration projectBuilderConfig= new DefaultProjectBuilderConfiguration();
        projectBuilderConfig.setExecutionProperties(model.getProperties());
        return modelInterpolator.interpolate(model, null, projectBuilderConfig, true);
    }
}
