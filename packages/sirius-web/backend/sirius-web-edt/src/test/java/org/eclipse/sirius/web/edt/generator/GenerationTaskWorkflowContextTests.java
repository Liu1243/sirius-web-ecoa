/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

public class GenerationTaskWorkflowContextTests {

    @Test
    public void restoreSnapshotPreservesWorkflowContext() {
        Instant createdAt = Instant.parse("2026-04-19T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-04-19T12:01:00Z");

        GenerationTask task = GenerationTask.restoreSnapshot(
                "task-1",
                "project-1",
                "workspace-1",
                GenerationTask.Status.AWAITING_CODE,
                GenerationTask.SubStatus.RUNNING_MSCIGT,
                75,
                "/workspace/output",
                List.of("log-1"),
                createdAt,
                updatedAt,
                GenerationWorkflowMode.HARNESS_DEV,
                "base.project.xml",
                "demo-harness.project.xml",
                "demo-harness.project.xml");

        assertThat(task.getWorkflowMode()).isEqualTo(GenerationWorkflowMode.HARNESS_DEV);
        assertThat(task.getBaseProjectFile()).isEqualTo("base.project.xml");
        assertThat(task.getActiveProjectFile()).isEqualTo("demo-harness.project.xml");
        assertThat(task.getHarnessProjectFile()).isEqualTo("demo-harness.project.xml");
    }

    @Test
    public void taskStatusResponseExposesWorkflowContext() {
        GenerationTask task = new GenerationTask("task-2", "project-2");
        task.setWorkflowMode(GenerationWorkflowMode.INTEGRATION);
        task.setBaseProjectFile("base.project.xml");
        task.setActiveProjectFile("active.project.xml");
        task.setHarnessProjectFile("harness.project.xml");

        EdtGeneratorController.TaskStatusResponse response = EdtGeneratorController.TaskStatusResponse.from(task);

        assertThat(response.workflowMode()).isEqualTo("INTEGRATION");
        assertThat(response.baseProjectFile()).isEqualTo("base.project.xml");
        assertThat(response.activeProjectFile()).isEqualTo("active.project.xml");
        assertThat(response.harnessProjectFile()).isEqualTo("harness.project.xml");
    }
}
