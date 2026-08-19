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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a running ECOA code generation task. Tracks the overall state machine status, sub-tool status, progress,
 * and logs.
 */
public class GenerationTask {

    /**
     * Main state machine statuses as defined in containerized_deployment_solution.md.
     */
    public enum Status {
        INIT, EXPORTING_XML, GENERATING,
        /** Skeleton generated; waiting for user to write business logic in Code Server before execution. Also known as CODE_EDIT_REQUIRED. */
        AWAITING_CODE,
        /** Integration mode: source readiness check failed, user must prepare source code first. */
        SOURCE_PREP_REQUIRED,
        /** Integration mode: waiting for user to select component versions before CSMGVT/LDP. */
        AWAITING_COMPONENT_SELECTION,
        /** Integration mode: CSMGVT completed; waiting for user to write test cases before LDP. */
        AWAITING_CSMGVT_TEST,
        COMPLETED, FAILED, CANCELLED
    }

    /**
     * Fine-grained sub-statuses for the GENERATING state (which tool is currently running).
     */
    public enum SubStatus {
        NONE, RUNNING_EXVT, RUNNING_MSCIGT, RUNNING_ASCTG, RUNNING_CSMGVT, RUNNING_LDP,
        /** ASCTG succeeded; switching activeProjectFile to harness project. */
        SWITCHING_ACTIVE_PROJECT,
        /** Code backflow patch has been applied to source of truth. */
        CODE_BACKFLOW_APPLIED,
        /** Code backflow has conflicts requiring manual resolution. */
        CONFLICT
    }

    /** Source code readiness state, used to prevent running CSMGVT/LDP on empty skeleton. */
    public enum SourceState {
        UNKNOWN, GENERATED_SKELETON, USER_EDIT_REQUIRED, USER_EDITED, SOURCE_READY
    }

    /** Component version selection for INTEGRATION mode: componentId -> versionId */
    public record ComponentVersionSelection(String componentId, String componentName, String versionId, String versionName) {
    }

    /** Evidence source for source readiness, used in INTEGRATION mode. */
    public enum SourceReadinessEvidence {
        UPSTREAM_TASK, SOURCE_IMPORT, CODE_SERVER_PREP, MANUAL_CONFIRMATION
    }

    private final String taskId;

    private final String projectId;

    /**
     * Unique sub-directory identifier for this generation's workspace. Defaults to taskId for new tasks; Re-run tasks
     * share the original task's workspaceId.
     */
    private String workspaceId;

    private volatile GenerationWorkflowMode workflowMode;

    private volatile String baseProjectFile;

    private volatile String activeProjectFile;

    private volatile String harnessProjectFile;

    /** Source code readiness state. */
    private volatile SourceState sourceState;

    /** Path where Code Server opens (same as workspace for continue execution). */
    private volatile String codeWorkspacePath;

    /** Evidence source for source readiness (INTEGRATION mode). */
    private volatile String sourceReadinessEvidence;

    /** Structured CSMGVT runtime.log check result from Python callback. */
    private volatile java.util.Map<String, Object> csmgvtResult;

    /** CSMGVT output product check result from Python callback. */
    private volatile java.util.Map<String, Object> csmgvtProductCheck;

    /** CSMGVT classified compile errors from Python callback. */
    private volatile java.util.List<String> csmgvtCompileErrors;

    /** CSMGVT csm execution result from Python callback. */
    private volatile java.util.Map<String, Object> csmgvtCsmResult;

    /** Path to the test workspace used for code backflow (audit record). */
    private volatile String testWorkspacePath;

    /** Path to the generated patch artifact (audit record). */
    private volatile String patchArtifactPath;

    /** Source version ID created or updated during code backflow. */
    private volatile String sourceVersionId;

    /** Source revision hash after code backflow was applied. */
    private volatile String sourceRevision;

    /** Component version selections for INTEGRATION mode. */
    private volatile List<ComponentVersionSelection> componentVersionSelections = new ArrayList<>();

    /** ID of the user who triggered this generation task. Nullable for legacy rows. */
    private volatile String userId;

    private volatile Status status;

    private volatile SubStatus subStatus;

    /** Overall progress, 0-100. */
    private volatile int progress;

    /** Path to the generated output directory (populated on success). */
    private volatile String outputPath;

    /** Accumulating log lines from tool execution. */
    private final List<String> logs;

    private Instant createdAt;

    private volatile Instant updatedAt;

