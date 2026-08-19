/*******************************************************************************
 * Copyright (c) 2026 Dassault Aviation.
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
package org.eclipse.sirius.web.edt.importexport.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.components.collaborative.api.IEditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.eclipse.sirius.web.edt.importexport.EdtImportSiriusWebZipEventHandler;
import org.eclipse.sirius.web.edt.importexport.EdtImportSiriusWebZipInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for importing a generic Sirius Web project ZIP into an existing project.
 *
 * <p>Two endpoints are exposed:
 * <ul>
 *   <li>{@code POST /api/edt/project/import-zip/{projectId}/preview} — upload the ZIP and return
 *       the list of ComponentCode versions it contains so the user can choose which ones to import.</li>
 *   <li>{@code POST /api/edt/project/import-zip/{projectId}} — perform the actual import.
 *       The Steps document is replaced via {@link EdtImportSiriusWebZipEventHandler} (same
 *       single-root replacement strategy as ECOA XML import).  Selected ComponentCode versions
 *       are created as PENDING.</li>
 * </ul>
 */
@RestController
public class ImportProjectZipController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportProjectZipController.class);

    private final IProjectSearchService projectSearchService;
    private final IProjectEditingContextService projectEditingContextService;
    private final IEditingContextEventProcessorRegistry eventProcessorRegistry;
    private final EdtImportSiriusWebZipEventHandler importHandler;

    public ImportProjectZipController(
            IProjectSearchService projectSearchService,
            IProjectEditingContextService projectEditingContextService,
            IEditingContextEventProcessorRegistry eventProcessorRegistry,
            EdtImportSiriusWebZipEventHandler importHandler) {
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
        this.eventProcessorRegistry = Objects.requireNonNull(eventProcessorRegistry);
        this.importHandler = Objects.requireNonNull(importHandler);
    }

    // -----------------------------------------------------------------------
    // Preview endpoint
    // -----------------------------------------------------------------------

    /**
     * Upload a ZIP and return the list of ComponentCode versions it contains.
     * The ZIP is NOT persisted; this is a pure read operation.
     *
     * @return {@code { versions: [ { componentId, componentName, versionName, commitMessage,
     *         author, tags } ] }}
     */
    @PostMapping(value = "/api/edt/project/import-zip/{projectId}/preview",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PreviewResult> previewZip(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) {

        var validation = validateRequest(projectId, file);
        if (validation != null) {
            return ResponseEntity.status(validation.status())
                    .body(new PreviewResult(false, validation.message(), List.of()));
        }

        try {
            byte[] zipBytes = file.getBytes();
            List<Map<String, Object>> versions = this.importHandler.previewVersions(zipBytes);
            return ResponseEntity.ok(new PreviewResult(true, "OK", versions));
        } catch (IOException e) {
            LOGGER.error("Error reading uploaded file for preview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PreviewResult(false, "Error reading file: " + e.getMessage(), List.of()));
        }
    }

    // -----------------------------------------------------------------------
    // Import endpoint
    // -----------------------------------------------------------------------

    /**
     * Import the ZIP into the given project.
     *
     * <p>The Steps document from {@code documents/*.json} replaces the existing Steps resource
     * (single root — no duplicate nodes in the explorer).  Each version ID in
     * {@code selectedVersionIds} is imported as a PENDING ComponentCode version.
     *
     * @param selectedVersionIds comma-separated list of {@code "{componentId}/{versionName}"}
     *                           keys to import (may be empty / absent)
     */
    @PostMapping(value = "/api/edt/project/import-zip/{projectId}",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportZipResult> importProjectZip(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "selectedVersionIds", required = false) List<String> selectedVersionIds) {

        LOGGER.info("Import-zip request for project {} (versions: {})", projectId, selectedVersionIds);

        var validation = validateRequest(projectId, file);
        if (validation != null) {
            return ResponseEntity.status(validation.status())
                    .body(new ImportZipResult(false, validation.message()));
        }

        // Resolve editing context
        Optional<String> optEditingContextId = this.projectEditingContextService.getEditingContextId(projectId.toString());
        if (optEditingContextId.isEmpty()) {
            LOGGER.warn("Could not resolve editing context for project {}", projectId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ImportZipResult(false, "Editing context not found for project"));
        }
        String editingContextId = optEditingContextId.get();

        byte[] zipBytes;
        try {
            zipBytes = file.getBytes();
        } catch (IOException e) {
            LOGGER.error("Error reading uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ImportZipResult(false, "Error reading file: " + e.getMessage()));
        }

        // Resolve project name for metadata
        String projectName = this.projectSearchService.findById(projectId.toString())
                .map(p -> p.getName())
                .orElse("Imported Project");

        List<String> versions = selectedVersionIds != null ? selectedVersionIds : List.of();

        var input = new EdtImportSiriusWebZipInput(UUID.randomUUID(), zipBytes, projectName, projectId, versions);

        var processorOpt = this.eventProcessorRegistry.getOrCreateEditingContextEventProcessor(editingContextId);
        if (processorOpt.isEmpty()) {
            LOGGER.warn("No event processor for context {}", editingContextId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ImportZipResult(false, "No editing context event processor found"));
        }

        var payloadOpt = processorOpt.get().handle(input).blockOptional();
        boolean success = payloadOpt.isPresent() && payloadOpt.get() instanceof SuccessPayload;

        if (!success) {
            String msg = payloadOpt
                    .map(p -> p instanceof org.eclipse.sirius.components.core.api.ErrorPayload ep ? ep.message() : p.getClass().getSimpleName())
                    .orElse("empty payload");
            LOGGER.warn("Import failed for project {}: {}", projectId, msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ImportZipResult(false, "Import failed: " + msg));
        }

        LOGGER.info("Import successful for project {}", projectId);
        return ResponseEntity.ok(new ImportZipResult(true, "Import successful"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ValidationError validateRequest(UUID projectId, MultipartFile file) {
        if (this.projectSearchService.findById(projectId.toString()).isEmpty()) {
            return new ValidationError(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
        }
        if (file == null || file.isEmpty()) {
            return new ValidationError(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            return new ValidationError(HttpStatus.BAD_REQUEST, "File must be a ZIP archive");
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Response records
    // -----------------------------------------------------------------------

    private record ValidationError(HttpStatus status, String message) { }

    /** Response body for the preview endpoint. */
    public record PreviewResult(boolean success, String message, List<Map<String, Object>> versions) { }

    /** Response body for the import endpoint. */
    public record ImportZipResult(boolean success, String message) { }
}
