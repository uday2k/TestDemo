package cucumber.contrib.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.process.JavaExecSpec

class CucumberTask extends JavaExec {
    boolean htmlReport = true
    boolean junitReport = true
    boolean jsonReport = true
    boolean prettyOutput = true
    boolean progressOutput = false
    boolean monochrome
    String defaultTags = null
    List<File> features = new ArrayList<>()
    List<String> glue = new ArrayList<>()

    CucumberTask() {
        setMain('cucumber.api.cli.Main')
        setEnableAssertions(true)
    }

    void configureDefaults() {
        setDebug(project.hasProperty('cukeDebug') && Boolean.parseBoolean(project.cukeDebug))
        setClasspath(project.configurations.cucumberRuntime + project.sourceSets.main.output + project.sourceSets.test.output)
        features += project.sourceSets.test.output.resourcesDir
        monochrome = !project.gradle.startParameter.colorOutput
    }

    @Override
    void exec() {
        def args = []
        def tags = getTagsArg()
        def names = getNamesArg()
        if (names != null) {
            args << '--name' << names
        } else if (tags != null) {
            args << '--tags' << tags
        }
        if (junitReport) {
            args << '-f' << 'junit:' + project.file("$project.buildDir/cucumber.xml")
        }
        if (htmlReport) {
            args << '-f' << 'html:' + project.file("$project.buildDir/cucumber-html-reports")
        }
        if (jsonReport) {
            args << '-f' << 'json:' + project.file("$project.buildDir/cucumber.json")
        }
        if (prettyOutput) {
            args << '-f' << 'pretty'
        }
        if (progressOutput) {
            args << '-f' << 'progress'
        }
        if (monochrome) {
            args << '--monochrome'
        }
        getGlueArgs().each { args << '--glue' << it }
        // cucumber doesn't like it if these directories don't exist
        args += features.findAll{ it.directory }*.absolutePath
        super.setArgs(args)
        super.exec()
    }

    @Override
    JavaExec args(Object... args) {
        throw new UnsupportedOperationException()
    }

    @Override
    JavaExecSpec args(Iterable<?> args) {
        throw new UnsupportedOperationException()
    }

    private Collection<String> getGlueArgs() {
        if (glue.empty)
            project.sourceSets.test.output.classesDir.listFiles()*.name
        else
            glue
    }

    private String getTagsArg() {
        String tags = defaultTags
        if (project.hasProperty('cukeTags')) {
            tags = project.cukeTags
        }
        return tags
    }

    private String getNamesArg() {
        if (project.hasProperty('cukeNames'))
            return project.cukeNames
        if (project.hasProperty('cukeName'))
            return project.cukeName
        return null
    }
}

class CucumberPlugin implements Plugin<Project> {

    public static final String CUCUMBER_RUNTIME = "cucumberRuntime"
    public static final String CUCUMBER_TASK_NAME = "cucumber"

    @Override
    void apply(Project project) {
        def configuration = project.configurations.create(CUCUMBER_RUNTIME)
        configuration.extendsFrom(project.configurations.getByName('testRuntime'))
        def cucumber = project.task(CUCUMBER_TASK_NAME, type: CucumberTask) as CucumberTask
        cucumber.configureDefaults()
    }
}
