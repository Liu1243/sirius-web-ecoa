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

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.eclipse.sirius.web.edt.importexport.EdtEcoaExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * REST controller for ECOA code generation task management.
 *
 * <ul>
 * <li>POST /api/edt/ecoa/generate/{projectId} — trigger generation</li>
 * <li>GET /api/edt/ecoa/generate/status/{taskId} — poll single task</li>
 * <li>GET /api/edt/ecoa/generate/history/{projectId} — list historical tasks</li>
 * <li>POST /api/internal/tasks/{taskId}/status — Python service callback</li>
 * </ul>
 */
@RestController
public class EdtGeneratorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtGeneratorController.class);

    /** URL of the Python generator micro-service (or mock). */
    @Value("${ecoa.python.generator.url}")
    private String pythonGeneratorUrl;

    /** Shared workspace root directory (mounted volume in Docker). */
    @Value("${ecoa.workspace.dir}")
    private String workspaceDir;

    @Value("${ecoa.backend.url}")
    private String backendUrl;

    private final GenerationTaskStore taskStore;

    private final GenerationTaskJdbcRepository taskRepository;

    private final IProjectSearchService projectSearchService;

    private final IProjectEditingContextService projectEditingContextService;

    private final EdtEcoaExportService ecoaExportService;

    private final CurrentUserService currentUserService;

    private final RestTemplate restTemplate;

    private final IComponentCodeVersionService componentCodeVersionService;

    private final IComponentCodeTagService componentCodeTagService;

    public EdtGeneratorController(GenerationTaskStore taskStore, GenerationTaskJdbcRepository taskRepository, IProjectSearchService projectSearchService,
            IProjectEditingContextService projectEditingContextService, EdtEcoaExportService ecoaExportService, CurrentUserService currentUserService,
            IComponentCodeVersionService componentCodeVersionService, IComponentCodeTagService componentCodeTagService) {
        this.taskStore = Objects.requireNonNull(taskStore);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
        this.ecoaExportService = Objects.requireNonNull(ecoaExportService);
        this.currentUserService = Objects.requireNonNull(currentUserService);
        this.restTemplate = new RestTemplate();
        this.componentCodeVersionService = Objects.requireNonNull(componentCodeVersionService);
        this.componentCodeTagService = Objects.requireNonNull(componentCodeTagService);
    }

    // -------------------------------------------------------------------------
    // 1. Trigger generation
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/generate/{projectId}")
    public ResponseEntity<TriggerResponse> triggerGeneration(@PathVariable String projectId, @RequestBody(required = false) GenerationRequestBody requestBody) {
        LOGGER.info("Received generation request for project: {}", projectId);

        var optProject = this.projectSearchService.findById(projectId);
        if (optProject.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Project not found: " + projectId));
        }

        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Editing context not found for project: " + projectId));
        }

        // Create task (in-memory + persist to DB)
        String taskId = UUID.randomUUID().toString();
        // workspaceId == taskId: every new generation gets its own subdirectory
        GenerationTask task = new GenerationTask(taskId, projectId);
        GenerationWorkflowMode workflowMode = requestBody != null && requestBody.workflowMode() != null ? GenerationWorkflowMode.fromString(requestBody.workflowMode().name()) : GenerationWorkflowMode.DIRECT_DEV;
        task.setWorkflowMode(workflowMode);
        task.setStatus(GenerationTask.Status.EXPORTING_XML);
        task.addLog("[INIT] 生成任务已创建，准备导出 ECOA XML...");
        // Record the user who triggered this task
        this.currentUserService.getCurrentUser().ifPresent(user -> task.setUserId(user.id()));
        this.taskStore.put(task);
        this.taskRepository.save(task);

        LOGGER.info("Created generation task: {} (workspaceId={})", taskId, task.getWorkspaceId());

        List<String> selectedPhases = requestBody != null && requestBody.selectedPhases() != null ? requestBody.selectedPhases() : GenerationWorkflowRules.defaultPhases(workflowMode, false);
        boolean continueOnError = requestBody != null && requestBody.continueOnError();
        Map<String, Map<String, String>> phaseParams = requestBody != null && requestBody.phaseParams() != null ? requestBody.phaseParams() : Map.of();
        // INTEGRATION mode: source readiness evidence must be provided for CSMGVT/LDP
        if (requestBody != null && requestBody.sourceReadinessEvidence() != null) {
            task.setSourceReadinessEvidence(requestBody.sourceReadinessEvidence());
            if (workflowMode == GenerationWorkflowMode.INTEGRATION) {
                task.setSourceState(GenerationTask.SourceState.SOURCE_READY);
            }
        }
        try {
            GenerationWorkflowRules.validate(workflowMode, selectedPhases, false);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new TriggerResponse(null, exception.getMessage()));
        }

        // Async: call Python generator — use workspaceId as sub-directory
        String workspaceId = task.getWorkspaceId();
        String callbackUrl = this.backendUrl + "/api/internal/tasks/" + taskId + "/status";
        String stepsDir = "/workspace/" + projectId + "/" + workspaceId + "/Steps";
        String outputDir = "/workspace/" + projectId + "/" + workspaceId + "/src";

        List<SelectedVersion> selectedVersions = requestBody != null ? requestBody.selectedVersions() : null;
        CompletableFuture.runAsync(() -> this.callPythonGenerator(task, stepsDir, outputDir, callbackUrl, selectedPhases, continueOnError, phaseParams, false, workflowMode, false, selectedVersions));

        return ResponseEntity.accepted().body(new TriggerResponse(taskId, "Generation started"));
    }

    // -------------------------------------------------------------------------
    // 1b. Export ECOA XML to disk (called internally or by generator service)
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/export-to-disk/{projectId}")
    public ResponseEntity<ExportToDiskResponse> exportToDisk(@PathVariable String projectId, @RequestParam(required = false) String workspaceId) {
        var optProject = this.projectSearchService.findById(projectId);
        if (optProject.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ExportToDiskResponse(false, null, null, "Project not found"));
        }

        String projectName = optProject.get().getName();
        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ExportToDiskResponse(false, null, null, "Editing context not found"));
        }

        var optZip = this.ecoaExportService.exportToZip(editingContextId, projectName);
        if (optZip.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ExportToDiskResponse(false, null, null, "Export to ZIP failed"));
        }

        // Unzip to: workspaceDir/{projectId}/{workspaceId}/Steps/
        // If no workspaceId provided (legacy callers), fall back to the old flat layout.
        try {
            Path targetDir = (workspaceId != null && !workspaceId.isBlank()) ? Paths.get(this.workspaceDir, projectId, workspaceId) : Paths.get(this.workspaceDir, projectId);
            Files.createDirectories(targetDir);
            deleteStaleNodesDeployment(targetDir);
            unzip(optZip.get(), targetDir);

            // Sanitize project name for file name: replace whitespace with underscores.
            // Must match the same logic in EdtEcoaExportService.exportStepsToZip().
            String sanitizedProjectName = projectName.replaceAll("[\\s]+", "_");
            String projectFile = sanitizedProjectName + ".project.xml";
            LOGGER.info("Exported ECOA XML to disk: {}", targetDir.resolve("Steps"));
            return ResponseEntity.ok(new ExportToDiskResponse(true, projectName, projectFile, "OK"));
        } catch (IOException e) {
            LOGGER.error("Failed to unzip ECOA export for project {}: {}", projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ExportToDiskResponse(false, null, null, e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // 1c. Validate-only: run EXVT without creating a GenerationTask (no history)
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/validate/{projectId}")
    public ResponseEntity<ValidationResult> validateEcoa(@PathVariable String projectId) {
        LOGGER.info("Received XML validation request for project: {}", projectId);

        var optProject = this.projectSearchService.findById(projectId);
        if (optProject.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ValidationResult(false, null, List.of("Project not found: " + projectId), "", ""));
        }
        String projectName = optProject.get().getName();
        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ValidationResult(false, null, List.of("Editing context not found for project: " + projectId), "", ""));
        }

        // Export ECOA XML into a fixed "validation" workspace (reused on every call)
        var optZip = this.ecoaExportService.exportToZip(editingContextId, projectName);
        if (optZip.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ValidationResult(false, null, List.of("[VALIDATE][ERROR] Failed to export ECOA XML"), "", ""));
        }

        String validationWorkspace = "validation";
        Path stepsDir;
        try {
            Path targetDir = Paths.get(this.workspaceDir, projectId, validationWorkspace);
            Files.createDirectories(targetDir);
            deleteStaleNodesDeployment(targetDir);
            unzip(optZip.get(), targetDir);
            stepsDir = targetDir.resolve("Steps");
        } catch (IOException e) {
            LOGGER.error("Failed to unzip ECOA export for validation of project {}: {}", projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ValidationResult(false, null, List.of("[VALIDATE][ERROR] " + e.getMessage()), "", ""));
        }

        // Resolve project file name (same sanitisation logic as export)
        String sanitizedProjectName = projectName.replaceAll("[\\s]+", "_");
        String projectFile = sanitizedProjectName + ".project.xml";
        String pythonProjectName = projectId + "/" + validationWorkspace + "/Steps";

        // Call Python /api/tools/execute-project synchronously (EXVT is fast, < 10s)
        String url = this.pythonGeneratorUrl + "/api/tools/execute-project";
        var pyRequest = new PythonExecuteProjectRequest(pythonProjectName, projectFile, "exvt", null, 3, false, null, null, false);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = this.restTemplate.postForObject(url, pyRequest, Map.class);
            if (body == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new ValidationResult(false, projectFile, List.of("[VALIDATE][ERROR] Empty response from ecoa-tools"), "", ""));
            }
            boolean success = Boolean.TRUE.equals(body.get("success"));
            int returnCode = body.get("return_code") instanceof Number n ? n.intValue() : -1;
            String stdout = String.valueOf(body.getOrDefault("stdout", ""));
            String stderr = String.valueOf(body.getOrDefault("stderr", ""));
            boolean passed = success && returnCode == 0;
            List<String> logs = buildValidationLogs(passed, stdout, stderr);
            return ResponseEntity.ok(new ValidationResult(passed, projectFile, logs, stdout, stderr));
        } catch (RestClientException e) {
            LOGGER.error("Failed to call ecoa-tools for validation of project {}: {}", projectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ValidationResult(false, projectFile, List.of("[VALIDATE][ERROR] ecoa-tools unreachable: " + e.getMessage()), "", ""));
        }
    }

    private List<String> buildValidationLogs(boolean passed, String stdout, String stderr) {
        List<String> logs = new ArrayList<>();
        if (!stdout.isBlank()) {
            for (String line : stdout.lines().toList()) {
                if (!line.isBlank()) logs.add("[EXVT][INFO] " + line);
            }
        }
        if (!stderr.isBlank()) {
            for (String line : stderr.lines().toList()) {
                if (!line.isBlank()) {
                    String level = line.toLowerCase().contains("error") ? "ERROR" : "WARN";
                    logs.add("[EXVT][" + level + "] " + line);
                }
            }
        }
        logs.add(passed ? "[EXVT][SUCCESS] XML validation passed ✓" : "[EXVT][ERROR] XML validation failed ✗");
        return logs;
    }

    // -------------------------------------------------------------------------
    // 1d. Re-run an existing task in its original workspace
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/generate/rerun/{taskId}")
    public ResponseEntity<TriggerResponse> rerunGeneration(@PathVariable String taskId, @RequestBody(required = false) GenerationRequestBody requestBody) {
        LOGGER.info("Received re-run request for task: {}", taskId);

        // Load the original task to retrieve its workspaceId and projectId
        GenerationTask originalTask = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));
        if (originalTask == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Original task not found: " + taskId));
        }

        String projectId = originalTask.getProjectId();
        String workspaceId = originalTask.getWorkspaceId(); // reuse workspace

        var optProject = this.projectSearchService.findById(projectId);
        if (optProject.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Project not found: " + projectId));
        }
        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Editing context not found for project: " + projectId));
        }

        // Determine whether this rerun is a "continuing" run (execution phases: CSMGVT/LDP)
        // The frontend sets continuing=true when retrying an execution-phase failure.
        boolean continuing = requestBody != null && requestBody.continuing();

        // Create a new task record that shares the original workspace
        String newTaskId = UUID.randomUUID().toString();
        GenerationTask newTask = new GenerationTask(newTaskId, projectId);
        GenerationWorkflowMode workflowMode = requestBody != null && requestBody.workflowMode() != null ? GenerationWorkflowMode.fromString(requestBody.workflowMode().name()) : originalTask.getWorkflowMode();
        newTask.setWorkflowMode(workflowMode);
        newTask.setWorkspaceId(workspaceId); // reuse original workspace directory
        // Carry over persisted project file references from the original task
        newTask.setBaseProjectFile(originalTask.getBaseProjectFile());
        newTask.setActiveProjectFile(originalTask.getActiveProjectFile());
        newTask.setHarnessProjectFile(originalTask.getHarnessProjectFile());
        newTask.setSourceState(originalTask.getSourceState());
        newTask.setCodeWorkspacePath(originalTask.getCodeWorkspacePath());
        // When continuing=true (retrying execution phases), skip XML export phase
        if (continuing) {
            newTask.setStatus(GenerationTask.Status.GENERATING);
            newTask.addLog("[INIT] Re-run 任务已创建（复用 workspace: " + workspaceId + "），跳过导出直接执行...");
        } else {
            newTask.setStatus(GenerationTask.Status.EXPORTING_XML);
            newTask.addLog("[INIT] Re-run 任务已创建（复用 workspace: " + workspaceId + "），准备导出 ECOA XML...");
        }
        // Record the user who triggered this re-run
        this.currentUserService.getCurrentUser().ifPresent(user -> newTask.setUserId(user.id()));
        this.taskStore.put(newTask);
        this.taskRepository.save(newTask);

        LOGGER.info("Created re-run task: {} (reusing workspaceId={}, continuing={})", newTaskId, workspaceId, continuing);
        List<String> selectedPhases = requestBody != null && requestBody.selectedPhases() != null ? requestBody.selectedPhases() : GenerationWorkflowRules.defaultPhases(workflowMode, continuing);
        boolean continueOnError = requestBody != null && requestBody.continueOnError();
        Map<String, Map<String, String>> phaseParams = requestBody != null && requestBody.phaseParams() != null ? requestBody.phaseParams() : Map.of();
        try {
            GenerationWorkflowRules.validate(workflowMode, selectedPhases, continuing);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new TriggerResponse(null, exception.getMessage()));
        }

        // When retrying execution phases (continuing=true), skip re-exporting XML to preserve
        // any user-edited source code already present in the workspace.
        boolean skipExport = continuing;

        String callbackUrl = this.backendUrl + "/api/internal/tasks/" + newTaskId + "/status";
        String stepsDir = "/workspace/" + projectId + "/" + workspaceId + "/Steps";
        String outputDir = "/workspace/" + projectId + "/" + workspaceId + "/src";

        List<SelectedVersion> rerunSelectedVersions = requestBody != null ? requestBody.selectedVersions() : null;
        CompletableFuture.runAsync(() -> this.callPythonGenerator(newTask, stepsDir, outputDir, callbackUrl, selectedPhases, continueOnError, phaseParams, skipExport, workflowMode, continuing, rerunSelectedVersions));

        return ResponseEntity.accepted().body(new TriggerResponse(newTaskId, "Re-run started in workspace: " + workspaceId));
    }

    // -------------------------------------------------------------------------
    // 1d. Continue an AWAITING_CODE task (same task record, no new task)
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/generate/continue/{taskId}")
    public ResponseEntity<TriggerResponse> continueGeneration(@PathVariable String taskId, @RequestBody(required = false) GenerationRequestBody requestBody) {
        LOGGER.info("Received continue request for AWAITING_CODE task: {}", taskId);

        GenerationTask task = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerResponse(null, "Task not found: " + taskId));
        }
        if (task.getStatus() != GenerationTask.Status.AWAITING_CODE) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new TriggerResponse(null, "Task is not in AWAITING_CODE state: " + task.getStatus()));
        }
        // Both DIRECT_DEV and HARNESS_DEV can continue after AWAITING_CODE (CODE_EDIT_REQUIRED)
        GenerationWorkflowMode taskMode = task.getWorkflowMode();
        if (taskMode != GenerationWorkflowMode.DIRECT_DEV && taskMode != GenerationWorkflowMode.HARNESS_DEV) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new TriggerResponse(null, "Only DIRECT_DEV or HARNESS_DEV tasks can continue after AWAITING_CODE, current: " + taskMode));
        }
        if (requestBody != null && requestBody.workflowMode() != null && requestBody.workflowMode() != task.getWorkflowMode()) {
            return ResponseEntity.badRequest().body(new TriggerResponse(null, "workflowMode does not match the existing task"));
        }

        String projectId = task.getProjectId();
        String workspaceId = task.getWorkspaceId();

        List<String> selectedPhases = requestBody != null && requestBody.selectedPhases() != null ? requestBody.selectedPhases() : GenerationWorkflowRules.defaultPhases(task.getWorkflowMode(), true);
        boolean continueOnError = requestBody != null && requestBody.continueOnError();
        Map<String, Map<String, String>> phaseParams = requestBody != null && requestBody.phaseParams() != null ? requestBody.phaseParams() : Map.of();
        try {
            GenerationWorkflowRules.validate(task.getWorkflowMode(), selectedPhases, true);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new TriggerResponse(null, exception.getMessage()));
        }

        // Reset the SAME task — no new record is created in the DB
        task.addLog("[ECOA-WEB] 用户已完成业务逻辑编写，继续执行阶段: " + selectedPhases);
        task.setStatus(GenerationTask.Status.GENERATING);
        task.setProgress(0);
        // Mark source as user-edited when continuing from AWAITING_CODE
        task.setSourceState(GenerationTask.SourceState.USER_EDITED);
        this.taskStore.put(task);
        this.taskRepository.save(task);

        String callbackUrl = this.backendUrl + "/api/internal/tasks/" + taskId + "/status";
        String stepsDir = "/workspace/" + projectId + "/" + workspaceId + "/Steps";
        String outputDir = "/workspace/" + projectId + "/" + workspaceId + "/src";

        LOGGER.info("Continuing task {} (workspaceId={}) phases: {} — skipExport=true, reusing persisted activeProjectFile={}", taskId, workspaceId, selectedPhases, task.getActiveProjectFile());
        final GenerationTask taskFinal = task;
        List<SelectedVersion> continueSelectedVersions = requestBody != null ? requestBody.selectedVersions() : null;
        // IMPORTANT: skipExport=true to prevent re-exporting EDT XML and overwriting user code
        CompletableFuture.runAsync(() -> this.callPythonGenerator(taskFinal, stepsDir, outputDir, callbackUrl, selectedPhases, continueOnError, phaseParams, true, task.getWorkflowMode(), true, continueSelectedVersions));

        // Return the SAME taskId — frontend polls the existing task record
        return ResponseEntity.accepted().body(new TriggerResponse(taskId, "Task " + taskId + " continuing with phases: " + selectedPhases));
    }

    private static void unzip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("ZIP entry outside target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.write(entryPath, zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Deletes nodes_deployment.xml from a previous export so it never lingers when
     * the new export omits it (nodes with no valid IP addresses).
     */
    private static void deleteStaleNodesDeployment(Path targetDir) {
        try {
            Files.deleteIfExists(targetDir.resolve("Steps/5-Integration/nodes_deployment.xml"));
        } catch (IOException ignored) {
            // Best effort — a stale file is worse than a failed delete, but don't abort the export.
        }
    }

    // -------------------------------------------------------------------------
    // 2. Poll single task status
    // -------------------------------------------------------------------------

    @GetMapping("/api/edt/ecoa/generate/status/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        // Try in-memory first (fastest), then fall back to DB
        var opt = this.taskStore.findById(taskId);
        if (opt.isPresent()) {
            return ResponseEntity.ok(TaskStatusResponse.from(opt.get()));
        }
        return this.taskRepository.findById(taskId).map(task -> ResponseEntity.ok(TaskStatusResponse.from(task))).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // -------------------------------------------------------------------------
    // 2b. Fetch Initial Assembly Components dynamically
    // -------------------------------------------------------------------------

    @GetMapping("/api/edt/ecoa/generate/components/{projectId}")
    public ResponseEntity<List<String>> getInitialAssemblyComponents(@PathVariable String projectId) {
        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var componentsOpt = this.ecoaExportService.getInitialAssemblyComponents(editingContextId);
        return componentsOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // -------------------------------------------------------------------------
    // 2c. Fetch Integration mode component versions
    // -------------------------------------------------------------------------

    @GetMapping("/api/edt/ecoa/integration/components/{projectId}")
    public ResponseEntity<List<Map<String, Object>>> getIntegrationComponentVersions(@PathVariable String projectId) {
        String editingContextId = this.projectEditingContextService.getEditingContextId(projectId).orElse(null);
        if (editingContextId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var componentsOpt = this.ecoaExportService.getInitialAssemblyComponents(editingContextId);
        if (componentsOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<String> components = componentsOpt.get();
        // For now, return components with mock version data
        // In production, this would fetch from a version control system or database
        List<Map<String, Object>> result = components.stream()
                .map(comp -> Map.<String, Object>of(
                        "componentName", comp,
                        "versions", List.of("latest", "v1.0.0", "v1.1.0", "v2.0.0"),
                        "selectedVersion", "latest"
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // 3. List project history
    // -------------------------------------------------------------------------

    @GetMapping("/api/edt/ecoa/generate/history/{projectId}")
    public ResponseEntity<List<TaskStatusResponse>> getProjectHistory(@PathVariable String projectId) {
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        List<GenerationTask> tasks;
        if (isAdmin) {
            // Admins can see all tasks for the project
            tasks = this.taskRepository.findByProjectId(projectId);
        } else {
            // Non-admin users only see their own tasks
            String userId = currentUser.map(AppUser::id).orElse(null);
            if (userId != null) {
                tasks = this.taskRepository.findByProjectIdAndUserId(projectId, userId);
            } else {
                // Unauthenticated users see nothing
                tasks = List.of();
            }
        }
        List<TaskStatusResponse> history = tasks.stream().map(TaskStatusResponse::from).toList();
        return ResponseEntity.ok(history);
    }

    // -------------------------------------------------------------------------
    // 3b. Get computing nodes for a workspace (from deployment.xml)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of logical computing nodes defined in the workspace deployment XML.
     * Each node entry contains the node ID and the list of protection domains running on it.
     */
    @GetMapping("/api/edt/ecoa/generate/download/{taskId}/nodes")
    public ResponseEntity<List<ComputingNodeInfo>> getComputingNodes(@PathVariable String taskId) {
        GenerationTask task = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Access control
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        if (!isAdmin) {
            String userId = currentUser.map(AppUser::id).orElse(null);
            if (userId == null || !userId.equals(task.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        String projectId = task.getProjectId();
        String workspaceId = task.getWorkspaceId();
        Path stepsDir = Paths.get(this.workspaceDir, projectId, workspaceId, "Steps");

        if (!Files.exists(stepsDir) || !Files.isDirectory(stepsDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<ComputingNodeInfo> nodes = parseComputingNodes(stepsDir);
        return ResponseEntity.ok(nodes);
    }

    // -------------------------------------------------------------------------
    // 3c. Download workspace as ZIP (only compiled executables + bundled libs)
    // -------------------------------------------------------------------------

    /**
     * Downloads the compiled executables and their bundled system libraries as a ZIP file.
     *
     * <p>The ZIP contains two top-level directories:
     * <ul>
     *   <li>{@code bin/} — executable binaries from {@code 6-Output/build/bin/}
     *       (all of them when no nodeId is given; only the executables whose name
     *       matches one of the protection domains assigned to the requested node otherwise)</li>
     *   <li>{@code lib/} — all system shared libraries from {@code 6-Output/build/lib/}
     *       (always included in full so the executables can run)</li>
     * </ul>
     *
     * <p>Executables are statically linked with internal ECOA / component libraries
     * (via {@code -DLDP_LINK_TYPE=STATIC}) so no internal {@code .so} files are needed.
     * The {@code lib/} directory only contains system libraries bundled by the compile
     * script ({@code ldd}-based collection).
     *
     * @param taskId task whose workspace to download
     * @param nodeId optional computing-node ID; when supplied only that node's executables are included
     */
    @GetMapping("/api/edt/ecoa/generate/download/{taskId}")
    public ResponseEntity<StreamingResponseBody> downloadWorkspace(@PathVariable String taskId,
            @RequestParam(required = false) String nodeId) {
        GenerationTask task = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Access control: non-admin users can only download their own tasks
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        if (!isAdmin) {
            String userId = currentUser.map(AppUser::id).orElse(null);
            if (userId == null || !userId.equals(task.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        String projectId = task.getProjectId();
        String workspaceId = task.getWorkspaceId();
        Path stepsDir = Paths.get(this.workspaceDir, projectId, workspaceId, "Steps");

        if (!Files.exists(stepsDir) || !Files.isDirectory(stepsDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Locate the build directory that contains bin/ and lib/
        Path buildDir = findBuildDir(stepsDir);
        if (buildDir == null) {
            LOGGER.warn("No compiled build/ directory found in Steps for task {}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path binDir = buildDir.resolve("bin");
        Path libDir = buildDir.resolve("lib");

        // When nodeId is specified, validate it and determine associated protection domains
        Set<String> nodeProtectionDomains = null;
        if (nodeId != null && !nodeId.isBlank()) {
            List<ComputingNodeInfo> nodes = parseComputingNodes(stepsDir);
            ComputingNodeInfo matchedNode = nodes.stream()
                    .filter(n -> nodeId.equals(n.nodeId()))
                    .findFirst()
                    .orElse(null);
            if (matchedNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            nodeProtectionDomains = Set.copyOf(matchedNode.protectionDomains());
        }

        final Set<String> finalNodePDs = nodeProtectionDomains;
        final Path finalBinDir = binDir;
        final Path finalLibDir = libDir;
        String safeNodeId = (nodeId != null && !nodeId.isBlank()) ? "-" + nodeId.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "";
        String zipFileName = "ecoa-" + projectId + "-" + workspaceId + safeNodeId + ".zip";

        StreamingResponseBody responseBody = (OutputStream out) -> {
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                // Pack bin/ directory (filtered by node PDs if nodeId was given)
                zipBinDir(finalBinDir, finalNodePDs, zos);
                // Pack lib/ directory (all system libraries, always full)
                zipLibDir(finalLibDir, zos);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"")
                .body(responseBody);
    }

    /**
     * Locates the {@code 6-Output/build} directory (case-insensitive variant matching)
     * under the given Steps directory.  Returns {@code null} when not found.
     */
    private static Path findBuildDir(Path stepsDir) {
        // Candidate output directory names produced by LDP
        String[] outputDirNames = {"6-output", "6-Output", "Output", "output", "build-output"};
        for (String name : outputDirNames) {
            Path candidate = stepsDir.resolve(name).resolve("build");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        // Fallback: walk the tree up to depth 3 looking for a build/bin directory
        try (var stream = Files.walk(stepsDir, 3)) {
            return stream
                    .filter(p -> Files.isDirectory(p) && "build".equals(p.getFileName().toString()))
                    .filter(p -> Files.isDirectory(p.resolve("bin")))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.warn("Error walking Steps dir to find build/: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses the deployment XML in the given Steps directory and returns a list of computing nodes,
     * each with the protection domains assigned to it.
     *
     * <p>Looks for {@code *.deployment.xml} files under {@code stepsDir/5-Integration/}
     * (and falls back to the whole Steps dir) and extracts
     * {@code protectionDomain[name].executeOn[computingNode]} mappings.</p>
     */
    private static List<ComputingNodeInfo> parseComputingNodes(Path stepsDir) {
        // Find deployment XML(s): prefer 5-Integration sub-directory
        List<Path> deploymentFiles = new ArrayList<>();
        try {
            Path integrationDir = stepsDir.resolve("5-Integration");
            if (Files.isDirectory(integrationDir)) {
                try (var stream = Files.walk(integrationDir, 2)) {
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".deployment.xml"))
                          .forEach(deploymentFiles::add);
                }
            }
            if (deploymentFiles.isEmpty()) {
                // Fallback: search entire Steps tree (max depth 4)
                try (var stream = Files.walk(stepsDir, 4)) {
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".deployment.xml"))
                          .forEach(deploymentFiles::add);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to search for deployment XML files", e);
        }

        if (deploymentFiles.isEmpty()) {
            return List.of();
        }

        // Use the first deployment file found (prefer harness-free one)
        Path deploymentFile = deploymentFiles.get(0);
        // node id -> list of protection domain names
        Map<String, List<String>> nodeToProtectionDomains = new LinkedHashMap<>();

        try (InputStream is = Files.newInputStream(deploymentFile)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            NodeList protectionDomains = doc.getElementsByTagName("protectionDomain");
            for (int i = 0; i < protectionDomains.getLength(); i++) {
                Element pd = (Element) protectionDomains.item(i);
                String pdName = pd.getAttribute("name");
                if (pdName.isBlank()) {
                    continue;
                }

                // Find executeOn child element
                NodeList executeOnList = pd.getElementsByTagName("executeOn");
                if (executeOnList.getLength() == 0) {
                    continue;
                }
                Element executeOn = (Element) executeOnList.item(0);
                String computingNode = executeOn.getAttribute("computingNode");
                if (computingNode.isBlank()) {
                    continue;
                }

                nodeToProtectionDomains.computeIfAbsent(computingNode, k -> new ArrayList<>()).add(pdName);
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOGGER.warn("Failed to parse deployment XML: {}", deploymentFile, e);
        }

        return nodeToProtectionDomains.entrySet().stream()
                .map(entry -> new ComputingNodeInfo(entry.getKey(), List.copyOf(entry.getValue())))
                .collect(Collectors.toList());
    }

    /**
     * Adds all regular files from {@code binDir} into the ZIP under the {@code bin/} prefix.
     *
     * <p>When {@code protectionDomains} is non-null only executables whose file name
     * contains (case-insensitively) one of the protection-domain names or equals
     * {@code "platform"} are included.  When null every file in the directory is added.
     *
     * @param binDir            path to {@code build/bin/}; if missing the method is a no-op
     * @param protectionDomains set of PD names to filter on, or {@code null} for all files
     * @param zos               target ZIP stream
     */
    private static void zipBinDir(Path binDir, Set<String> protectionDomains, ZipOutputStream zos) throws IOException {
        if (binDir == null || !Files.isDirectory(binDir)) {
            return;
        }
        try (var stream = Files.list(binDir)) {
            for (Path path : stream.sorted().toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (protectionDomains != null) {
                    // Filter: only include executables matching a protection domain or "platform"
                    String fileLower = fileName.toLowerCase();
                    boolean matches = "platform".equals(fileLower)
                            || protectionDomains.stream().anyMatch(pd -> {
                                String pdLower = pd.toLowerCase();
                                return fileLower.equals(pdLower)
                                        || fileLower.equals("pd_" + pdLower)
                                        || fileLower.startsWith(pdLower + ".")
                                        || fileLower.startsWith("pd_" + pdLower + ".")
                                        || fileLower.contains(pdLower);
                            });
                    if (!matches) {
                        continue;
                    }
                }
                zos.putNextEntry(new ZipEntry("bin/" + fileName));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * Adds all regular files from {@code libDir} into the ZIP under the {@code lib/} prefix.
     * Typically contains system shared libraries bundled by the compile script.
     *
     * @param libDir path to {@code build/lib/}; if missing the method is a no-op
     * @param zos    target ZIP stream
     */
    private static void zipLibDir(Path libDir, ZipOutputStream zos) throws IOException {
        if (libDir == null || !Files.isDirectory(libDir)) {
            return;
        }
        try (var stream = Files.list(libDir)) {
            for (Path path : stream.sorted().toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry("lib/" + path.getFileName()));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3c. Delete task
    // -------------------------------------------------------------------------

    @DeleteMapping("/api/edt/ecoa/generate/task/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable String taskId) {
        GenerationTask task = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Access control: non-admin users can only delete their own tasks
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        if (!isAdmin) {
            String userId = currentUser.map(AppUser::id).orElse(null);
            if (userId == null || !userId.equals(task.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // Remove from in-memory store and database
        this.taskStore.delete(taskId);
        this.taskRepository.deleteById(taskId);

        LOGGER.info("Deleted task: {}", taskId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // 3d. Get component version content (for INTEGRATION mode source overlay)
    // -------------------------------------------------------------------------

    @GetMapping("/api/edt/ecoa/component-version/{versionId}")
    public ResponseEntity<ComponentCodeVersionDTO> getComponentVersion(@PathVariable String versionId) {
        try {
            UUID versionUuid = UUID.fromString(versionId);
            Optional<ComponentCodeVersionDTO> versionOpt = this.componentCodeVersionService.getComponentCodeVersion(versionUuid);
            return versionOpt.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid version ID format: {}", versionId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // -------------------------------------------------------------------------
    // 4. Python service callback
    // -------------------------------------------------------------------------

    @PostMapping("/api/internal/tasks/{taskId}/status")
    public ResponseEntity<Void> receiveCallback(@PathVariable String taskId, @RequestBody CallbackPayload payload) {

        LOGGER.info("Callback for task {}: status={}, subStatus={}, progress={}", taskId, payload.status(), payload.subStatus(), payload.progress());

        // Load from memory or DB
        GenerationTask task = this.taskStore.findById(taskId).orElseGet(() -> this.taskRepository.findById(taskId).orElse(null));

        if (task == null) {
            LOGGER.warn("Unknown task ID in callback: {}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (payload.status() != null) {
            try {
                GenerationTask.Status oldStatus = task.getStatus();
                GenerationTask.Status newStatus = GenerationTask.Status.valueOf(payload.status());
                task.setStatus(newStatus);
                LOGGER.info("Task {} status updated: {} -> {}", taskId, oldStatus, newStatus);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown status: {}", payload.status());
            }
        }
        if (payload.subStatus() != null) {
            try {
                task.setSubStatus(GenerationTask.SubStatus.valueOf(payload.subStatus()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown subStatus: {}", payload.subStatus());
            }
        }
        if (payload.progress() != null) {
            task.setProgress(payload.progress());
        }
        if (payload.outputPath() != null) {
            task.setOutputPath(payload.outputPath());
        }
        if (payload.workflowMode() != null) {
            try {
                task.setWorkflowMode(GenerationWorkflowMode.fromString(payload.workflowMode()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown workflowMode: {}", payload.workflowMode());
            }
        }
        if (payload.baseProjectFile() != null) {
            task.setBaseProjectFile(payload.baseProjectFile());
        }
        if (payload.activeProjectFile() != null) {
            task.setActiveProjectFile(payload.activeProjectFile());
        }
        if (payload.harnessProjectFile() != null) {
            task.setHarnessProjectFile(payload.harnessProjectFile());
        }
        if (payload.codeWorkspacePath() != null) {
            task.setCodeWorkspacePath(payload.codeWorkspacePath());
        }
        if (payload.sourceState() != null) {
            try {
                task.setSourceState(GenerationTask.SourceState.valueOf(payload.sourceState()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown sourceState: {}", payload.sourceState());
            }
        }
        if (payload.sourceReadinessEvidence() != null) {
            task.setSourceReadinessEvidence(payload.sourceReadinessEvidence());
        }
        if (payload.csmgvtResult() != null) {
            task.setCsmgvtResult(payload.csmgvtResult());
        }
        if (payload.csmgvtProductCheck() != null) {
            task.setCsmgvtProductCheck(payload.csmgvtProductCheck());
        }
        if (payload.csmgvtCompileErrors() != null && !payload.csmgvtCompileErrors().isEmpty()) {
            task.setCsmgvtCompileErrors(payload.csmgvtCompileErrors());
        }
        if (payload.csmgvtCsmResult() != null) {
            task.setCsmgvtCsmResult(payload.csmgvtCsmResult());
        }
        if (payload.testWorkspacePath() != null) {
            task.setTestWorkspacePath(payload.testWorkspacePath());
        }
        if (payload.patchArtifactPath() != null) {
            task.setPatchArtifactPath(payload.patchArtifactPath());
        }
        if (payload.sourceVersionId() != null) {
            task.setSourceVersionId(payload.sourceVersionId());
        }
        if (payload.sourceRevision() != null) {
            task.setSourceRevision(payload.sourceRevision());
        }
        if (payload.logs() != null && !payload.logs().isEmpty()) {
            task.addLogs(payload.logs());
        } else if (payload.log() != null) {
            task.addLog(payload.log());
        }

        // Persist to DB on every callback
        this.taskRepository.save(task);

        // If task is terminal, also add to in-memory store for fast polling
        this.taskStore.put(task);

        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void callPythonGenerator(GenerationTask task, String stepsDir, String outputDir, String callbackUrl, List<String> selectedPhases, boolean continueOnError,
            Map<String, Map<String, String>> phaseParams, boolean skipExport, GenerationWorkflowMode workflowMode, boolean continuing, List<SelectedVersion> selectedVersions) {
        task.setStatus(GenerationTask.Status.GENERATING);
        task.addLog("[ECOA-WEB] 正在调用 Python 生成器微服务...");
        this.taskRepository.save(task);

        try {
            String url = this.pythonGeneratorUrl + "/api/generate";
            String sourceReadinessEvidence = task.getSourceReadinessEvidence();
            var requestBody = new PythonGenerateRequest(task.getTaskId(), task.getProjectId(), task.getWorkspaceId(), stepsDir, outputDir, callbackUrl, selectedPhases, continueOnError, phaseParams,
                    skipExport, workflowMode, continuing, task.getBaseProjectFile(), task.getActiveProjectFile(), task.getHarnessProjectFile(), sourceReadinessEvidence, selectedVersions);
            this.restTemplate.postForEntity(url, requestBody, Void.class);
            LOGGER.info("Python generator called successfully for task {}", task.getTaskId());
            task.addLog("[ECOA-WEB] Python 微服务已接受请求（202 Accepted），后台生成中...");
            this.taskRepository.save(task);
        } catch (Exception e) {
            LOGGER.error("Failed to call Python generator for task {}: {}", task.getTaskId(), e.getMessage());
            task.addLog("[ERROR] 无法连接到 Python 生成器微服务: " + e.getMessage());
            task.addLog("[ERROR] 请确认 Python 服务已启动");
            task.setStatus(GenerationTask.Status.FAILED);
            task.setProgress(0);
            this.taskRepository.save(task);
        }
    }

    // -------------------------------------------------------------------------
    // 7. Code Backflow — scan, patch, apply
    // -------------------------------------------------------------------------

    @PostMapping("/api/edt/ecoa/backflow/scan/{taskId}")
    public ResponseEntity<Map<String, Object>> backflowScan(@PathVariable String taskId, @RequestBody(required = false) BackflowRequestBody requestBody) {
        var optTask = this.taskStore.findById(taskId).or(() -> this.taskRepository.findById(taskId));
        if (optTask.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found: " + taskId));
        }
        var task = optTask.get();

        // Allow backflow for all workflow modes (user assumes responsibility for testing)

        List<String> returnableComponents = requestBody != null ? requestBody.returnableComponents() : null;

        try {
            var payload = Map.<String, Object>ofEntries(
                Map.entry("taskId", taskId),
                Map.entry("projectId", task.getProjectId()),
                Map.entry("workspaceId", task.getWorkspaceId()),
                Map.entry("workspacePath", "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/Steps"),
                Map.entry("outputPath", task.getOutputPath() != null ? task.getOutputPath() : "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/src"),
                Map.entry("returnableComponents", returnableComponents != null ? returnableComponents : List.of())
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = this.restTemplate.postForObject(
                this.pythonGeneratorUrl + "/api/backflow/scan", payload, Map.class);
            return ResponseEntity.ok(result);
        } catch (HttpClientErrorException.NotFound e) {
            String errorBody = e.getResponseBodyAsString();
            LOGGER.error("Backflow scan 404 for task {}: {}", taskId, errorBody);
            // Check if it's Flask default 404 (endpoint not found) or workspace not found
            if (errorBody != null && errorBody.contains("The requested URL was not found")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "代码扫描服务端点未实现(/api/backflow/scan)，请联系管理员检查 Python 服务"));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "工作空间不存在，可能已被清理或删除，请重新执行任务"));
        } catch (HttpClientErrorException e) {
            LOGGER.error("Backflow scan HTTP error for task {}: {}", taskId, e.getMessage());
            String errorBody = e.getResponseBodyAsString();
            if (errorBody != null && errorBody.contains("Workspace not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "工作空间不存在，可能已被清理或删除，请重新执行任务"));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "代码扫描服务暂不可用，请稍后重试"));
        } catch (RestClientException e) {
            LOGGER.error("Backflow scan connection failed for task {}: {}", taskId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "代码扫描服务连接失败，请稍后重试"));
        } catch (Exception e) {
            LOGGER.error("Backflow scan failed for task {}: {}", taskId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "代码扫描失败，请稍后重试"));
        }
    }

    @PostMapping("/api/edt/ecoa/backflow/patch/{taskId}")
    public ResponseEntity<Map<String, Object>> backflowPatch(@PathVariable String taskId, @RequestBody(required = false) BackflowRequestBody requestBody) {
        var optTask = this.taskStore.findById(taskId).or(() -> this.taskRepository.findById(taskId));
        if (optTask.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found: " + taskId));
        }
        var task = optTask.get();

        List<String> returnableComponents = requestBody != null ? requestBody.returnableComponents() : null;

        try {
            var payload = Map.<String, Object>ofEntries(
                Map.entry("taskId", taskId),
                Map.entry("projectId", task.getProjectId()),
                Map.entry("workspaceId", task.getWorkspaceId()),
                Map.entry("workspacePath", "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/Steps"),
                Map.entry("outputPath", task.getOutputPath() != null ? task.getOutputPath() : "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/src"),
                Map.entry("returnableComponents", returnableComponents != null ? returnableComponents : List.of())
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = this.restTemplate.postForObject(
                this.pythonGeneratorUrl + "/api/backflow/patch", payload, Map.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Backflow patch generation failed for task {}: {}", taskId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Backflow patch failed: " + e.getMessage()));
        }
    }

    @PostMapping("/api/edt/ecoa/backflow/apply/{taskId}")
    public ResponseEntity<Map<String, Object>> backflowApply(@PathVariable String taskId, @RequestBody BackflowApplyRequestBody requestBody) {
        var optTask = this.taskStore.findById(taskId).or(() -> this.taskRepository.findById(taskId));
        if (optTask.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found: " + taskId));
        }
        var task = optTask.get();

        String callbackUrl = this.backendUrl + "/api/internal/tasks/" + taskId + "/status";

        try {
            var payload = Map.<String, Object>ofEntries(
                Map.entry("taskId", taskId),
                Map.entry("projectId", task.getProjectId()),
                Map.entry("workspaceId", task.getWorkspaceId()),
                Map.entry("workspacePath", "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/Steps"),
                Map.entry("outputPath", task.getOutputPath() != null ? task.getOutputPath() : "/workspace/" + task.getProjectId() + "/" + task.getWorkspaceId() + "/src"),
                Map.entry("returnableComponents", requestBody.returnableComponents() != null ? requestBody.returnableComponents() : List.of()),
                Map.entry("mode", requestBody.mode()),
                Map.entry("callbackUrl", callbackUrl),
                Map.entry("tag", requestBody.tag() != null ? requestBody.tag() : ""),
                Map.entry("commitMessage", requestBody.commitMessage() != null ? requestBody.commitMessage() : "")
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = this.restTemplate.postForObject(
                this.pythonGeneratorUrl + "/api/backflow/apply", payload, Map.class);

            // Update task with backflow result
            if (result != null) {
                Boolean success = (Boolean) result.get("success");
                if (Boolean.TRUE.equals(success)) {
                    task.setSubStatus(GenerationTask.SubStatus.CODE_BACKFLOW_APPLIED);
                    task.addLog("[BACKFLOW] Patch applied successfully");

                    // Create component code versions for backflowed components
                    this.createComponentCodeVersionsFromBackflow(task, result, requestBody);
                } else {
                    task.setSubStatus(GenerationTask.SubStatus.CONFLICT);
                    task.addLog("[BACKFLOW] Patch application failed — conflicts detected");
                }
                Object sourceRevision = result.get("sourceRevision");
                if (sourceRevision != null) {
                    task.addLog("[BACKFLOW] Source revision: " + sourceRevision);
                }
                this.taskRepository.save(task);
                this.taskStore.put(task);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("Backflow apply failed for task {}: {}", taskId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Backflow apply failed: " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // DTO Records
    // -------------------------------------------------------------------------

    /** Request body from frontend with phase selection. */
    public record GenerationRequestBody(GenerationWorkflowMode workflowMode, List<String> selectedPhases, boolean continueOnError, Map<String, Map<String, String>> phaseParams,
            String sourceReadinessEvidence, List<SelectedVersion> selectedVersions, boolean continuing) {
    }

    /** Selected component version for INTEGRATION mode. */
    public record SelectedVersion(String componentId, String componentName, String versionId, String versionName) {
    }

    public record TriggerResponse(String taskId, String message) {
    }

    public record TaskStatusResponse(String taskId, String projectId, String workspaceId, String status, String subStatus, int progress, String outputPath, List<String> logs, String workflowMode,
            String baseProjectFile, String activeProjectFile, String harnessProjectFile, String userId, String sourceState, String codeWorkspacePath, String sourceReadinessEvidence,
            java.util.Map<String, Object> csmgvtResult,
            java.util.Map<String, Object> csmgvtProductCheck,
            List<String> csmgvtCompileErrors,
            java.util.Map<String, Object> csmgvtCsmResult,
            String testWorkspacePath, String patchArtifactPath, String sourceVersionId, String sourceRevision,
            Instant createdAt, Instant updatedAt) {

        static TaskStatusResponse from(GenerationTask task) {
            return new TaskStatusResponse(task.getTaskId(), task.getProjectId(), task.getWorkspaceId(), task.getStatus().name(), task.getSubStatus().name(), task.getProgress(), task.getOutputPath(),
                    task.getLogs(), task.getWorkflowMode().name(), task.getBaseProjectFile(), task.getActiveProjectFile(), task.getHarnessProjectFile(), task.getUserId(),
                    task.getSourceState() != null ? task.getSourceState().name() : null, task.getCodeWorkspacePath(), task.getSourceReadinessEvidence(),
                    task.getCsmgvtResult(),
                    task.getCsmgvtProductCheck(),
                    task.getCsmgvtCompileErrors(),
                    task.getCsmgvtCsmResult(),
                    task.getTestWorkspacePath(), task.getPatchArtifactPath(), task.getSourceVersionId(), task.getSourceRevision(),
                    task.getCreatedAt(), task.getUpdatedAt());
        }
    }

    /** Payload sent from the Python micro-service callback. */
    public record CallbackPayload(String status, String subStatus, Integer progress, String outputPath, String log, List<String> logs, String workflowMode, String baseProjectFile,
            String activeProjectFile, String harnessProjectFile, String codeWorkspacePath, String sourceState, String sourceReadinessEvidence,
            java.util.Map<String, Object> csmgvtResult,
            java.util.Map<String, Object> csmgvtProductCheck,
            List<String> csmgvtCompileErrors,
            java.util.Map<String, Object> csmgvtCsmResult,
            String testWorkspacePath, String patchArtifactPath, String sourceVersionId, String sourceRevision) {
    }

    /** Request body sent to the Python generator micro-service. */
    public record PythonGenerateRequest(String taskId, String projectId, String workspaceId, String stepsDir, String outputDir, String callbackUrl, List<String> selectedPhases,
            boolean continueOnError, Map<String, Map<String, String>> phaseParams, boolean skipExport, GenerationWorkflowMode workflowMode, boolean continuing, String baseProjectFile,
            String activeProjectFile, String harnessProjectFile, String sourceReadinessEvidence, List<SelectedVersion> selectedVersions) {
    }

    /** Response from the export-to-disk endpoint. */
    public record ExportToDiskResponse(boolean success, String projectName, String projectFile, String message) {
    }

    /** Response from the validate endpoint (EXVT-only, not stored in history). */
    public record ValidationResult(boolean success, String projectFile, List<String> logs, String stdout, String stderr) {
    }

    /** Request sent to Python /api/tools/execute-project for synchronous tool execution. */
    public record PythonExecuteProjectRequest(
            String project_name, String project_file, String tool, String checker,
            int verbose, boolean compile, String config_file, String log_library, boolean force) {
    }

    /** Request body for backflow scan/patch endpoints. */
    public record BackflowRequestBody(List<String> returnableComponents) {
    }

    /** Request body for backflow apply endpoint. */
    public record BackflowApplyRequestBody(List<String> returnableComponents, String mode, String tag, String commitMessage) {
    }

    /**
     * Create component code versions from backflow result.
     * Reads the backflowed files and creates version records for each component.
     */
    @SuppressWarnings("unchecked")
    private void createComponentCodeVersionsFromBackflow(GenerationTask task, Map<String, Object> result, BackflowApplyRequestBody requestBody) {
        try {
            List<String> appliedFiles = (List<String>) result.get("appliedFiles");
            if (appliedFiles == null || appliedFiles.isEmpty()) {
                return;
            }

            UUID projectId = UUID.fromString(task.getProjectId());
            String sourceRevision = (String) result.get("sourceRevision");

            // Resolve the display name for the author: prefer username of the current request user,
            // fall back to the task's userId so at least something meaningful is stored.
            String authorName = this.currentUserService.getCurrentUser()
                    .map(AppUser::username)
                    .orElseGet(() -> task.getUserId() != null ? task.getUserId() : "system");

            // Use provided tag as version name if available, otherwise use source revision or generate one
            String userTag = requestBody.tag();
            String versionName = (userTag != null && !userTag.isBlank()) ? userTag
                    : (sourceRevision != null ? sourceRevision : "backflow-" + System.currentTimeMillis());

            // Use provided commit message if available
            String userCommitMessage = requestBody.commitMessage();
            String commitMessage = (userCommitMessage != null && !userCommitMessage.isBlank()) ? userCommitMessage
                    : "Code backflow from task: " + task.getTaskId();

            // Group files by component (first directory in path)
            Map<String, List<String>> componentFiles = new java.util.HashMap<>();
            for (String filePath : appliedFiles) {
                String componentId = filePath.split("/")[0];
                componentFiles.computeIfAbsent(componentId, k -> new ArrayList<>()).add(filePath);
            }

            // Create version for each component
            Path workspacePath = Paths.get(this.workspaceDir, task.getProjectId(), task.getWorkspaceId(), "Steps", "4-ComponentImplementations");

            for (Map.Entry<String, List<String>> entry : componentFiles.entrySet()) {
                String componentId = entry.getKey();
                List<String> files = entry.getValue();

                try {
                    // Read all files and create a zip-like content
                    StringBuilder codeContent = new StringBuilder();
                    codeContent.append("{");

                    for (int i = 0; i < files.size(); i++) {
                        String filePath = files.get(i);
                        Path fullPath = workspacePath.resolve(filePath);

                        codeContent.append("\"").append(filePath).append("\":");

                        if (Files.exists(fullPath)) {
                            String content = Files.readString(fullPath);
                            // Escape JSON string
                            content = content.replace("\\", "\\\\")
                                           .replace("\"", "\\\"")
                                           .replace("\n", "\\n")
                                           .replace("\r", "\\r");
                            codeContent.append("\"").append(content).append("\"");
                        } else {
                            codeContent.append("null");
                        }

                        if (i < files.size() - 1) {
                            codeContent.append(",");
                        }
                    }

                    codeContent.append("}");

                    // Create version
                    var createdVersion = this.componentCodeVersionService.createComponentCodeVersion(
                        projectId,
                        componentId,
                        componentId, // Use componentId as componentName for now
                        versionName,
                        codeContent.toString(),
                        commitMessage,
                        authorName,
                        null // modelVersionId
                    );

                    // If user provided a tag (and it's different from version name), create and associate it
                    if (userTag != null && !userTag.isBlank() && !userTag.equals(versionName)) {
                        try {
                            // Check if tag already exists
                            var existingTags = this.componentCodeTagService.getComponentCodeTags(projectId);
                            var existingTag = existingTags.stream()
                                    .filter(t -> t.name().equals(userTag))
                                    .findFirst();

                            ComponentCodeTagDTO tag;
                            if (existingTag.isPresent()) {
                                tag = existingTag.get();
                            } else {
                                // Create new tag with a random color
                                String color = generateRandomColor();
                                tag = this.componentCodeTagService.createComponentCodeTag(projectId, userTag, color);
                            }

                            // Associate tag with version
                            this.componentCodeTagService.addTagToVersion(createdVersion.id(), tag.id());
                            LOGGER.info("Associated tag '{}' with version {}", userTag, createdVersion.id());
                        } catch (Exception tagEx) {
                            LOGGER.warn("Failed to create/associate tag '{}': {}", userTag, tagEx.getMessage());
                        }
                    }

                    LOGGER.info("Created component code version for {} with version {}", componentId, versionName);
                } catch (Exception e) {
                    LOGGER.error("Failed to create component code version for {}: {}", componentId, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create component code versions from backflow: {}", e.getMessage());
        }
    }

    private String generateRandomColor() {
        String[] colors = {"#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#06b6d4", "#84cc16"};
        return colors[(int) (Math.random() * colors.length)];
    }

    /**
     * Represents a logical computing node identified in the deployment XML,
     * along with the protection domains that are assigned to execute on it.
     */
    public record ComputingNodeInfo(String nodeId, List<String> protectionDomains) {
    }
}