    public GenerationTask(String taskId, String projectId) {
        this.taskId = taskId;
        this.projectId = projectId;
        this.workspaceId = taskId; // default: each task has its own workspace
        this.workflowMode = GenerationWorkflowMode.DIRECT_DEV;
        this.status = Status.INIT;
        this.subStatus = SubStatus.NONE;
        this.sourceState = SourceState.UNKNOWN;
        this.progress = 0;
        this.logs = new CopyOnWriteArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Factory method to restore a GenerationTask from persistent storage. Unlike the constructor, this preserves the
     * original createdAt / updatedAt timestamps read from the database instead of using Instant.now().
     */
    public static GenerationTask restore(String taskId, String projectId, Instant createdAt, Instant updatedAt) {
        GenerationTask task = new GenerationTask(taskId, projectId);
        task.createdAt = createdAt;
        task.updatedAt = updatedAt;
        return task;
    }

    /**
     * Restores a full task snapshot from persistent storage without bumping updatedAt.
     */
    public static GenerationTask restoreSnapshot(String taskId, String projectId, String workspaceId, Status status, SubStatus subStatus, int progress, String outputPath, List<String> logs,
            Instant createdAt, Instant updatedAt) {
        return restoreSnapshot(taskId, projectId, workspaceId, status, subStatus, progress, outputPath, logs, createdAt, updatedAt, GenerationWorkflowMode.DIRECT_DEV, null, null, null);
    }

    /**
     * Restores a full task snapshot from persistent storage, including workflow context, without bumping updatedAt.
     */
    public static GenerationTask restoreSnapshot(String taskId, String projectId, String workspaceId, Status status, SubStatus subStatus, int progress, String outputPath, List<String> logs,
            Instant createdAt, Instant updatedAt, GenerationWorkflowMode workflowMode, String baseProjectFile, String activeProjectFile, String harnessProjectFile) {
        return restoreSnapshot(taskId, projectId, workspaceId, status, subStatus, progress, outputPath, logs, createdAt, updatedAt, workflowMode, baseProjectFile, activeProjectFile,
                harnessProjectFile, null);
    }

    /**
     * Restores a full task snapshot from persistent storage, including user context, without bumping updatedAt.
     */
    public static GenerationTask restoreSnapshot(String taskId, String projectId, String workspaceId, Status status, SubStatus subStatus, int progress, String outputPath, List<String> logs,
            Instant createdAt, Instant updatedAt, GenerationWorkflowMode workflowMode, String baseProjectFile, String activeProjectFile, String harnessProjectFile, String userId) {
        return restoreSnapshot(taskId, projectId, workspaceId, status, subStatus, progress, outputPath, logs, createdAt, updatedAt, workflowMode, baseProjectFile, activeProjectFile, harnessProjectFile, userId, null, null, null);
    }

    public static GenerationTask restoreSnapshot(String taskId, String projectId, String workspaceId, Status status, SubStatus subStatus, int progress, String outputPath, List<String> logs,
            Instant createdAt, Instant updatedAt, GenerationWorkflowMode workflowMode, String baseProjectFile, String activeProjectFile, String harnessProjectFile, String userId,
            SourceState sourceState, String codeWorkspacePath, String sourceReadinessEvidence) {
        GenerationTask task = restore(taskId, projectId, createdAt, updatedAt);
        task.workspaceId = workspaceId;
        task.workflowMode = workflowMode != null ? workflowMode : GenerationWorkflowMode.DIRECT_DEV;
        task.baseProjectFile = baseProjectFile;
        task.activeProjectFile = activeProjectFile;
        task.harnessProjectFile = harnessProjectFile;
        task.userId = userId;
        task.sourceState = sourceState != null ? sourceState : SourceState.UNKNOWN;
        task.codeWorkspacePath = codeWorkspacePath;
        task.sourceReadinessEvidence = sourceReadinessEvidence;
        task.status = status;
        task.subStatus = subStatus;
        task.progress = Math.max(0, Math.min(100, progress));
        task.outputPath = outputPath;
        task.logs.clear();
        task.logs.addAll(logs);
        return task;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public GenerationWorkflowMode getWorkflowMode() {
        return workflowMode;
    }

    public void setWorkflowMode(GenerationWorkflowMode workflowMode) {
        this.workflowMode = workflowMode != null ? workflowMode : GenerationWorkflowMode.DIRECT_DEV;
        this.updatedAt = Instant.now();
    }

    public String getBaseProjectFile() {
        return baseProjectFile;
    }

    public void setBaseProjectFile(String baseProjectFile) {
        this.baseProjectFile = baseProjectFile;
        this.updatedAt = Instant.now();
    }

    public String getActiveProjectFile() {
        return activeProjectFile;
    }

    public void setActiveProjectFile(String activeProjectFile) {
        this.activeProjectFile = activeProjectFile;
        this.updatedAt = Instant.now();
    }

    public String getHarnessProjectFile() {
        return harnessProjectFile;
    }

    public void setHarnessProjectFile(String harnessProjectFile) {
        this.harnessProjectFile = harnessProjectFile;
        this.updatedAt = Instant.now();
    }

    public SourceState getSourceState() {
        return sourceState;
    }

    public void setSourceState(SourceState sourceState) {
        this.sourceState = sourceState;
        this.updatedAt = Instant.now();
    }

    public String getCodeWorkspacePath() {
        return codeWorkspacePath;
    }

    public void setCodeWorkspacePath(String codeWorkspacePath) {
        this.codeWorkspacePath = codeWorkspacePath;
        this.updatedAt = Instant.now();
    }

    public String getSourceReadinessEvidence() {
        return sourceReadinessEvidence;
    }

    public void setSourceReadinessEvidence(String sourceReadinessEvidence) {
        this.sourceReadinessEvidence = sourceReadinessEvidence;
        this.updatedAt = Instant.now();
    }

    public java.util.Map<String, Object> getCsmgvtResult() {
        return csmgvtResult;
    }

    public void setCsmgvtResult(java.util.Map<String, Object> csmgvtResult) {
        this.csmgvtResult = csmgvtResult;
        this.updatedAt = Instant.now();
    }

    public java.util.Map<String, Object> getCsmgvtProductCheck() {
        return csmgvtProductCheck;
    }

    public void setCsmgvtProductCheck(java.util.Map<String, Object> csmgvtProductCheck) {
        this.csmgvtProductCheck = csmgvtProductCheck;
        this.updatedAt = Instant.now();
    }

    public java.util.List<String> getCsmgvtCompileErrors() {
        return csmgvtCompileErrors;
    }

    public void setCsmgvtCompileErrors(java.util.List<String> csmgvtCompileErrors) {
        this.csmgvtCompileErrors = csmgvtCompileErrors;
        this.updatedAt = Instant.now();
    }

    public java.util.Map<String, Object> getCsmgvtCsmResult() {
        return csmgvtCsmResult;
    }

    public void setCsmgvtCsmResult(java.util.Map<String, Object> csmgvtCsmResult) {
        this.csmgvtCsmResult = csmgvtCsmResult;
        this.updatedAt = Instant.now();
    }

    public String getTestWorkspacePath() {
        return testWorkspacePath;
    }

    public void setTestWorkspacePath(String testWorkspacePath) {
        this.testWorkspacePath = testWorkspacePath;
        this.updatedAt = Instant.now();
    }

    public String getPatchArtifactPath() {
        return patchArtifactPath;
    }

    public void setPatchArtifactPath(String patchArtifactPath) {
        this.patchArtifactPath = patchArtifactPath;
        this.updatedAt = Instant.now();
    }

    public String getSourceVersionId() {
        return sourceVersionId;
    }

    public void setSourceVersionId(String sourceVersionId) {
        this.sourceVersionId = sourceVersionId;
        this.updatedAt = Instant.now();
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public void setSourceRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
        this.updatedAt = Instant.now();
    }

    public List<ComponentVersionSelection> getComponentVersionSelections() {
        return componentVersionSelections != null ? new ArrayList<>(componentVersionSelections) : new ArrayList<>();
    }

    public void setComponentVersionSelections(List<ComponentVersionSelection> componentVersionSelections) {
        this.componentVersionSelections = componentVersionSelections != null ? new ArrayList<>(componentVersionSelections) : new ArrayList<>();
        this.updatedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public SubStatus getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(SubStatus subStatus) {
        this.subStatus = subStatus;
        this.updatedAt = Instant.now();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        this.updatedAt = Instant.now();
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
        this.updatedAt = Instant.now();
    }

    public List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public void addLog(String line) {
        this.logs.add(line);
        this.updatedAt = Instant.now();
    }

    public void addLogs(List<String> lines) {
        this.logs.addAll(lines);
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
