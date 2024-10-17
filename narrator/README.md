# Narrator
---
Welcome to Narrator!

## Overview
A Gradle plugin dedicated for the generation of Models, DAOs, DTOs, etc.
Narrator is built on top of Kotlin KSP API and Gradle API as a tool for generation of code to help reduce the overhead of repetitive code implementation.
This document is a step-by-step guide on how to use it.

## Using the plugin in a project
#### Clone `Narcore Plugins` and publish the `Narrator` to `mavelLocal`
- After cloning `Narcore Plugins`, run `./gradlew narrator:publishToMavenLocal` to publish the plugin to `mavenLocal`
#### Configuring the target project
- The target project is the `server` sub-project and `build.gradle.kts` script here is the script of the target project
- In `settings.gradle.kts`, make sure `mavenLocal()` is a repository for both the Plugins and Dependencies of the project
  ```
  pluginManagement {  
      repositories {  
          mavenLocal()  
      }  
  }
  ```
  ``` 
  dependencyResolutionManagement {  
      repositories {  
          mavenLocal()  
      }  
  }
  ```
- In `gradle.properties` add `ksp.incremental=false` to disable KSP incremental processing.
- In `build.gradle.kts` apply both `Narrator` and `KSP` plugins
  ```
  plugins {  
      id("com.narbase.narcore.narrator") version("0.1.0")  
      id("com.google.devtools.ksp") version("2.0.0-1.0.24")  
  }
  ```
- In `build.gradle.kts` add the following dependencies in the `dependencies` block
  ```
  dependencies {
	  implementation("com.narbase.narcore:narrator:0.1.0")  
	  ksp("com.narbase.narcore:narrator:0.1.0")
  }
  ```
-  In `build.gradle.kts` add the root project path as an option to the KSP processor
   ```
   ksp {  
       arg("rootProjectPath", project.rootProject.projectDir.path)  
   }
   ```
- In `build.gradle.kts` add package configurations for the generated code in a `narrator` configuration block and make sure all fields are present
  ```
	  narrator {  
	    dtoWebPath = projects.dtoWeb.dependencyProject.projectDir.path  
	    shouldOverwrite = true   
	    destinationConfig {  
	        packageRelativePath = "com/narbase/narcore"  
	        daosRelativePath = "data/access"  
	        dtosRelativePath = "dto/domain"  
	        convertorsRelativePath = "data/conversions"  
	    }  
	}
	```

#### Using the plugin
- After configuring the target project and successfully reloading project, run this command in the terminal to start the code generation task after replacing `server-module` with the name of the server sub-project in your project `./gradlew :server-module:narrateAll`

#### Command-line options
##### `--overwrite`
- Provide this option if you want `all` generated files to overwrite existing files. By default, only new file are generated. `./gradlew :server-module:narrateAll --overwrite`