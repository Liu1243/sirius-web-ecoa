/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.generator;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory store for active ECOA code generation tasks.
 * Uses ConcurrentHashMap for thread-safe access.
 * Tasks are stored until explicitly removed or the application restarts.
 */
@Component
public class GenerationTaskStore {

    private final ConcurrentHashMap<String, GenerationTask> tasks = new ConcurrentHashMap<>();

    /**
     * Store a new task.
     */
    public void put(GenerationTask task) {
        this.tasks.put(task.getTaskId(), task);
    }

    /**
     * Find a task by its ID.
     */
    public Optional<GenerationTask> findById(String taskId) {
        return Optional.ofNullable(this.tasks.get(taskId));
    }

    /**
     * Remove a task from the store.
     */
    public void remove(String taskId) {
        this.tasks.remove(taskId);
    }

    /**
     * Delete a task by ID (alias for remove).
     */
    public void delete(String taskId) {
        this.tasks.remove(taskId);
    }

    /**
     * Remove all tasks for a specific project.
     */
    public void deleteByProjectId(String projectId) {
        this.tasks.values().removeIf(task -> task.getProjectId().equals(projectId));
    }
}
