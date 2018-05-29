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

package com.aries.gradle.docker.databases.plugin.extensions

import org.gradle.api.tasks.Optional

/**
 *
 *
 *  Base class for all databases to inherit common functionality from.
 *
 */
public abstract class BaseDatabase {

    @Optional
    String id

    @Optional
    String repository // docker image id (e.g. postgres)

    @Optional
    String tag // docker tag id (e.g. latest or 10.0)

    @Optional
    String port // what port to expose database on. if set to an empty string a random port will be picked.

    @Optional
    String liveOnLog // the log line within the container we will use to confirm it is "live"

    @Optional
    Closure createDatabase // closure to further configure the `DockerCreateDatabase` task.

    @Optional
    Closure startDatabase // closure to further configure the `DockerStartDatabase` task.

    abstract String repository()

    abstract String defaultPort()

    abstract String liveOnLog()

    String id() {
        this.id ?: System.getProperty("user.name")
    }

    String databaseId() {
        "${id()}-database"
    }

    String databaseDataId() {
        "${databaseId()}-data"
    }

    String tag() {
        this.tag ?: 'latest'
    }

    String image() {
        "${repository()}:${tag()}"
    }

    // helper method to configure the `DockerCreateDatabase` closure
    void createDatabase(Closure closure) {
        this.createDatabase = closure
    }

    // helper method to configure the `DockerStartDatabase` closure
    void startDatabase(Closure closure) {
        this.startDatabase = closure
    }
}
