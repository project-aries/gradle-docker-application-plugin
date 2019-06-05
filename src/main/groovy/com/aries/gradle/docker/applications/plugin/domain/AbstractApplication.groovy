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

package com.aries.gradle.docker.applications.plugin.domain

import com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPlugin
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

import javax.annotation.Nullable


/**
 *
 *  Base class for all applications to inherit functionality from.
 *
 */
class AbstractApplication {

    @Internal
    private ObjectFactory objectFactory = GradleDockerApplicationsPlugin.objectFactory

    @Internal
    private ProviderFactory providerFactory = GradleDockerApplicationsPlugin.providerFactory

    // if set will pass along a network name to use (will create custom network if not present) for application.
    @Input
    @Optional
    final Property<String> network = objectFactory.property(String)
    void network(@Nullable final String requestedNetwork) {
        network.set(requestedNetwork)
    }
    void network(final Closure<String> requestedNetwork) {
        if (requestedNetwork) { network.set(providerFactory.provider(requestedNetwork))}
    }

    // if set to true we will skip custom network creation and/or connecting to.
    @Input
    @Optional
    final Property<Integer> count = objectFactory.property(Integer)
    void count(final Integer requestedCount) {
        if (requestedCount) { count.set(providerFactory.provider{ requestedCount })}
    }
    void count(final Closure<Integer> requestedCount) {
        if (requestedCount) { count.set(providerFactory.provider(requestedCount))}
    }

    // if set will override the application-name part of the docker container.
    @Input
    @Optional
    final Property<String> id = objectFactory.property(String)
    void id(final String requestedId) {
        if (requestedId) { id.set(providerFactory.provider{ requestedId })}
    }
    void id(final Closure<String> requestedId) {
        if (requestedId) { id.set(providerFactory.provider(requestedId))}
    }

    // internal helper collection to hold AbstractApplication names
    // that this AbstractApplication dependsOn.
    @Internal
    final Collection<String> applicationDependsOn = ArrayList.newInstance()

    // if set will be used as a shared acquire amongst all tasks.
    @Input
    @Optional
    final Collection<Object> dependsOn = []
    Collection<Object> dependsOn(final Object... paths = null) {
        paths?.each { dep ->
            if (dep) {
                def arbitraryDep = dep
                if (arbitraryDep instanceof AbstractApplication) {
                    this.applicationDependsOn.add(dep.getName())
                    arbitraryDep = dep.getName() + "Up"
                }
                this.dependsOn.add(arbitraryDep)
            }
        }

        dependsOn
    }

    // methods and properties used to configure the main container.
    final List<Closure<MainContainer>> mainConfigs = []
    void main(final Closure<MainContainer> mainConfig) {
        if (mainConfig) {
            mainConfigs.add(mainConfig)
        }
    }

    // methods and properties used to configure the data container
    final List<Closure<DataContainer>> dataConfigs = []
    void data(final Closure<DataContainer> dataConfig) {
        if (dataConfig) {
            dataConfigs.add(dataConfig)
        }
    }

    final String name
    AbstractApplication(final String name) {
        this.name = name
        this.count.set(1)
        this.network.set('generate')
    }
}
