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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for persisting ECOA generation tasks to the database.
 * Complements the in-memory GenerationTaskStore for durability across optional restarts.
 */
@Repository
public class GenerationTaskJdbcRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public GenerationTaskJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Upsert a task record. Create if not exists, update if exists.
     */
    public void save(GenerationTask task) {
        String logText = String.join("\n", task.getLogs());
        String csmgvtResultJson = serializeJson(task.getCsmgvtResult());
        String csmgvtProductCheckJson = serializeJson(task.getCsmgvtProductCheck());
        String csmgvtCompileErrorsJson = serializeJson(task.getCsmgvtCompileErrors());
        String csmgvtCsmResultJson = serializeJson(task.getCsmgvtCsmResult());
        String componentVersionSelectionsJson = serializeJson(task.getComponentVersionSelections());

        // Try UPDATE first (compatible with both H2 and PostgreSQL)
        int updated = this.jdbcClient.sql("""
                UPDATE edt_generation_tasks SET
                    workspace_id = :workspaceId,
                    workflow_mode = :workflowMode,
                    base_project_file = :baseProjectFile,
                    active_project_file = :activeProjectFile,
                    harness_project_file = :harnessProjectFile,
                    user_id      = :userId,
                    status       = :status,
                    sub_status   = :subStatus,
                    progress     = :progress,
                    output_path  = :outputPath,
                    logs         = :logs,
                    source_state = :sourceState,
                    code_workspace_path = :codeWorkspacePath,
                    source_readiness_evidence = :sourceReadinessEvidence,
                    csmgvt_result = :csmgvtResult,
                    csmgvt_product_check = :csmgvtProductCheck,
                    csmgvt_compile_errors = :csmgvtCompileErrors,
                    csmgvt_csm_result = :csmgvtCsmResult,
                    test_workspace_path = :testWorkspacePath,
                    patch_artifact_path = :patchArtifactPath,
                    source_version_id = :sourceVersionId,
                    source_revision = :sourceRevision,
                    component_version_selections = :componentVersionSelections,
                    updated_at   = :updatedAt
                WHERE task_id = :taskId
                """)
                .param("taskId", task.getTaskId())
                .param("workspaceId", task.getWorkspaceId())
                .param("workflowMode", task.getWorkflowMode().name())
                .param("baseProjectFile", task.getBaseProjectFile())
                .param("activeProjectFile", task.getActiveProjectFile())
                .param("harnessProjectFile", task.getHarnessProjectFile())
                .param("userId", task.getUserId())
                .param("status", task.getStatus().name())
                .param("subStatus", task.getSubStatus().name())
                .param("progress", task.getProgress())
                .param("outputPath", task.getOutputPath())
                .param("logs", logText)
                .param("sourceState", task.getSourceState() != null ? task.getSourceState().name() : null)
                .param("codeWorkspacePath", task.getCodeWorkspacePath())
                .param("sourceReadinessEvidence", task.getSourceReadinessEvidence())
                .param("csmgvtResult", csmgvtResultJson)
                .param("csmgvtProductCheck", csmgvtProductCheckJson)
                .param("csmgvtCompileErrors", csmgvtCompileErrorsJson)
                .param("csmgvtCsmResult", csmgvtCsmResultJson)
                .param("testWorkspacePath", task.getTestWorkspacePath())
                .param("patchArtifactPath", task.getPatchArtifactPath())
                .param("sourceVersionId", task.getSourceVersionId())
                .param("sourceRevision", task.getSourceRevision())
                .param("componentVersionSelections", componentVersionSelectionsJson)
                .param("updatedAt", java.sql.Timestamp.from(task.getUpdatedAt()))
                .update();

        // If no row was updated, insert a new record
        if (updated == 0) {
            this.jdbcClient.sql("""
                    INSERT INTO edt_generation_tasks
                        (task_id, project_id, workspace_id, workflow_mode, base_project_file, active_project_file, harness_project_file, user_id, status, sub_status, progress, output_path, logs, source_state, code_workspace_path, source_readiness_evidence, csmgvt_result, csmgvt_product_check, csmgvt_compile_errors, csmgvt_csm_result, test_workspace_path, patch_artifact_path, source_version_id, source_revision, component_version_selections, created_at, updated_at)
                    VALUES
                        (:taskId, :projectId, :workspaceId, :workflowMode, :baseProjectFile, :activeProjectFile, :harnessProjectFile, :userId, :status, :subStatus, :progress, :outputPath, :logs, :sourceState, :codeWorkspacePath, :sourceReadinessEvidence, :csmgvtResult, :csmgvtProductCheck, :csmgvtCompileErrors, :csmgvtCsmResult, :testWorkspacePath, :patchArtifactPath, :sourceVersionId, :sourceRevision, :componentVersionSelections, :createdAt, :updatedAt)
                    """)
                    .param("taskId", task.getTaskId())
                    .param("projectId", task.getProjectId())
                    .param("workspaceId", task.getWorkspaceId())
                    .param("workflowMode", task.getWorkflowMode().name())
                    .param("baseProjectFile", task.getBaseProjectFile())
                    .param("activeProjectFile", task.getActiveProjectFile())
                    .param("harnessProjectFile", task.getHarnessProjectFile())
                    .param("userId", task.getUserId())
                    .param("status", task.getStatus().name())
                    .param("subStatus", task.getSubStatus().name())
                    .param("progress", task.getProgress())
                    .param("outputPath", task.getOutputPath())
                    .param("logs", logText)
                    .param("sourceState", task.getSourceState() != null ? task.getSourceState().name() : null)
                    .param("codeWorkspacePath", task.getCodeWorkspacePath())
                    .param("sourceReadinessEvidence", task.getSourceReadinessEvidence())
                    .param("csmgvtResult", csmgvtResultJson)
                    .param("csmgvtProductCheck", csmgvtProductCheckJson)
                    .param("csmgvtCompileErrors", csmgvtCompileErrorsJson)
                    .param("csmgvtCsmResult", csmgvtCsmResultJson)
                    .param("testWorkspacePath", task.getTestWorkspacePath())
                    .param("patchArtifactPath", task.getPatchArtifactPath())
                    .param("sourceVersionId", task.getSourceVersionId())
                    .param("sourceRevision", task.getSourceRevision())
                    .param("componentVersionSelections", componentVersionSelectionsJson)
                    .param("createdAt", java.sql.Timestamp.from(task.getCreatedAt()))
                    .param("updatedAt", java.sql.Timestamp.from(task.getUpdatedAt()))
                    .update();
        }
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Find a task by ID.
     */
    public Optional<GenerationTask> findById(String taskId) {
        return this.jdbcClient.sql("""
                SELECT * FROM edt_generation_tasks WHERE task_id = :taskId
                """)
                .param("taskId", taskId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    /**
     * Find all tasks for a project, ordered newest first.
     * Excludes intermediate states (INIT, EXPORTING_XML) that are not meaningful to users.
     */
    public List<GenerationTask> findByProjectId(String projectId) {
        return this.jdbcClient.sql("""
                SELECT * FROM edt_generation_tasks
                WHERE project_id = :projectId
                AND status NOT IN ('INIT', 'EXPORTING_XML')
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    /**
     * Find tasks for a project filtered by user, ordered newest first.
     * Excludes intermediate states (INIT, EXPORTING_XML) that are not meaningful to users.
     */
    public List<GenerationTask> findByProjectIdAndUserId(String projectId, String userId) {
        return this.jdbcClient.sql("""
                SELECT * FROM edt_generation_tasks
                WHERE project_id = :projectId AND user_id = :userId
                AND status NOT IN ('INIT', 'EXPORTING_XML')
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .param("userId", userId)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    /**
     * Delete a task by ID.
     */
    public int deleteById(String taskId) {
        return this.jdbcClient.sql("""
                DELETE FROM edt_generation_tasks WHERE task_id = :taskId
                """)
                .param("taskId", taskId)
                .update();
    }

    /**
     * Delete all tasks for a project (admin only).
     */
    public int deleteByProjectId(String projectId) {
        return this.jdbcClient.sql("""
                DELETE FROM edt_generation_tasks WHERE project_id = :projectId
                """)
                .param("projectId", projectId)
                .update();
    }

    /**
     * Delete tasks for a project filtered by user.
     */
    public int deleteByProjectIdAndUserId(String projectId, String userId) {
        return this.jdbcClient.sql("""
                DELETE FROM edt_generation_tasks WHERE project_id = :projectId AND user_id = :userId
                """)
                .param("projectId", projectId)
                .param("userId", userId)
                .update();
    }

    // -------------------------------------------------------------------------

    private GenerationTask mapRow(ResultSet rs) throws SQLException {
        String taskId = rs.getString("task_id");
        String projectId = rs.getString("project_id");
        String workspaceId = rs.getString("workspace_id");

        java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
        java.sql.Timestamp updatedTs = rs.getTimestamp("updated_at");
        java.time.Instant createdAt = createdTs != null ? createdTs.toInstant() : java.time.Instant.now();
        java.time.Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : java.time.Instant.now();

        GenerationWorkflowMode workflowMode = GenerationWorkflowMode.DIRECT_DEV;
        try {
            String workflowModeValue = rs.getString("workflow_mode");
            if (workflowModeValue != null) {
                workflowMode = GenerationWorkflowMode.fromString(workflowModeValue);
            }
        } catch (IllegalArgumentException ignored) {
            // Keep DIRECT_DEV fallback for malformed legacy rows.
        }

        GenerationTask.Status status = GenerationTask.Status.INIT;
        try {
            status = GenerationTask.Status.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException ignored) {
            // Keep INIT fallback for malformed legacy rows.
        }

        GenerationTask.SubStatus subStatus = GenerationTask.SubStatus.NONE;
        try {
            subStatus = GenerationTask.SubStatus.valueOf(rs.getString("sub_status"));
        } catch (IllegalArgumentException ignored) {
            // Keep NONE fallback for malformed legacy rows.
        }

        String logsText = rs.getString("logs");
        List<String> logs = logsText == null || logsText.isBlank() ? List.of() : List.of(logsText.split("\n"));

        GenerationTask.SourceState sourceState = null;
        try {
            String sourceStateValue = rs.getString("source_state");
            if (sourceStateValue != null && !sourceStateValue.isBlank()) {
                sourceState = GenerationTask.SourceState.valueOf(sourceStateValue);
            }
        } catch (IllegalArgumentException ignored) {
            // null fallback for malformed legacy rows
        }

        GenerationTask task = GenerationTask.restoreSnapshot(
                taskId,
                projectId,
                workspaceId != null ? workspaceId : taskId,
                status,
                subStatus,
                rs.getInt("progress"),
                rs.getString("output_path"),
                logs,
                createdAt,
                updatedAt,
                workflowMode,
                rs.getString("base_project_file"),
                rs.getString("active_project_file"),
                rs.getString("harness_project_file"),
                rs.getString("user_id"),
                sourceState,
                rs.getString("code_workspace_path"),
                rs.getString("source_readiness_evidence"));

        // Restore CSMGVT results
        task.setCsmgvtResult(deserializeJsonMap(rs.getString("csmgvt_result")));
        task.setCsmgvtProductCheck(deserializeJsonMap(rs.getString("csmgvt_product_check")));
        task.setCsmgvtCompileErrors(deserializeJsonList(rs.getString("csmgvt_compile_errors")));
        task.setCsmgvtCsmResult(deserializeJsonMap(rs.getString("csmgvt_csm_result")));

        // Restore code backflow data
        task.setTestWorkspacePath(rs.getString("test_workspace_path"));
        task.setPatchArtifactPath(rs.getString("patch_artifact_path"));
        task.setSourceVersionId(rs.getString("source_version_id"));
        task.setSourceRevision(rs.getString("source_revision"));

        // Restore component version selections
        task.setComponentVersionSelections(deserializeComponentVersionSelections(rs.getString("component_version_selections")));

        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return this.objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> deserializeJsonList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return this.objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<GenerationTask.ComponentVersionSelection> deserializeComponentVersionSelections(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return this.objectMapper.readValue(json, new TypeReference<List<GenerationTask.ComponentVersionSelection>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
