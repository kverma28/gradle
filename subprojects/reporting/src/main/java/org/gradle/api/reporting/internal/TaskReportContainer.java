/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.reporting.internal;

import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.Report;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.DeprecationLogger;

public abstract class TaskReportContainer<T extends Report> extends DefaultReportContainer<T> {
    private final TaskInternal task;

    public TaskReportContainer(Class<? extends T> type, final Task task) {
        this(type, task, CollectionCallbackActionDecorator.NOOP);
        DeprecationLogger.nagUserOfDeprecated("Internal API constructor TaskReportContainer(Class<T>, Task)");
    }

    public TaskReportContainer(Class<? extends T> type, final Task task, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(type, ((ProjectInternal) task.getProject()).getServices().get(Instantiator.class), callbackActionDecorator);
        this.task = (TaskInternal) task;
    }

    protected Task getTask() {
        return task;
    }
}
