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
package org.fusesource.mvnplugins.avro;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.avro.specific.SpecificCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * A Maven Mojo so that the Avro compiler can be used with maven.
 * 
 * @goal compile
 * @phase process-sources 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class AvroMojo extends AbstractMojo {

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory where the Avro files (<code>*.avpr</code> or <code>*.avsc</code>) are
     * located.
     * 
     * @parameter default-value="${basedir}/src/main/avro"
     */
    private File mainSourceDirectory;

    /**
     * The directory where the output files will be located.
     * 
     * @parameter default-value="${project.build.directory}/generated-sources/avro"
     */
    private File mainOutputDirectory;
    
    /**
     * The directory where the Avro files (<code>*.avpr</code> or <code>*.avsc</code>) are
     * located.
     * 
     * @parameter default-value="${basedir}/src/test/avro"
     */
    private File testSourceDirectory;

    /**
     * The directory where the output files will be located.
     * 
     * @parameter default-value="${project.build.directory}/test-generated-sources/avro"
     */
    private File testOutputDirectory;

    public void execute() throws MojoExecutionException {

        File[] mainFiles = null;
        if ( mainSourceDirectory.exists() ) {
            mainFiles = mainSourceDirectory.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    String name = pathname.getName();
                    return name.endsWith(".avsc") || name.endsWith(".avpr");
                }
            });
            if (mainFiles==null || mainFiles.length==0) {
                getLog().warn("No avro files found in directory: " + mainSourceDirectory.getPath());
            } else {
                processFiles(mainFiles, mainOutputDirectory);
                this.project.addCompileSourceRoot(mainOutputDirectory.getAbsolutePath());
            }
        }
        
        File[] testFiles = null;
        if ( testSourceDirectory.exists() ) {
            testFiles = testSourceDirectory.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    String name = pathname.getName();
                    return name.endsWith(".avsc") || name.endsWith(".avpr");
                }
            });
            if (testFiles==null || testFiles.length==0) {
                getLog().warn("No avro files found in directory: " + testSourceDirectory.getPath());
            } else {
                processFiles(testFiles, testOutputDirectory);
                this.project.addTestCompileSourceRoot(testOutputDirectory.getAbsolutePath());
            }
        }
    }

    private void processFiles(File[] mainFiles, File outputDir) throws MojoExecutionException {
        for (File file : mainFiles) {
            try {
                getLog().info("Compiling: "+file.getPath());
                if( file.getName().endsWith(".avpr") ) {
                    SpecificCompiler.compileProtocol(file, outputDir);
                } else if( file.getName().endsWith(".avsc") ) {
                    SpecificCompiler.compileSchema(file, outputDir);
                } else {
                    throw new RuntimeException("Unhandled file type: "+file.getName());
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Avro compile failed.", e);
            }
        }
    }

}
