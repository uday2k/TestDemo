Cucumber-JVM Gradle Plugin
===

Add the following to your `build.gradle`:

    buildscript {
        repositories {
            maven {
                url 'http://dl.bintray.com/ramonza/gradle-plugins'
            }
        }

        dependencies {
            classpath 'cucumber.contrib.gradle:cucumber-gradle:0.2.0'
        }
    }

    apply plugin: 'cucumber'

This will add a `cucumber` task that runs all features in `src/test/resources`.

The `cucumber` task extends `JavaExec` so any properties that work on `JavaExec` should also work on `cucumber`,
in addition to cucumber-specific properties:

    cucumber {
        systemProperty 'mySystemProperty', 'someValue'
        jvmArgs '-Xmx1G'

        // glue defaults to all packages in compile output if not specified
        glue += 'com.example.glue'

        // The following are default the values:
        features += project.sourceSets.test.output.resourcesDir
        htmlReport true
        jsonReport true
        junitReport true
        progressOutput false
        prettyOutput true

        // monochrome defaults to use Gradle --color flag
        monochrome false
    }

Assertions are enabled by default. Do not modify the `mainClass` or `args` properties unless you know what you're doing.

Additionally, the `cucumber` task reads certain project properties, making it easier to invoke from the commandline.

To debug the cucumber process:

    $ gradle cucumber -PcukeDebug=true

To run certain tags:

    $ gradle cucumber -PcukeTags=~@wip

To run certain features or scenarios matched by description:

    $ gradle cucumber -PcukeNames='my special scenario title'

