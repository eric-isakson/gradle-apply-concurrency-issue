I really like the idea of caches that speed up my builds.

I've been burned by bugs in the caches before, so I started using the
`--recompile-scripts` flag to work around it.

Now I run into intermittent concurrency issues with the build script cache
getting cleared while it is still in use.

    * What went wrong:
    A problem occurred configuring root project 'projectDir-2--73782733'.
    > Could not open cp_proj remapped class cache for 7i4myz9tty3a06xwqos5b0lrh (C:\dev\bugs\gradle-apply-concurrency-issue\build\test\userHome\junit9132474214283903594\gradle_user_home--73782733\caches\3.5\scripts-remapped\build_919s7ys18dihczwvx0jvdmj8y\7i4myz9tty3a06xwqos5b0lrh\cp_proj0341ca279e16587311ec2a53cd62afc0).
       > java.io.FileNotFoundException: C:\dev\bugs\gradle-apply-concurrency-issue\build\test\userHome\junit9132474214283903594\gradle_user_home--73782733\caches\3.5\scripts\7i4myz9tty3a06xwqos5b0lrh\cp_proj\cp_proj0341ca279e16587311ec2a53cd62afc0\classes\_BuildScript_.class (The system cannot find the path specified)

I've isolated this down to shared build script code that itself has a buildscript block and
written a test that demonstrates the problem. There is a copy of the test reports from
my run against Gradle 3.5 at [reports/tests/test](https://github.com/eric-isakson/gradle-apply-concurrency-issue/master/reports/tests/test/index.html)

I'm left with doing complete isolation on every build with a clean gradle user home and getting
no cache advantages or trusting the build script cache, getting rid of --recompile-scripts
and moving on to other issues. I guess I have a trust issue.

I've read over similar bug reports like these:

* https://github.com/gradle/gradle/issues/1425
* https://issues.gradle.org/browse/GRADLE-2795

Right now, I have a really mixed environment with builds that range between Gradle 2.5 and Gradle 2.14.1 and
plan to start adoption of 3.5.

Looks to me like the [strategy proposed by Hemant Gokhale in GRADLE-2795](https://issues.gradle.org/browse/GRADLE-2795?focusedCommentId=17777&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-17777)
would be my best plan of attack to prevent concurrent access to Gradle user home in my Jenkins environment and manage the caches so they do
not grow without bounds.

It looks like the module cache lock contention issue was solved in 2.13, so
once I get all my builds moved up to 2.13 or later, perhaps I can start allowing
the multiple executors to share a single cache.

Anything that has changed since his 2013 post that would require some adjustments?