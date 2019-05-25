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

package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.domain.GradleLock
import com.aries.gradle.docker.applications.plugin.domain.SummaryReport
import com.bmuschko.gradle.docker.tasks.DockerOperation
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
import com.github.dockerjava.api.model.ContainerNetwork
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.util.ConfigureUtil

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.*
import static java.util.Objects.requireNonNull

/**
 * Manage the lifecycle of a single application-container
 */
class DockerManageContainer extends DefaultTask {

    enum CommandTypes {
        UP,
        STOP,
        DOWN
    }

    @Input
    @Optional
    final Property<String> command = project.objects.property(String)

    @Input
    final Property<String> id = project.objects.property(String)

    @Input
    @Optional
    final Property<String> repository = project.objects.property(String)

    @Input
    @Optional
    final Property<String> tag = project.objects.property(String)

    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> createOnly = project.objects.property(Boolean)

    @Input
    @Optional
    final ListProperty<String> volumesFrom = project.objects.listProperty(String)

    @Input
    @Optional
    final ListProperty<Closure<DockerCreateContainer>> createContainerConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerCopyFileToContainer>> copyFileConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerLivenessContainer>> livenessConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerExecContainer>> execConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerExecStopContainer>> stopConfigs = project.objects.listProperty(Closure)

    @Internal
    private GradleLock lock

    @Internal
    private SummaryReport summaryReport

    DockerManageContainer() {

        command.set(CommandTypes.UP.toString())
        volumesFrom.empty()
        createOnly.set(false)
        createContainerConfigs.empty()
        copyFileConfigs.empty()
        livenessConfigs.empty()
        execConfigs.empty()
        stopConfigs.empty()
    }

    @TaskAction
    void execute() {

        // sanity check for potentially requested lock(s)
        String lockName
        boolean acquire = false
        boolean release = false
        if (this.lock) {
            lockName = this.lock.name
            if (isNullOrEmpty(lockName)) {
                throw new GradleException("'lock' not defined with a valid name")
            }
            acquire = this.lock.acquire
            release = this.lock.release
        }

        try {

            if (acquire) { acquireLock(project, lockName) }

            final String requestedAction = (command.getOrNull() ?: CommandTypes.UP.toString()).trim().toUpperCase()
            switch(CommandTypes.valueOf(requestedAction)) {
                case CommandTypes.UP: command_UP(); break
                case CommandTypes.STOP: command_STOP(); break
                case CommandTypes.DOWN: command_DOWN(); break
                default: throw new GradleException("Unknown requested action: '${requestedAction}'")
            }

        } finally {

            if (release) { releaseLock(project, lockName) }
        }
    }

