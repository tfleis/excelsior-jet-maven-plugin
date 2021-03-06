[![Maven Central](https://img.shields.io/maven-central/v/com.excelsiorjet/excelsior-jet-maven-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/com.excelsiorjet/excelsior-jet-maven-plugin)
Excelsior JET Maven Plugin
=====

*Excelsior JET Maven Plugin* provides Maven users with an easy way to compile their applications
down to optimized native Windows, OS X, or Linux executables with [Excelsior JET](http://excelsiorjet.com).
Such precompiled applications start and often work faster, do not depend on the JRE,
and are as difficult to reverse engineer as if they were written in C++.

### Prerequisites
Before using this plugin, you need to install Excelsior JET.
You may find a fully functional evaluation version of Excelsior JET [here](http://www.excelsiorjet.com/evaluate).
It is free for evaluation purposes and the only limitation it has is that it expires 90 days
after installation, along with all compiled applications.

**Note:** Excelsior JET does not yet support cross-compilation, so you need to build your application on each target platform
separately. The supported platforms are Windows (32- and 64-bit), Linux (32- and 64-bit), and OS X (64-bit).
  

### Overview

The current version of the plugin supports very basic functionality.
It can only handle *plain Java SE applications*, i.e. applications that have a main class
and all their dependencies are explicitly listed in the JVM classpath at launch time.
In other words, if your application can be launched using a command line
of the following form:
    
```
java -cp [dependencies-list] [main class]
```
and loads classes mostly from jars that are present
in the `dependencies-list`, then you can use this plugin.

This plugin will transform your application into an optimized native executable for the platform
on which you run Maven, and place it into a separate directory together with all required
Excelsior JET runtime files. In addition, it can either pack that directory into a zip archive
(all platforms) or create an Excelsior Installer setup (Windows and Linux only).
    
Excelsior JET supports many more features than this plugin.
We plan to cover all those features in the future.


### Usage

You need to copy and paste the following configuration into the `<plugins>` section of
your `pom.xml` file:

```xml
<plugin>
	<groupId>com.excelsiorjet</groupId>
	<artifactId>excelsior-jet-maven-plugin</artifactId>
	<version>0.2.1</version>
	<configuration>
		<mainClass></mainClass>
	</configuration>
</plugin>
```

set the `<mainClass>` parameter, and use the following command line to build the application:

```
mvn jet:build
```

### Excelsior JET Installation Directory Lookup

In order to do its job, the plugin needs to locate an Excelsior JET installation.
You have three ways to specify the Excelsior JET installation directory explicitly:

- add the `<jetHome>` parameter to the `<configuration>` section of the plugin
- pass the `jet.home` system property on the Maven command line as follows:
```
mvn jet:build -Djet.home=[JET-Home]
```
- or set the `JET_HOME` O/S environment variable

If none of above is set, the plugin searches for an Excelsior JET installation along the `PATH`.
So if you only have one copy of Excelsior JET installed, the plugin should be able to find it on Windows right away,
and on Linux and OS X - if you have run the Excelsior JET `setenv` script prior to launching Maven.

### Configurations other than `<mainClass>`
For a complete list of parameters, look into the Javadoc of `@Parameter` field declarations
in the  [JetMojo](https://github.com/excelsior-oss/excelsior-jet-maven-plugin/blob/master/src/main/java/com/excelsiorjet/maven/plugin/JetMojo.java)
class. Most of them have default values derived from your `pom.xml` project
such as `<outputName>` parameter specifying resulting executable name.

There are also two useful Windows-specific configuration parameters:

`<hideConsole>true</hideConsole>` – hide console

`<icon>`*icon-file*`</icon>` – set executable icon (in Windows .ico format)

It is recommended to place the executable icon into a VCS, and if you place it to
`${project.basedir}/src/main/jetresources/icon.ico`, you do not need to explicitly specify it
in the configuration. The plugin uses the location `${project.basedir}/src/main/jetresources`
for other Excelsior JET-specific resource files (such as the EULA for Excelsior Installer setups).

#### Excelsior Installer Configurations

Starting from 0.2.0 release, the plugin supports creation of Excelsior Installer setups -
conventional installer GUIs for Windows or self-extracting archives with command-line interface
for Linux.

To create an Excelsior Installer setup, add the following configuration into the plugin
`<configuration>` section:

`<packaging>excelsior-installer</packaging>`

Excelsior Installer setup, in turn, has the following configurations:

* `<product>`*product-name*`</product>` - default is `${project.name}`

* `<vendor>`*vendor-name*`</vendor>` -  default is `${project.organization.name}`

* `<version>`*product-version*`</version>` - default is `${project.version}`

* `<eula>`*end-user-license-agreement-file*`</eula>` - default is `${project.basedir}/src/main/jetresources/eula.txt`

* `<eulaEncoding>`*eula-file-encoding*`</eulaEncoding>` - default is `autodetect`. Supported encodings are US-ASCII (plain text), UTF16-LE

* `<installerSplash>`*installer-splash-screen-image*`</installerSplash>` - default is `${project.basedir}/src/main/jetresources/installerSplash.bmp`

#### Windows Version-Information Resource Configurations

On Windows, the plugin automatically adds a
[version-information resource](https://msdn.microsoft.com/en-us/library/windows/desktop/ms646981%28v=vs.85%29.aspx)
to the resulting executable. This can be disabled by specifying the following
configuration:

    <addWindowsVersionInfo>false</addWindowsVersionInfo>

By default, the values of version-information resource strings are derived from project settings.
The values of `<product>` and `<vendor>` configurations are used verbatim as
`ProductName` and `CompanyName` respectively;
other defaults can be changed using the following configuration parameters:

* `<winVIVersion>`*version-string*`</winVIVersion>` - version number (both `FileVersion` and `ProductVersion` strings are set to this same value)

    **Notice:** unlike Maven `${project.version}`, this string must have format `v1.v2.v3.v4`, where vi is a number.
    The plugin would use heuristics to derive a correct version string from the specified value if the latter
    does not meet this requirement, or from `${project.version}` if this configuration is not present.

* `<winVICopyright>`*legal-copyright*`</winVICopyright>` - `LegalCopyright` string, with default value derived from other parameters

* `<winVIDescription>`*executable-description*`</winVIDescription>` - `FileDescription` string, default is `${project.name}`

#### Multi-app Executables
The plugin may compile more than one application into a single executable and
let you select a particular application at launch time via command line arguments.

The command line syntax of [multi-app executables](http://www.excelsior-usa.com/doc/jet/jetw011.html#0330)
is an extension of the `java` launcher command
line syntax that allows specifying the main class, VM options, Java system properties,
and the arguments of the application:

```
    Exe-name [Properties-and-options] Main-classname [App-arguments]
```

To enable the multi-app mode add the following configuration parameter:

`<multiApp>true</multiApp>`

#### Startup Accelerator Configurations

**New in 0.3.0:**

The Startup Accelerator improves the startup time of applications compiled with Excelsior JET.
Since version 0.3.0, the plugin automatically runs the compiled application immediately after build,
collects the necessary profile information and hard-wires it into the executable just created.
The JET Runtime will then use the information to reduce the application startup time.
The Startup Accelerator is enabled by default, but you may disable it by specifying the following
configuration:

`<profileStartup>false</profileStartup>`

You may also specify the duration of the profiling session in seconds by specifying the following
configuration:

`<profileStartupTimeout>`*duration-in-seconds*`</profileStartupTimeout>`

As soon as the specified period elapses, profiling stops and the application is automatically terminated,
so ensure that the timeout value is large enough to capture all actions the application nomrally carries out
during startup. (It is safe to close the application manually if the profiling period proves to be excessively long.)

### Performing a Test Run

**New in 0.3.0:**

Since version 0.3.0, the plugin can run your Java application on the Excelsior JET JVM
using a JIT compiler before pre-compiling it to native code. This so-called test run
helps Excelsior JET:

* verify that your application can be executed successfully on the Excelsior JET JVM.
  Usually, if the test run completes normally, the natively compiled application also works well.
* detect the optional parts of Excelsior JET Runtime that are used by your application.
  For instance, JavaFX Webkit is not included in the resulting package by default
  due to its size, but if the application used it during a test run, it gets included automatically.
* collect profile information to optimize your app more effectively

To perform a test run, execute the following Maven command:

```
mvn jet:testrun
```

The plugin will place the gathered profiles in the `${project.basedir}/src/main/jetresources` directory.
Incremental changes of application code do not typically invalidate the profiles, so 
it is recommended to commit the profiles (`.usg`, `.startup`) to VCS to allow the plugin
to re-use them during automatic application builds without performing the Test Run.

Note: 64-bit versions of Excelsior JET do not collect `.usg` profiles yet.
      So it is recommended to perform a test run on the 32-bit version of Excelsior JET at least once.

The profiles will be used by the Startup Optimizer, supported since version 0.3.0 of the plugin,
and Global Optimizer, which will be supported in the future.

Note: During a test run, the application executes in a special profiling mode,
      so disregard its modest start-up time and performance.

### Build process

The native build is performed in the `jet` subdirectory of the Maven target build directory.
First, the plugin copies the main application jar to the `jet/build` directory,
and copies all its run time dependencies to `jet/build/lib`.
Then it invokes the Excelsior JET AOT compiler to compile all those jars into a native executable.
Upon success, it copies that executable and the required Excelsior JET Runtime files
into the `jet/app` directory, and binds the executable to that copy of the Runtime.

> Your natively compiled application is ready for distribution at this point: you may copy
> contents of the `jet/app` directory to another computer that has neither Excelsior JET nor
> the Oracle JRE installed, and the executable should work as expected.

Finally, the plugin packs the contents of the `jet/app` directory into
a zip archive named `${project.build.finalName}.zip` so as to aid single file re-distribution.
On Windows and Linux, you can also set the `<packaging>excelsior-installer</packaging>`
configuration parameter to have the plugin create an Excelsior Installer setup instead.

In the future, the plugin will also support the creation of OS X app bundles.

## Sample Project

To demonstrate the process and result of plugin usage, we have forked the [JavaFX VNC Client](https://github.com/comtel2000/jfxvnc) project on GitHub, added the Excelsior JET plugin to its `pom.xml` file, and run it through Maven to build native binaries for three platforms.

You can download the binaries from here:

* [Windows (32-bit, 33MB installer)](http://www.excelsior-usa.com/download/jet/maven/jfxvnc-ui-1.0.0-windows-x86.exe)
* [OS X (64-bit, 45MB)](http://www.excelsior-usa.com/download/jet/maven/jfxvnc-ui-1.0.0-osx-amd64.zip)
* [Linux (64-bit, 31MB installer)](http://www.excelsior-usa.com/download/jet/maven/jfxvnc-ui-1.0.0-linux-amd64.bin)

or clone [the project](https://github.com/pjBooms/jfxvnc) and build it yourself:

```
    git clone https://github.com/pjBooms/jfxvnc
    cd jfxvnc/ui
    mvn jet:build
```

## Release Notes

Version 0.3.0 (??-Jan-2016)

* Startup Accelerator supported and enabled by default
* Test Run Mojo implemented that enables:
   - running an application on the Excelsior JET JVM before pre-compiling it to native code
   - gathering application execution profiles to enable the Startup Optimizer

Version 0.2.1 (21-Jan-2016)

* Support of multi-app executables

Version 0.2.0 (14-Dec-2015)

* Support of Excelsior Installer setup generation
* Windows Version Information generation


Version 0.1.0 (08-Dec-2015)
* Initial release supporting compilation of the Maven Project with all dependencies into native executable
and placing it into a separate directory with required Excelsior JET runtime files.

## Roadmap

Even though we are going to base the plugin development on your feedback in the future, we have our own short-term plan as well.
So the next few releases will add the following features:

* [Java Runtime Slim-Down](http://www.excelsiorjet.com/solutions/java-download-size).
* Mapping of compiler and packager options to plugin configuration parameters.
* Creation of Mac OS X application bundles.
* Code signing.
* Tomcat Web Applications support.

Note that the order of appearance of these features is not fixed and can be adjusted based on your feedback.
