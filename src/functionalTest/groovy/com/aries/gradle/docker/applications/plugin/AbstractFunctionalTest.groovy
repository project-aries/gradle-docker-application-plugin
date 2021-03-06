/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aries.gradle.docker.applications.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import spock.lang.Specification

/**
 *
 *  Base class for all functional tests.
 *
 */
abstract class AbstractFunctionalTest extends Specification {

    static final String possibleOffline = System.getProperty('test.offline')

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile

    // basic check to ensure we can get off the ground.
    def setup() {
        projectDir = temporaryFolder.root
        setupBuildfile()
    }

    protected void setupBuildfile() {

        if (buildFile) {
            buildFile.delete()
        }
        buildFile = temporaryFolder.newFile('build.gradle')

        buildFile << """
            plugins {
                id 'gradle-docker-applications-plugin'
            }

            repositories {
                mavenLocal()
                jcenter()
            }
        """
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).withDebug(true).build()
    }

    protected BuildResult buildAndFail(String... arguments) {
        createAndConfigureGradleRunner(arguments).buildAndFail()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        def args = ['--full-stacktrace',
            '--warning-mode=all',
            '--parallel',
            '-Dorg.gradle.parallel=true',
            '-Dorg.gradle.worker.max=4',
            '-Dorg.gradle.daemon=false',
            '-Dorg.gradle.jvmargs=-Xms1g -Xmx1g -XX:+CMSPermGenSweepingEnabled -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC -XX:+HeapDumpOnOutOfMemoryError']
        if (Boolean.valueOf(possibleOffline).booleanValue() == true) {
            args << '--offline'
        }
        if (arguments) {
            args.addAll(arguments)
        }

        GradleRunner.create().withProjectDir(projectDir).withArguments(args).withPluginClasspath().forwardOutput()
    }

    static String randomString() {
        'gdap-' + UUID.randomUUID().toString().replaceAll("-", "")
    }

    /**
     * Count the number of instances of substring within a string.
     *
     * @param string     String to look for substring in.
     * @param substring  Sub-string to look for.
     * @return           Count of substrings in string.
     */
    static int count(final String string, final String substring) {
       int count = 0;
       int idx = 0;

       while ((idx = string.indexOf(substring, idx)) != -1) {
          idx++;
          count++;
       }

       return count;
    }

    // load an arbitrary file from classpath resource
    static URL loadResource(String resourcePath) {
        this.getClass().getResource(resourcePath)
    }

    /**
     * Copy and replace an arbitrary number of tokens in a given file. If no
     * tokens are found then file is more/less just copied to new destination.
     *
     * @param source the source file we will replace tokens in
     * @param destination the destination file we will write
     * @param tokensToReplaceWithValues map where key=token-to-replace, value=value-to-replace-with
     */
    static void copyAndReplaceTokensInFile(File source, File destination, def tokensToReplaceWithValues = [:]) {
        destination.withWriter { dest ->
            source.eachLine { line ->
                def localLine = line
                tokensToReplaceWithValues.each { k, v ->
                    localLine = localLine.replaceAll(k, String.valueOf(v))
                }
                dest << localLine + System.lineSeparator()
            }
        }
    }
}