    private command_UP() {

        final String containerId = id.get()
        final String repositoryId = requireNonNull(repository.getOrNull(), "Property 'repository' is required if using UP command.")
        final String tagId = requireNonNull(tag.getOrNull(), "Property 'tag' is required if using UP command.")
        final String imageId = repositoryId + ":" + tagId
        final String networkName = network.getOrNull()
        final List<String> volumesFromContainers = volumesFrom.getOrElse([])
        final boolean createOnly = createOnly.getOrElse(false)

        // 1.) Check if container is currently available
        final Task availableContainerTask = project.tasks.create(randomString(), DockerInspectContainer, {
            targetContainerId(containerId)
            ext.exists = false
            ext.inspection = null
            ext.hasNetwork = false
            onNext { possibleContainer ->
                ext.exists = true
                ext.inspection = possibleContainer
                ext.hasNetwork = possibleContainer.getNetworkSettings().getNetworks().containsKey(networkName)
            }
            onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(availableContainerTask)

        // 2.) Create network if requested and not present
        if (networkName && !availableContainerTask.ext.hasNetwork) {
            final Task createNetworkTask = project.tasks.create(randomString(), DockerCreateNetwork, {
                onlyIf {
                    try {
                        dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec()
                        false
                    } catch (Exception e) {
                        true
                    }
                }

                networkId = networkName
            })
            executeTaskCode(createNetworkTask)
        }

        boolean restartedContainer = false
        Date restartDate = null
        if (availableContainerTask.ext.exists == true) {

            // 3.) Restart container but only if it's available and not in a running state
            if (createOnly == false && availableContainerTask.ext.inspection.state.running == false) {
                final Task restartContainerTask = project.tasks.create(randomString(), DockerRestartContainer, { cnf ->
                    restartDate = new Date()

                    cnf.targetContainerId(containerId)
                    cnf.waitTime = 3000
                })
                executeTaskCode(restartContainerTask)
                restartedContainer = true
            }

        } else {

            // 4.) Pull image for container if it does not already exist
            final Task inspectImageTask = project.tasks.create(randomString(), DockerInspectImage, {
                targetImageId(imageId)
                ext.hasImage = false
                onNext {
                    ext.hasImage = true
                }
                onError { err ->
                    throwOnValidError(err)
                }
            })
            executeTaskCode(inspectImageTask)

            if (inspectImageTask.ext.hasImage == false) {
                final Task pullImageTask = project.tasks.create(randomString(), DockerPullImage, { cnf ->
                    cnf.repository = repositoryId
                    cnf.tag = tagId
                    cnf.onError { err ->
                        throwOnValidError(err)
                    }
                })
                executeTaskCode(pullImageTask)
            }

            // 5.) create, copy files to, and start container if it didn't previously exist
            final Task createContainerTask = project.tasks.create(randomString(), DockerCreateContainer, { cnf ->
                cnf.network = networkName
                cnf.targetImageId(imageId)
                cnf.containerName = containerId
                cnf.volumesFrom = volumesFromContainers
            })
            executeTaskCode(createContainerTask, createContainerConfigs.get())

            if (copyFileConfigs.get()) {
                final Task copyFilesToContainerTask = project.tasks.create(randomString(), DockerCopyFileToContainer, {
                    targetContainerId(containerId)
                })
                executeTaskCode(copyFilesToContainerTask, copyFileConfigs.get())
            }

            if (createOnly == false) {
                final Task startContainerTask = project.tasks.create(randomString(), DockerStartContainer, {
                    ext.startTime = new Date()
                    targetContainerId(containerId)
                })
                executeTaskCode(startContainerTask)
            }
        }

        if (createOnly == false) {

            // pause done to allow the container to come up and potentially
            // exit (e.g. container that has no entrypoint or cmd defined).
            sleep(2000)

            // 6.) perform liveness check to confirm container is running
            final Task livenessContainerTask = project.tasks.create(randomString(), DockerLivenessContainer, {

                targetContainerId(containerId)

                // only 2 ways this task can kick so we will proceed to configure
                // the `since` option based ONLY upon a "restart" scenario as we will
                // use it to determine where in the logs we should start from whereas
                // in the "start" scenario we simply start from the very beginning
                // of the logs docker gives us.
                since.set(restartDate ?: null)
                onComplete {

                    // though we should be live at this point we sleep for
                    // another 2 seconds to give the potential application
                    // some breathing room before we start hammering away
                    // on it with potential requests.
                    sleep(2000)
                }
            })
            executeTaskCode(livenessContainerTask, livenessConfigs.get())

            // 7.) run any "exec" tasks inside the container now that it's started
            boolean execStarted = false
            if (execConfigs.get() && (restartedContainer == false)) {
                final Task execContainerTask = project.tasks.create(randomString(), DockerExecContainer, {

                    targetContainerId(containerId)

                    onComplete {

                        // sleeping for 2 seconds just in-case any command caused this container to
                        // come down, potentially gracefully, before we presume things are live.
                        sleep(2000)
                    }
                })
                executeTaskCode(execContainerTask, execConfigs.get())
                execStarted = true
            }

            // 8.) get the summary for the running container and print to stdout
            summaryReport = new SummaryReport()
            final Task summaryContainerTask = project.tasks.create(randomString(), DockerOperation, {

                onNext { dockerClient ->

                    // 1.) Set the last used "inspection" for potential downstream use
                    if (execStarted) {

                        // if the `execContainerTask` task kicked we need to
                        // make an additional inspection call to ensure things
                        // are still live and running just to be on the safe side.
                        summaryReport.inspection = dockerClient.inspectContainerCmd(containerId).exec()
                        if (!summaryReport.inspection.state.running) {
                            throw new GradleException("Container '${containerId}' was NOT in a running state after exec(s) finished. Was this expected?")
                        }
                    } else {
                        summaryReport.inspection = livenessContainerTask.lastInspection()
                    }

                    // 2.) set handful of variables for easy access and downstream use
                    summaryReport.id = summaryReport.inspection.id
                    summaryReport.name = summaryReport.inspection.name.replaceFirst('/', '')
                    summaryReport.image = summaryReport.inspection.getConfig().image
                    summaryReport.command = (summaryReport.inspection.getConfig().getEntrypoint()) ? summaryReport.inspection.getConfig().getEntrypoint().join(' ') : null
                    if (summaryReport.inspection.getArgs()) {
                        if (!summaryReport.command) {
                            summaryReport.command = ""
                        }
                        summaryReport.command = ("${summaryReport.command} " + summaryReport.inspection.getArgs().join(' ')).trim()
                    }
                    summaryReport.created = summaryReport.inspection.created
                    if (summaryReport.inspection.getNetworkSettings().getPorts()) {
                        summaryReport.inspection.getNetworkSettings().getPorts().getBindings().each { k, v ->
                            def key = '' + k.getPort()
                            def value = '' + (v ? v[0].hostPortSpec : 0)
                            summaryReport.ports.put(key, value)
                        }
                    }

                    // find the proper network to use for downstream consumption
                    String foundNetwork = null
                    if (summaryReport.inspection.getNetworkSettings().getNetworks().isEmpty()) {
                        summaryReport.address = null
                        summaryReport.gateway = null
                    } else {
                        ContainerNetwork containerNetwork
                        if (networkName) {
                            containerNetwork = summaryReport.inspection.getNetworkSettings().getNetworks().get(networkName)
                            foundNetwork = networkName
                        } else {
                            for (final Map.Entry<String, ContainerNetwork> entry : summaryReport.inspection.getNetworkSettings().getNetworks().entrySet()) {
                                if (!entry.getKey().equals('none')) {
                                    foundNetwork = entry.getKey()
                                    containerNetwork = entry.getValue()
                                    break
                                }
                            }
                        }

                        summaryReport.address = containerNetwork?.getIpAddress()
                        summaryReport.gateway = containerNetwork?.getGateway()
                    }

                    summaryReport.network = foundNetwork

                    // 3.) print banner to stdout as an indication that we are now live
                    logger.quiet '' // newline just to put a break between last output and this banner being printed
                    logger.quiet summaryReport.banner()
                }
            })
            executeTaskCode(summaryContainerTask)
        }
    }

    private command_STOP() {

        final String containerId = id.get()

        final Task execStopContainerTask = project.tasks.create(randomString(), DockerExecStopContainer, {

            targetContainerId(containerId)

            onNext { output ->
                // pipe output to nowhere for the time being
            }
            onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(execStopContainerTask, stopConfigs.get())
    }

    private command_DOWN() {

        final String containerId = id.get()
        final String networkName = network.getOrNull()

        final Task deleteContainerTask = project.tasks.create(randomString(), DockerRemoveContainer, {

            removeVolumes = true
            force = true
            targetContainerId(containerId)

            onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(deleteContainerTask)

        if (networkName) {
            final Task removeNetworkTask = project.tasks.create(randomString(), DockerRemoveNetwork, {
                onlyIf {
                    try {
                        dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec().containers.size() == 0
                    } catch (Exception e) {
                        false
                    }
                }

                targetNetworkId(networkName)

                onError { err ->
                    throwOnValidError(err)
                }
            })
            executeTaskCode(removeNetworkTask)
        }
    }

    void create(final List<Closure<DockerCreateContainer>> createContainerConfigList) {
        if (createContainerConfigList) { createContainerConfigList.each { cfg -> create(cfg) } }
    }

    void create(final Closure<DockerCreateContainer> createContainerConfig) {
        if (createContainerConfig) { createContainerConfigs.add(createContainerConfig) }
    }

    void files(final List<Closure<DockerCopyFileToContainer>> copyFileConfigList) {
        if (copyFileConfigList) { copyFileConfigList.each { cfg -> files(cfg) } }
    }

    void files(final Closure<DockerCopyFileToContainer> copyFileConfig) {
        if (copyFileConfig) { copyFileConfigs.add(copyFileConfig) }
    }

    void liveness(final List<Closure<DockerLivenessContainer>> livenessConfigList) {
        if (livenessConfigList) { livenessConfigList.each { cfg -> liveness(cfg) } }
    }

    void liveness(final Closure<DockerLivenessContainer> livenessConfig) {
        if (livenessConfig) { livenessConfigs.add(livenessConfig) }
    }

    void exec(final List<Closure<DockerExecContainer>> execConfigList) {
        if (execConfigList) { execConfigList.each { cfg -> exec(cfg) } }
    }

    void exec(final Closure<DockerExecContainer > execConfig) {
        if (execConfig) { execConfigs.add(execConfig) }
    }

    void stop(final List<Closure<DockerExecStopContainer>> stopConfigList) {
        if (stopConfigList) { stopConfigList.each{ cfg -> stop(cfg) } }
    }

    void stop(final Closure<DockerExecStopContainer> stopConfig) {
        if (stopConfig) { stopConfigs.add(stopConfig) }
    }

    void lock(final Closure<GradleLock> requestedLock) {
        if (requestedLock) {
            lock = ConfigureUtil.configure(requestedLock, lock ?: new GradleLock())
        }
    }

    SummaryReport getSummaryReport() {
        summaryReport
    }
}
