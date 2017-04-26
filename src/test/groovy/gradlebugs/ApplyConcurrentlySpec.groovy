package gradlebugs

import groovyx.gpars.GParsPool
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Demonstrate a buildscript caching bug related to concurrent apply when using --recompile-scripts and a shared
 * gradle user home.
 */
class ApplyConcurrentlySpec extends Specification {
    static final GRADLE_VERSIONS = []
    static {
        GRADLE_VERSIONS = System.getProperty('target.gradle.versions').split(/,/)
    }

    /**
     * Give the tests access to their name.
     */
    @Rule final TestName testName = new TestName()

    /**
     * The test user home directory. Used as the root for things like the gradle user home to be shared by all tests
     * so the gradle distributions are only downloaded once for the whole set of tests and the buildscript caches
     * are shared. This is also used to isolate our tests from anything configured in the real user home that might
     * cause side effects.
     */
    @Shared protected TemporaryFolder testUserHome

    void setupSpec() {
        testUserHome = new TemporaryFolder(new File(System.getProperty('target.user.home')))
        testUserHome.create()
    }

    void cleanupSpec() {
        // rather than clean up, let's just leave things on the filesystem so we can inspect them after the test
        // execution, we normally just call the build with "./gradlew clean test" so we do not get side effects

        //testUserHome.delete()
    }

    @Unroll
    void 'should not fail when running concurrently with optional arguments \'#optionalArguments\' iteration #iteration'() {
        given:
        List<GradleRunner> runners = []
        // everything shares a single gradle user home to simulate a typical build environment where we want to share out caches for performance reasons
        String gradleUserHome = testUserHome.newFolder('gradle_user_home-' + testName.methodName.hashCode()).absolutePath
        (1..10).each { concurrentProjectNumber ->
            File projectDir = testUserHome.newFolder('projectDir-' + concurrentProjectNumber + '-' + testName.methodName.hashCode())
            File buildFile = new File(projectDir, 'build.gradle')
            buildFile << """
buildscript { script ->
    println "script.sourceFile.toURI()='\${script.sourceFile.toURI()}'"
    apply from: "${System.getProperty('target.shared.script')}"
}
"""
            GRADLE_VERSIONS.each { gradleVersion ->
                String testIsolationDir = "isolation-${concurrentProjectNumber}-${gradleVersion}-${testName.methodName.hashCode()}"
                runners << GradleRunner.create()
                        .withTestKitDir(testUserHome.newFolder(testIsolationDir, 'testKitDir'))
                        .withProjectDir(projectDir)
                        .withArguments([
                            '-Duser.home=' + testUserHome.root.absolutePath,
                            '-Dmaven.repo.local=' + testUserHome.newFolder(testIsolationDir,'m2repository').absolutePath,
                            '--gradle-user-home', gradleUserHome,
                            '-PbuildDir=' + testUserHome.newFolder(testIsolationDir,'build').absolutePath,
                            '--no-search-upward', '--refresh-dependencies',
                            '--full-stacktrace', '--info'] +
                            optionalArguments + [
                            'tasks' // use a task that is always available, does not matter which one
                        ])
                        .withGradleDistribution("https://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip".toURI())
                        .forwardOutput()
            }
        }

        when:
        // invoke these in parallel threads to induce possible concurrency issues
        GParsPool.withPool {
            runners.eachParallel { runner ->
                runner.build()
            }
        }

        then:
        noExceptionThrown()

        where:
        // run the tests a few times each with different optional arguments
        // we do this because the problem may be intermittent and I'm not sure if it
        // will succeed or fail on any given run. Do it enough times that it might show up
        [optionalArguments, iteration] << [[['--recompile-scripts'],[]],(1..3)].combinations()
    }
}
