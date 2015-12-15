/*
 * Copyright (c) 2015, Excelsior LLC.
 *
 *  This file is part of Excelsior JET Maven Plugin.
 *
 *  Excelsior JET Maven Plugin is free software:
 *  you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Excelsior JET Maven Plugin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Excelsior JET Maven Plugin.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
*/
package com.excelsiorjet.maven.plugin;

import com.excelsiorjet.JetHome;
import com.excelsiorjet.JetHomeException;
import com.excelsiorjet.Txt;
import com.excelsiorjet.Utils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import static com.excelsiorjet.Txt.s;

/**
 * Parent of Excelsior JET Maven Plugin mojos.
 *
 * @author Nikita Lipsky
 */
public abstract class AbstractJetMojo extends AbstractMojo {
    /**
     * The Maven Project Object.
     */
    @Parameter(defaultValue="${project}", readonly=true, required=true)
    protected MavenProject project;

    /**
     * The main application class.
     */
    @Parameter(property = "mainClass", required = true)
    protected String mainClass;

    /**
     * The main application jar.
     * The default is the main project artifact, which must be a jar file.
     */
    @Parameter(property = "mainJar", defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    protected File mainJar;

    /**
     * Excelsior JET installation directory.
     * If unspecified, the plugin uses the following algorithm to set the value of this property:
     * <ul>
     *   <li> If the jet.home system property is set, use its value</li>
     *   <li> Otherwise, if the JET_HOME environment variable is set, use its value</li>
     *   <li> Otherwise scan the PATH environment variable for a suitable Excelsior JET installation</li>
     * </ul>
     */
    @Parameter(property = "jetHome", defaultValue = "${jet.home}")
    protected String jetHome;

    /**
     * Directory for temporary files generated during the build process
     * and the target directory for the resulting package.
     * <p>
     * The plugin will place the final self-contained package in the "app" subdirectory
     * of {@code jetOutputDir}. You may deploy it to other systems using a simple copy operation.
     * For convenience, the plugin will also create a ZIP archive {@code ${project.build.finalName}.zip}
     * with the same content, if the {@code packaging} parameter is set to {@code zip}.
     * </p>
     */
    @Parameter(property = "jetOutputDir", defaultValue = "${project.build.directory}/jet")
    protected File jetOutputDir;

    /**
     * The placement of execution profiles gathered during Test Run.
     * By default, they are placed into "src/main/jetresources" subdirectory of your project.
     * It is recommended to commit the profiles (.usg, .startup) to VCS to allow the plugin
     * use the profiles during automatic application builds withour running the Test Run.
     *
     * @see TestRunMojo
     */
    @Parameter(property = "execProfilesDir", defaultValue = "${project.basedir}/src/main/jetresources")
    protected File execProfilesDir;

    /**
     * The file name of execution profiles. By default, ${project.artifactId} is used.
     */
    @Parameter(property = "execProfilesName", defaultValue = "${project.artifactId}")
    protected String execProfilesName;

    protected static final String BUILD_DIR = "build";
    protected static final String LIB_DIR = "lib";

    protected JetHome checkPrerequisites() throws MojoFailureException {
        Txt.log = getLog();

        // first check that main jar were built
        if (!mainJar.exists()) {
            String error;
            if (!"jar".equalsIgnoreCase(project.getPackaging())) {
                error = s("JetMojo.BadPackaging.Failure", project.getPackaging());
            } else {
                error = s("JetMojo.MainJarNotFound.Failure", mainJar.getAbsolutePath());
            }
            getLog().error(error);
            throw new MojoFailureException(error);
        }

        // check main class
        if (Utils.isEmpty(mainClass)) {
            throw new MojoFailureException(s("JetMojo.MainNotSpecified.Failure"));
        }

        // check jet home
        JetHome jetHomeObj;
        try {
            jetHomeObj = Utils.isEmpty(jetHome)? new JetHome() : new JetHome(jetHome);

        } catch (JetHomeException e) {
            throw new MojoFailureException(e.getMessage());
        }

        return jetHomeObj;
    }

    protected void mkdir(File dir) throws MojoExecutionException {
        if (!dir.exists() && !dir.mkdirs()) {
            if (!dir.exists()) {
                throw new MojoExecutionException(s("JetMojo.DirCreate.Error", dir.getAbsolutePath()));
            }
            getLog().warn(s("JetMojo.DirCreate.Warning", dir.getAbsolutePath()));
        }
    }

    protected File createBuildDir() throws MojoExecutionException {
        File buildDir = new File(jetOutputDir, BUILD_DIR);
        mkdir(buildDir);
        return buildDir;
    }

    private void copyDependency(File from, File to, File buildDir, ArrayList<String> dependencies) {
        try {
            if (!to.exists()) {
                Files.copy(from.toPath(), to.toPath());
            }
            dependencies.add(buildDir.toPath().relativize(to.toPath()).toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies project dependencies.
     *
     * @return list of dependencies relative to buildDir
     */
    protected ArrayList<String> copyDependencies(File buildDir, File mainJar) throws MojoExecutionException {
        File libDir = new File(buildDir, LIB_DIR);
        mkdir(libDir);
        ArrayList<String> dependencies = new ArrayList<>();
        try {
            copyDependency(mainJar, new File(buildDir, mainJar.getName()), buildDir, dependencies);
            project.getArtifacts().stream()
                    .map(Artifact::getFile)
                    .filter(File::isFile)
                    .forEach(f -> copyDependency(f, new File(libDir, f.getName()), buildDir, dependencies))
            ;
            return dependencies;
        } catch (Exception e) {
            throw new MojoExecutionException(s("JetMojo.ErrorCopyingDependency.Exception"), e);
        }
    }
}
