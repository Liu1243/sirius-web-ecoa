/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.collaborative.dto.CreateRepresentationInput;
import org.eclipse.sirius.components.collaborative.dto.CreateRepresentationSuccessPayload;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.IRepresentationDescription;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.eclipse.sirius.web.edt.importexport.EdtImportEcoaStepsInput;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.EdtComponentImplementationDiagramDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.compositediagram.EdtCompositeDiagramDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.EdtLogicalSystemDiagramDescriptionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import edtimplementation.ComponentImplementation;
import edtlogical.LogicalSystem;
import edtproject.Composite;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;

/**
 * REST controller for ECOA project import. Delegates to the IEditingContextEventProcessorRegistry to ensure persistence
 * via SEMANTIC_CHANGE. After Steps are imported, automatically creates Composite Diagram, Logical System Diagram, and
 * Component Implementation Diagrams.
 */
@RestController
@RequestMapping("/api/edt/ecoa")
public class EdtEcoaImportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtEcoaImportController.class);

    private final IEditingContextEventProcessorRegistry eventProcessorRegistry;

    private final IProjectSearchService projectSearchService;

    private final IProjectEditingContextService projectEditingContextService;

    private final IEditingContextSearchService editingContextSearchService;

    private final IRepresentationDescriptionSearchService representationDescriptionSearchService;

    public EdtEcoaImportController(IEditingContextEventProcessorRegistry eventProcessorRegistry, IProjectSearchService projectSearchService, IProjectEditingContextService projectEditingContextService,
            IEditingContextSearchService editingContextSearchService, IRepresentationDescriptionSearchService representationDescriptionSearchService) {
        this.eventProcessorRegistry = Objects.requireNonNull(eventProcessorRegistry);
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
        this.editingContextSearchService = Objects.requireNonNull(editingContextSearchService);
        this.representationDescriptionSearchService = Objects.requireNonNull(representationDescriptionSearchService);
    }

    /**
     * Import an ECOA project from a ZIP file. The import is processed via IEditingContextEventProcessorRegistry so that
     * EdtImportEcoaStepsEventHandler can emit SEMANTIC_CHANGE and trigger DB persistence. After successful import,
     * Composite Diagram, Logical System Diagram, and Component Implementation Diagrams are automatically created.
     */
    @PostMapping(value = "/import/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importEcoaProject(@PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {

        LOGGER.info("Import request received for project: {}", projectId);

        // Validate project exists
        var optionalProject = this.projectSearchService.findById(String.valueOf(projectId));
        if (optionalProject.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ImportResult(false, "Project not found: " + projectId, 0, 0));
        }

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ImportResult(false, "No file uploaded", 0, 0));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(new ImportResult(false, "File must be a ZIP archive", 0, 0));
        }

        try {
            byte[] zipBytes = file.getBytes();
            // Convert projectId to editing context ID (semantic data ID) used by Sirius Web
            String editingContextId = this.projectEditingContextService.getEditingContextId(projectId.toString()).orElse(null);

            if (editingContextId == null) {
                LOGGER.warn("Could not resolve editing context ID for project: {}", projectId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ImportResult(false, "Editing context not found for project.", 0, 0));
            }

            var inputId = UUID.randomUUID();
            var importInput = new EdtImportEcoaStepsInput(inputId, zipBytes, optionalProject.get().getName());
            AtomicBoolean success = new AtomicBoolean(false);

            // Step 1: Dispatch import to trigger Steps persistence
            this.eventProcessorRegistry.getOrCreateEditingContextEventProcessor(editingContextId).ifPresentOrElse(processor -> {
                var payloadOpt = processor.handle(importInput).blockOptional();
                payloadOpt.ifPresent(payload -> {
                    if (!(payload instanceof org.eclipse.sirius.components.core.api.ErrorPayload)) {
                        success.set(true);
                    }
                });
            }, () -> LOGGER.warn("No editing context event processor found for: {}", editingContextId));

            if (!success.get()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ImportResult(false, "Import failed - check logs for details", 0, 0));
            }

            // Step 2: After import succeeds, create diagrams
            List<String> createdDiagrams = this.createDiagramsAfterImport(editingContextId);
            String message = "Import successful (persisted to database). Created diagrams: " + createdDiagrams;
            LOGGER.info(message);
            return ResponseEntity.ok(new ImportResult(true, message, 0, 0));

        } catch (IOException e) {
            LOGGER.error("Error reading uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ImportResult(false, "Error reading file: " + e.getMessage(), 0, 0));
        }
    }

    /**
     * After Steps import, find the Composite, LogicalSystem, and ComponentImplementation objects and create their diagrams.
     */
    private List<String> createDiagramsAfterImport(String editingContextId) {
        List<String> created = new ArrayList<>();

        var optEditingContext = this.editingContextSearchService.findById(editingContextId);
        if (optEditingContext.isEmpty()) {
            LOGGER.warn("EditingContext not found when trying to create diagrams: {}", editingContextId);
            return created;
        }

        var editingContext = optEditingContext.get();

        // Find description IDs by name
        var descriptions = this.representationDescriptionSearchService.findAll(editingContext);
        String compositeDescId = findDescriptionId(descriptions, EdtCompositeDiagramDescriptionProvider.NAME);
        String logicalDescId = findDescriptionId(descriptions, EdtLogicalSystemDiagramDescriptionProvider.NAME);
        String componentImplementationDescId = findDescriptionId(descriptions, EdtComponentImplementationDiagramDescriptionProvider.NAME);

        if (!(editingContext instanceof IEMFEditingContext emfCtx)) {
            LOGGER.warn("EditingContext is not IEMFEditingContext, cannot create diagrams");
            return created;
        }

        var resourceSet = emfCtx.getDomain().getResourceSet();

        // Find Steps in the ResourceSet
        Steps steps = resourceSet.getResources().stream().flatMap(r -> r.getContents().stream()).filter(Steps.class::isInstance).map(Steps.class::cast).findFirst().orElse(null);

        if (steps == null) {
            LOGGER.warn("No Steps found in editing context after import, cannot create diagrams");
            return created;
        }

        // Create Composite Diagram for InitialAssembly (Step3.composite)
        if (compositeDescId != null) {
            Step3 step3 = steps.getStep().stream().filter(Step3.class::isInstance).map(Step3.class::cast).findFirst().orElse(null);
            if (step3 != null && step3.getInitialAssembly() != null) {
                Composite composite = step3.getInitialAssembly();
                String objectId = getEMFObjectId(composite);
                if (objectId != null) {
                    boolean ok = createDiagram(editingContextId, compositeDescId, objectId, (composite.getName() != null ? composite.getName() : "Assembly") + " - Composite Diagram");
                    if (ok)
                        created.add("Composite Diagram");
                }
            }
        } else {
            LOGGER.warn("Could not find description ID for: {}", EdtCompositeDiagramDescriptionProvider.NAME);
        }

        // Create Logical System Diagram for Step5's LogicalSystem
        if (logicalDescId != null) {
            LogicalSystem logicalSystem = findLogicalSystem(steps);
            if (logicalSystem != null) {
                String objectId = getEMFObjectId(logicalSystem);
                if (objectId != null) {
                    boolean ok = createDiagram(editingContextId, logicalDescId, objectId, (logicalSystem.getId() != null ? logicalSystem.getId() : "LogicalSystem") + " - Logical System Diagram");
                    if (ok)
                        created.add("Logical System Diagram");
                }
            } else {
                LOGGER.debug("No LogicalSystem found in Steps, skipping Logical System Diagram");
            }
        } else {
            LOGGER.warn("Could not find description ID for: {}", EdtLogicalSystemDiagramDescriptionProvider.NAME);
        }

        if (componentImplementationDescId != null) {
            Step4 step4 = steps.getStep().stream().filter(Step4.class::isInstance).map(Step4.class::cast).findFirst().orElse(null);
            if (step4 != null) {
                int createdCount = 0;
                for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
                    String objectId = getEMFObjectId(componentImplementation);
                    if (objectId == null) {
                        LOGGER.warn("Could not resolve objectId for component implementation '{}'", componentImplementation.getName());
                        continue;
                    }

                    String label = (componentImplementation.getName() != null ? componentImplementation.getName() : "ComponentImplementation")
                            + " - Component Implementation Diagram";
                    if (createDiagram(editingContextId, componentImplementationDescId, objectId, label)) {
                        createdCount++;
                    }
                }
                if (createdCount > 0) {
                    created.add("Component Implementation Diagram x" + createdCount);
                }
            } else {
                LOGGER.debug("No Step4 found in Steps, skipping Component Implementation Diagrams");
            }
        } else {
            LOGGER.warn("Could not find description ID for: {}", EdtComponentImplementationDiagramDescriptionProvider.NAME);
        }

        return created;
    }

    /**
     * Find a LogicalSystem object within Steps via Step5.getLogicalSystem().
     */
    private LogicalSystem findLogicalSystem(Steps steps) {
        return steps.getStep().stream().filter(Step5.class::isInstance).map(Step5.class::cast).map(Step5::getLogicalSystem).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * Get the Sirius Web object ID for an EMF EObject. Format: documentId#fragment (matches the JSON resource URI
     * format).
     */
    private String getEMFObjectId(EObject eObject) {
        var resource = eObject.eResource();
        if (resource == null) {
            return null;
        }
        var uri = resource.getURI();
        if (uri == null) {
            return null;
        }
        // The resource URI path is the documentId (UUID); fragment is the object's position
        String documentId = uri.path();
        if (documentId.startsWith("/")) {
            documentId = documentId.substring(1);
        }
        String fragment = resource.getURIFragment(eObject);
        return documentId + "#" + fragment;
    }

    /**
     * Find a representation description ID by its name.
     */
    private String findDescriptionId(java.util.Map<String, IRepresentationDescription> descriptions, String name) {
        return descriptions.entrySet().stream().filter(e -> name.equals(e.getValue().getLabel())).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }

    /**
     * Create a representation (diagram) via the event processor.
     */
    private boolean createDiagram(String editingContextId, String descriptionId, String objectId, String label) {
        var createInput = new CreateRepresentationInput(UUID.randomUUID(), editingContextId, descriptionId, objectId, label);
        var result = new AtomicBoolean(false);

        this.eventProcessorRegistry.getOrCreateEditingContextEventProcessor(editingContextId).ifPresentOrElse(processor -> {
            var payloadOpt = processor.handle(createInput).blockOptional();
            if (payloadOpt.isPresent() && payloadOpt.get() instanceof CreateRepresentationSuccessPayload) {
                result.set(true);
                LOGGER.info("Created diagram '{}' for object {}", label, objectId);
            } else {
                LOGGER.warn("Failed to create diagram '{}': payload={}", label, payloadOpt.map(p -> p.getClass().getSimpleName()).orElse("empty"));
            }
        }, () -> LOGGER.warn("No event processor for creating diagram in context: {}", editingContextId));

        return result.get();
    }

    /**
     * Result of an import operation.
     */
    public record ImportResult(boolean success, String message, int typesImported, int servicesImported) {
    }
}
