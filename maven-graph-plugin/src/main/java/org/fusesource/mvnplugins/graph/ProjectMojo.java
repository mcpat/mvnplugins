/**
 * Copyright (C) 2009 Progress Software, Inc.
 * http://fusesource.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.mvnplugins.graph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.ModelInheritanceAssembler;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;

/**
 * Generates a graph image of the dependencies of the project using the graphviz
 * tool 'dot'.  You must have the 'dot' executable installed and in your path
 * before using this goal.
 * <p/>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 * @goal project
 * @requiresDependencyResolution compile|test|runtime
 */
public class ProjectMojo extends AbstractMojo {

    /**
     * @required
     * @readonly
     * @parameter expression="${project}"
     * @since 1.0
     */
    protected MavenProject project;

    /**
     * @required
     * @readonly
     * @parameter expression="${localRepository}"
     * @since 1.0
     */
    protected ArtifactRepository localRepository;
    
    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * @required
     * @component
     * @since 1.0
     */
    protected ArtifactResolver artifactResolver;

    /**
     * @required
     * @readonly
     * @component
     * @since 1.0
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @required
     * @readonly
     * @component
     * @since 1.0
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * @required
     * @readonly
     * @component
     */
    protected ArtifactCollector artifactCollector;

    /**
     * @required
     * @readonly
     * @component
     * @since 1.0
     */
    protected DependencyTreeBuilder treeBuilder;
    
    /**
     * @readonly
     * @component
     * @required
     */
    protected MavenProjectBuilder mavenProjectBuilder;

    /**
     * @readonly
     * @component
     * @required
     */
    protected ModelInheritanceAssembler modelInheritanceAssembler;
    
    /**
     * @readonly
     * @component
     * @required
     */
    protected ModelInterpolator modelInterpolator;

    /**
     * The file the diagram will be written to.  Must use a file extension that the dot command supports or just the
     * '.dot' extension.
     * <br/>
     * @parameter default-value="${project.build.directory}/project-graph.png" expression="${graph.target}"
     */
    protected File target;

    /**
     * If set to true, omitted dependencies will not be drawn.  Dependencies are marked
     * as omitted if it would result in a resolution conflict.
     * <br/>
     * @parameter default-value="true" expression="${hide-omitted}"
     */
    protected boolean hideOmitted;

    /**
     * If set to true optional dependencies are not drawn.
     * <br/>
     * @parameter default-value="false" expression="${hide-optional}"
     */
    protected boolean hideOptional;

    /**
     * If set to true if dependencies external to the reactor project should be hidden.
     * <br/>
     * @parameter default-value="false" expression="${hide-external}"
     */
    protected boolean hideExternal;

    /**
     * If set to true pom dependencies are not drawn.
     * <br/>
     * @parameter default-value="true" expression="${hide-poms}"
     */
    protected boolean hidePoms;

    /**
     * A comma separated list of scopes.  Dependencies which
     * match the specified scopes will not be drawn.
     * <br/>
     * For example: <code>runtime,test</code>
     * <br/>
     * @parameter expression="${hide-scope}"
     */
    protected String hideScopes;

    /**
     * If set to true then dependencies not explicitly defined in the projects
     * pom will not be drawn.
     * <br/>
     * @parameter default-value="false" expression="${hide-transitive}"
     */
    protected boolean hideTransitive;

    /**
     * If set to true then the version label will not be drawn.
     * <br/>
     * @parameter default-value="false" expression="${hide-version}"
     */
    protected boolean hideVersion;

    /**
     * If set to true then the group id label will not be drawn.
     * <br/>
     * @parameter default-value="false" expression="${hide-group-id}"
     */
    protected boolean hideGroupId;

    /**
     * If set to true then the module type label will not be drawn.
     * <br/>
     * @parameter default-value="false" expression="${hide-type}"
     */
    protected boolean hideType;

    /**
     * If set to true then the intermediate dot file will not be deleted.
     * <br/>
     * @parameter default-value="false" expression="${keep-dot}"
     */
    protected boolean keepDot;

    /**
     * The label for the graph.
     * <br/>
     * @parameter default-value="Dependency Graph for ${project.name}" expression="${graph.label}"
     */
    protected String label;

