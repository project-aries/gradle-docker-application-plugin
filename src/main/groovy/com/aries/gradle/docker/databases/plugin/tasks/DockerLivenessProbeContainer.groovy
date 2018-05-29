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

package com.aries.gradle.docker.databases.plugin.tasks

import static com.aries.gradle.docker.databases.plugin.GradleDockerDatabasesPluginUtils.createProgressLogger

import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

import com.aries.gradle.docker.databases.plugin.domain.Probe

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input

/**
 *  Poll a given running container for an arbitrary log message to confirm liveness.
 */
class DockerLivenessProbeContainer extends DockerLogsContainer {

    @Input
    Probe probe

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Starting liveness probe on container with ID '${getContainerId()}'."

        // create progressLogger for pretty printing of terminal log progression
        final def progressLogger = createProgressLogger(project, DockerLivenessProbeContainer)
        progressLogger.started()

        boolean matchFound = false
        long localPollTime = probe.pollTime
        int pollTimes = 0

        // 1.) Write the content of the logs into a StringWriter which we zero-out
        //     below after each successive log grab.
        setSink(new StringWriter())

        while (localPollTime > 0) {
            pollTimes = pollTimes + 1

            // 2.) check if container is actually running
            def container = dockerClient.inspectContainerCmd(getContainerId()).exec()
            if (container.getState().getRunning() == false) {
                throw new GradleException("Container with ID '${getContainerId()}' is not running and so can't perform liveness probe.");
            }

            // 3.) execute our "special" version of `runRemoteCommand` to
            //     check if next log line has the message we're interested in
            //     which in turn will have its output written into the sink.
            specialRunRemoteCommand(dockerClient)

            // 4.) check if log contains expected message otherwise sleep
            String logLine = getSink().toString()
            if (logLine && logLine.contains(probe.logContains)) {
                matchFound = true
                break
            } else {
                progressLogger.progress(sprintf('probing for %010dms', pollTimes *probe.pollInterval ))
                try {

                    // zero'ing out the below so as to save on memory for potentially
                    // big logs returned from container.
                    logLine = null
                    getSink().getBuffer().setLength(0)

                    localPollTime = localPollTime - probe.pollInterval
                    sleep(probe.pollInterval)
                } catch (Exception e) {
                    throw e
                }
            }
        }
        progressLogger.completed()

        if (!matchFound) {
            throw new GradleException("Liveness probe failed to find a match: ${probe.toString()}")
        }
    }

    /**
     * Define the probe options for this liveness check.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @param logContains content within container log we will search for
     * @return instance of Probe
     */
    def probe(final long pollTime, final long pollInterval, final String logContains) {
        this.probe = new Probe(pollTime, pollInterval, logContains)
    }

    // overridden version of `DockerLogsContainer` method `runRemoteCommand` to get the
    // same functionality but without the `logger.quiet` call that gets run every time.
    private specialRunRemoteCommand(dockerClient) {
        def logCommand = dockerClient.logContainerCmd(getContainerId())
        super.setContainerCommandConfig(logCommand)
        logCommand.exec(super.createCallback())?.awaitCompletion()
    }
}
