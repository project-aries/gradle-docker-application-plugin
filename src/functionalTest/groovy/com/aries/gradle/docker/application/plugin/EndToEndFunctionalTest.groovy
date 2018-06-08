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

package com.aries.gradle.docker.application.plugin

import com.aries.gradle.docker.application.plugin.AbstractFunctionalTest

import static java.util.concurrent.TimeUnit.MINUTES

import org.gradle.testkit.runner.BuildResult
import spock.lang.Timeout

/**
 *
 *  Functional tests to perform up, pause, and down tasks.
 *
 */
class EndToEndFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 5, unit = MINUTES)
    def "Can start, stop, and remove a postgres application stack"() {

        String uuid = randomString()
        buildFile << """

            applications {
                myPostgresStack {
                    id = "${uuid}"
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        stop {
                            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                            successOnExitCodes = [0, 127, 137]
                            timeout = 60000
                            probe(60000, 10000)
                        }
                        liveness {
                            probe(300000, 10000, 'database system is ready to accept connections')
                        }
                    }
                }
            }
            
            task up(dependsOn: ['myPostgresStackUp'])
            
            task stop(dependsOn: ['myPostgresStackStop'])

            task down(dependsOn: ['myPostgresStackDown'])
        """

        when:
            BuildResult result = build('up', 'stop', 'down')

        then:
            result.output.contains('is not running or available to inspect')
            result.output.contains('Inspecting container with ID')
            result.output.contains('Created container with ID')
            result.output.contains('Starting liveness probe on container')
            result.output.contains('Running exec-stop on container with ID')
            result.output.contains('Removing container with ID')
            result.output.contains('RestartContainer SKIPPED')
            !result.output.contains('ListImages SKIPPED')
    }

    @Timeout(value = 5, unit = MINUTES)
    def "Can stop a postgres application stack without failing"() {

        String uuid = randomString()
        buildFile << """

            applications {
                myPostgresStack {
                    id = "${uuid}"
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        stop {
                            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                            successOnExitCodes = [0, 127, 137]
                            timeout = 60000
                            probe(60000, 10000)
                        }
                        liveness {
                            probe(300000, 10000, 'database system is ready to accept connections')
                        }
                    }
                }
            }
                        
            task stop(dependsOn: ['myPostgresStackStop'])
        """

        when:
        BuildResult result = build('stop')

        then:
        result.output.contains('is not running or available to stop')
        !result.output.contains('Created container with ID')
    }

    @Timeout(value = 5, unit = MINUTES)
    def "Can remove a postgres application stack without failing"() {

        String uuid = randomString()
        buildFile << """

            applications {
                myPostgresStack {
                    id = "${uuid}"
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        stop {
                            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                            successOnExitCodes = [0, 127, 137]
                            timeout = 60000
                            probe(60000, 10000)
                        }
                        liveness {
                            probe(300000, 10000, 'database system is ready to accept connections')
                        }
                    }
                }
            }

            task down(dependsOn: ['myPostgresStackDown'])
        """

        when:
        BuildResult result = build('down')

        then:
        count(result.output, 'is not available to delete') == 2
    }

}