    /**
     * If true then the 'test scope' and 'optional' attributes are cascaded
     * down to the dependencies of the original node. 
     *
     * <br/>
     * @parameter default-value="true" expression="${graph.cascade}"
     */
    protected boolean cascade;

    /**
     * The direction that the graph will be laid out in.
     * it can be one of the following values:
     * <br/>
     * <code>TB LR BT RL <code>
     * <br/>
     * top to bottom, from left to right, from bottom to top, and from right to left, respectively
     *
     * <br/>
     * @parameter default-value="TB" expression="${graph.direction}"
     */
    protected String direction;
    
    /**
     * @parameter 
     */
    protected ColorDefinition[] colorDefinitions;
    
    protected class ContextImpl implements IVisualiserContext {
        private final HashMap<String, ArtifactData> _data= new HashMap<String, ArtifactData>();
        
        public Color getCustomColor(Artifact artifact) {
            ArtifactData data= getData(artifact);
            return data != null ? data.color : (Color) null;
        }

        public String getURL(Artifact artifact) {
            ArtifactData data= getData(artifact);
            return data != null ? data.url : (String) null;
        }
        
        private ArtifactData getData(Artifact artifact) {
            String key= artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion();
            
            if(!_data.containsKey(key)) {
                _data.put(key, createArtifactData(artifact));
            }
            
            return _data.get(key);
        }
    }

    public void execute() throws MojoExecutionException {
getLog().info("COLOR DEFS: " + colorDefinitions);
        try {
            DependencyVisualizer visualizer = new DependencyVisualizer();
            visualizer.cascade = cascade;
            visualizer.direction = direction;
            visualizer.hideOptional = hideOptional;
            visualizer.hidePoms = hidePoms;
            visualizer.hideOmitted = hideOmitted;
            visualizer.hideExternal = hideExternal;
            visualizer.hideVersion = hideVersion;
            visualizer.hideGroupId = hideGroupId;
            visualizer.hideType = hideType;
            visualizer.keepDot = keepDot;
            visualizer.label = label;
            visualizer.hideTransitive = hideTransitive;
            visualizer.log = getLog();
            visualizer.context= createVisualiserContext();

            if (hideScopes != null) {
                for (String scope : hideScopes.split(",")) {
                    visualizer.hideScopes.add(scope);
                }
            }

            ArrayList<MavenProject> projects = new ArrayList<MavenProject>();
            collectProjects(projects);

            for (MavenProject p : projects) {
                ArtifactFilter filter = null;
                DependencyNode node = treeBuilder.buildDependencyTree(p, localRepository, artifactFactory, artifactMetadataSource, filter, artifactCollector);
                visualizer.add(node);
            }

            getTarget().getParentFile().mkdirs();
            visualizer.export(getTarget());
            getLog().info("Dependency graph exported to: " + getTarget());
        } catch (DependencyTreeBuilderException e) {
            throw new MojoExecutionException("Could not build the depedency tree.", e);
        }
    }

    protected void collectProjects(ArrayList<MavenProject> projects) {
        projects.add(project);
    }
    
    protected IVisualiserContext createVisualiserContext() {
        return new ContextImpl();
    }

    public File getTarget() {
        return target;
    }
    
    protected ArtifactData createArtifactData(Artifact artifact) {
        ArtifactData data;
        try {
            Model model= getModelForArtifact(artifact);
            data= new ArtifactData();
            data.url= model.getUrl();
            
            Properties props= model.getProperties();
            
            if(colorDefinitions != null) {
                for(int i= colorDefinitions.length; i >= 0; --i) {
                    ColorDefinition cd= colorDefinitions[i];
                    
                    if(!cd.isFullySpecified()) {
                        continue;
                    }
                    
                    String val= props.getProperty(cd.key);
                    
                    if(val != null && val.equals(cd.value)) {
                        data.color= Color.decode(cd.color);
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            data= null;
        }
        
        return data;
    }
    
    private Model getModelForArtifact(Artifact artifact) throws ModelInterpolationException, ProjectBuildingException  {
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