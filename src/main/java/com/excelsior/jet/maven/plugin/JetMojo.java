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
package com.excelsior.jet.maven.plugin;

import com.excelsior.jet.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;

import static com.excelsior.jet.Txt.s;

/**
 *  Main Mojo for building Java (JVM) applications with Excelsior JET.
 *
 * @author Nikita Lipsky
 */
@Mojo( name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class JetMojo extends AbstractMojo {

    /**
     * The Maven Project Object.
     */
    @Parameter(defaultValue="${project}", readonly=true, required=true)
    private MavenProject project;

    /**
     * The main application class.
     */
    @Parameter(property = "mainClass", required = true)
    private String mainClass;

    /**
     * Excelsior JET installation directory.
     * If it is not specified, it is detected by the following algorithm:
     * <ul>
     *   <li> first -Djet.home system property is checked, if it is set, jet home is set from it</li>
   	 *   <li> then JET_HOME environment variable is checked, if it is set, jet home is set from it </li>
   	 *   <li> finally PATH environment variable is scanned for appropriate jet home </li>
     * </ul>
     */
    @Parameter(property = "jetHome", defaultValue = "${jet.home}")
    private String jetHome;

    /**
     * Directory for Excelsior JET temporary files build process
     * and the target directory for resulting package.
     * <p/>
     * "app" subdirectory of jetOutputDir will contain the final self-contained package
     * that you may deploy to another PCs by simple copy operation.
     * For convenience, "${project.build.finalName}.zip" is also created that is ZIP archive
     * of "app" directory, if 'zipOutput' property is set to {@code true}.
     */
    @Parameter(property = "jetOutputDir", defaultValue = "${project.build.directory}/jet")
    private File jetOutputDir;

    /**
     * Target executable name. If not set, the main class name is used for executable name.
     */
    @Parameter(property = "outputName")
    private String outputName;

    /**
     * Windows .ico file to associate with resulting executable file.
     */
    @Parameter(property = "icon", defaultValue = "${project.basedir}/src/main/jetresources/icon.ico")
    private File icon;

    /**
     * If set to {@code true} the resulting executable file will not show console on Windows.
     */
    @Parameter(property = "hideConsole")
    boolean hideConsole;

    /**
     * Controls creating zip archive containing self-contained package as the final step of build process.
     */
    @Parameter(property = "zipOutput", defaultValue = "true")
    private boolean zipOutput;

    private static final String BUILD_DIR = "build";
    private static final String LIB_DIR = "lib";
    private static final String PACKAGE_DIR = "app";

    private JetHome checkPrerequisites(File mainJar) throws MojoFailureException {
        // first check that main jar were built
        if (!mainJar.exists()) {
            getLog().error(s("JetMojo.MainJarNotFound.Error", mainJar.getAbsolutePath()));
            throw new MojoFailureException(s("JetMojo.MainJarNotFound.Failure"));
        }

        // check main class
        if (Utils.isEmpty(mainClass)) {
            throw new MojoFailureException(s("JetMojo.MainNotSpecified.Failure"));
        }

        //normalize main and set outputName
        mainClass = mainClass.replace('.', '/');
        if (outputName == null) {
            int lastSlash = mainClass.lastIndexOf('/');
            outputName = lastSlash < 0 ? mainClass : mainClass.substring(lastSlash + 1);
        }

        // check and return jet home
        try {
            return Utils.isEmpty(jetHome)? new JetHome() : new JetHome(jetHome);
        } catch (JetHomeException e) {
            throw new MojoFailureException(e.getMessage());
        }
    }

    private void mkdir(File dir) throws MojoExecutionException {
        if (!dir.exists() && !dir.mkdirs()) {
            if (!dir.exists()) {
                throw new MojoExecutionException(s("JetMojo.DirCreate.Error", dir.getAbsolutePath()));
            }
            getLog().warn(s("JetMojo.DirCreate.Warning", dir.getAbsolutePath()));
        }

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
    private ArrayList<String> copyDependencies(File buildDir, File mainJar) throws MojoExecutionException {
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

    /**
     * Compiles application with AOT compiler.
     */
    private void compile(JetHome jetHome, File buildDir, ArrayList<String> compilerArgs) throws MojoFailureException, CmdLineToolException {
        if (Utils.isWindows()) {
            if (icon.isFile()) {
                compilerArgs.add(icon.getAbsolutePath());
            }
            if (hideConsole) {
                compilerArgs.add("-gui+");
            }
        }
        compilerArgs.add("-main=" + mainClass);
        compilerArgs.add("-outputname=" + outputName);
        compilerArgs.add("-decor=ht");
        if (new JetCompiler(jetHome, compilerArgs.toArray(new String[compilerArgs.size()]))
                .workingDirectory(buildDir).withLog(getLog()).execute() != 0) {
            throw new MojoFailureException(s("JetMojo.Build.Failure"));
        }
    }

    /**
     * Package built executable with required Excelsior JET runtime files
     * into a self-contained directory
     */
    private void pack(JetHome jetHome, File buildDir, File packageDir) throws CmdLineToolException, MojoFailureException {
        if (new JetPackager(jetHome,
                 "-add-file", Utils.mangleExeName(outputName), "/",
                 "-target", packageDir.getAbsolutePath())
                .workingDirectory(buildDir).withLog(getLog()).execute() != 0) {
            throw new MojoFailureException(s("JetMojo.Package.Failure"));
        }
    }

    private static void compressZipfile(File sourceDir, File outputFile) throws IOException {
        ZipArchiveOutputStream zipFile = new ZipArchiveOutputStream(new FileOutputStream(outputFile));
        compressDirectoryToZipfile(sourceDir.getAbsolutePath(), sourceDir.getAbsolutePath(), zipFile);
        IOUtils.closeQuietly(zipFile);
    }

    private static void compressDirectoryToZipfile(String rootDir, String sourceDir, ZipArchiveOutputStream out) throws IOException {
        File[] files = new File(sourceDir).listFiles();
        assert files != null;
        for (File file : files) {
            if (file.isDirectory()) {
                compressDirectoryToZipfile(rootDir, sourceDir + File.separator + file.getName(), out);
            } else {
                ZipArchiveEntry entry = new ZipArchiveEntry(sourceDir.replace(rootDir, "")  + File.separator + file.getName());
                if (Utils.isUnix() && file.canExecute()) {
                    entry.setUnixMode(0777);
                }
                out.putArchiveEntry(entry);
                FileInputStream in = new FileInputStream(sourceDir + File.separator +  file.getName());
                IOUtils.copy(in, out);
                IOUtils.closeQuietly(in);
                out.closeArchiveEntry();
            }
        }
    }

    private void finishBuild(File packageDir) throws IOException {
        if (zipOutput) {
            getLog().info(s("JetMojo.ZipApp.Info"));
            File targetZip = new File(jetOutputDir, project.getBuild().getFinalName() + ".zip");
            compressZipfile(packageDir, targetZip);
            getLog().info(s("JetMojo.Build.Success"));
            getLog().info(s("JetMojo.GetZip.Info", targetZip.getAbsolutePath()));
        } else {
            getLog().info(s("JetMojo.Build.Success"));
            getLog().info(s("JetMojo.GetDir.Info", packageDir.getAbsolutePath()));
        }
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        Txt.log = getLog();

        Build build = project.getBuild();
        File mainJar = new File(build.getDirectory(), build.getFinalName() + ".jar");

        JetHome jetHome = checkPrerequisites(mainJar);

        // creating output dirs
        File buildDir = new File(jetOutputDir, BUILD_DIR);
        mkdir(buildDir);
        File packageDir = new File(jetOutputDir, PACKAGE_DIR);
        //cleanup packageDir
        try {
            Utils.cleanDirectory(packageDir);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        ArrayList<String> compilerArgs = copyDependencies(buildDir, mainJar);

        try {
            compile(jetHome, buildDir, compilerArgs);

            pack(jetHome, buildDir, packageDir);

            finishBuild(packageDir);

        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException(s("JetMojo.Unexpected.Error"), e);
        }
    }
}