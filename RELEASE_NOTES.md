### Version 1.5.2 (June 10, 2019)
* Tasks that are created in an ad-hoc manner sometimes fails for no reason given by gradle. When this happens we will attempt to recreate the task X number of times.
* Task `SummaryReportCollector` was created to house all SummaryReports of a given execution and to be easily queried downstream.

### Version 1.5.1 (June 8, 2019)
* `AbsractApplication` gained optionally `dependsOnParallel` which behaves much like gradles `dependsOn` only executes this in parallel with the actual task execution.

### Version 1.5.0 (June 7, 2019)
* The `options` closure has been removed in favor of using gradle Property providers.
* All dockerized applications now run in parallel via gradles worker api.
* All around code simplification and refactoring across the entire codebase.

### Version 1.4.2 (May 31, 2019)
* `AbstractApplication` now has options defined within an `options` closure so that we can further delay resolving them.

### Version 1.4.1 (May 25, 2019)
* `AbstractApplication` can now dependOn other `AbstractApplications` and ensures all ordering of operations (up, stop, down) is done correctly.
* Ad-hoc execution of gradle task-code now takes into account `onlyIf` blocks.
* Bump `gradle-docker-plugin` to `4.9.0`.

### Version 1.4.0 (May 18, 2019)
* Introduction of `DockerManageContainer` task.
* Overall refactor of internal code to use less gradle tasks and take advantage of `DockerManageContainer` task.
* All `Up` tasks will now have a list of `SummaryReport` objects attached to them for downstreaming querying of running applications.

### Version 1.3.0 (May 10, 2019)
* `main` and `data` containers now lazily initialized.
* application dsl gained `lock` and `dependsOn`.

### Version 1.2.0 (May 1, 2019)
* Each application gets its own private network stack.
* application dsl gained `network` and `disableNetwork`.

### Version 1.1.0 (July 27, 2018)
* All configs will now be applied/configured within its backing tasks doFirst block.
* Bump gradle-docker-plugin to 3.5.0

### Version 1.0.2 (July 19, 2018)
* Removal of application extension points which were created after evaluation and not very useful.
* Valid container failure checking will now take into account exception messages which contain the phrase `not running`.

### Version 1.0.1 (July 16, 2018)
* Account for when plugin is applied to a script which is not the root script.

### Version 1.0.0 (July 15, 2018)
* Bump gradle-docker-plugin to 3.4.4

### Version 0.9.9 (July 14, 2018)
* If 'id' is defined tha will take the place of the entire container name instead of being a concatenation of if and the image being used.
* Add 'ConflictException' to list of regex's we will check should container not be present or running.

### Version 0.9.8 (July 8, 2018)
* Bump gradle-docker-plugin to 3.4.3

### Version 0.9.7 (July 7, 2018)
* Bump gradle-docker-plugin to 3.4.2

### Version 0.9.6 (July 1, 2018)
* Bump gradle-docker-plugin to 3.4.1

### Version 0.9.5 (June 25, 2018)
* Rename project and package structure to be `gradle-docker-applications-plugin`
* Only execute *CopyFiles tasks if have more than 0 `files` configs.

### Version 0.9.4 (June 23, 2018)
* Both the `main` and `data` container can now configure an optional `files` task to allow for an arbitrary number of files
to be added to either container BEFORE we attempt to start the `main` container.

### Version 0.9.3 (June 17, 2018)
* The 'main' container can now configure an optional 'exec' task to be run once liveness has been attained.

### Version 0.9.2 (June 17, 2018)
* Fix for `data` container not getting properly configured.
* Bump gradle-docker-plugin to 3.3.5

### Version 0.9.1 (June 16, 2018)
* Bump gradle-docker-plugin to 3.3.4

### Version 0.9.0 (June 12, 2018)
* Initial project release that allows users to define up to N dockerized applications with each gettin their own **Up**, **Stop**, and **Down** tasks.
