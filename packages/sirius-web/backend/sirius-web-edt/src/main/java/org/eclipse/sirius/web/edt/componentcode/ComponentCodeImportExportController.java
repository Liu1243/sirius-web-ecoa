package org.eclipse.sirius.web.edt.componentcode;

import org.eclipse.sirius.web.application.capability.SiriusWebCapabilities;
import org.eclipse.sirius.web.application.capability.services.api.ICapabilityEvaluator;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST endpoints for component code pending import management.
 *
 * <ul>
 *   <li>GET /api/edt/component-code/pending/{projectId} — list PENDING versions</li>
 *   <li>POST /api/edt/component-code/confirm/{projectId} — confirm or reject PENDING versions</li>
 * </ul>
 */
@RestController
public class ComponentCodeImportExportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentCodeImportExportController.class);

    private final IComponentCodeVersionService versionService;
    private final IProjectSearchService projectSearchService;
    private final IProjectEditingContextService projectEditingContextService;
    private final ICapabilityEvaluator capabilityEvaluator;

    public ComponentCodeImportExportController(IComponentCodeVersionService versionService,
                                                IProjectSearchService projectSearchService,
                                                IProjectEditingContextService projectEditingContextService,
                                                ICapabilityEvaluator capabilityEvaluator) {
        this.versionService = Objects.requireNonNull(versionService);
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
        this.capabilityEvaluator = Objects.requireNonNull(capabilityEvaluator);
    }

    /**
     * Resolves an ID to a project UUID, accepting either:
     * <ul>
     *   <li>a project entity UUID (e.g., from the project browser URL)</li>
     *   <li>an editing-context / semantic-data UUID (which is what {@code ComponentHistoryView}
     *       passes as {@code editingContextId})</li>
     * </ul>
     */
    private UUID resolveProjectId(String id) {
        // Direct project lookup
        if (this.projectSearchService.findById(id).isPresent()) {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("ID '{}' found in project table but is not a valid UUID", id);
                return null;
            }
        }
        // Fallback: treat id as editingContextId and resolve to projectId
        return this.projectEditingContextService.getProjectId(id)
                .map(projectId -> {
                    try {
                        return UUID.fromString(projectId);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Resolved projectId '{}' from editingContextId '{}' is not a valid UUID", projectId, id);
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Returns all PENDING component code versions for a project.
     *
     * <p>The path variable accepts either a project UUID or an editing-context UUID
     * (which is what {@code ComponentHistoryView} passes as {@code editingContextId}).
     */
    @GetMapping("/api/edt/component-code/pending/{projectId}")
    public ResponseEntity<Map<String, Object>> getPendingVersions(@PathVariable String projectId) {
        UUID projectUUID = this.resolveProjectId(projectId);
        if (projectUUID == null) {
            return ResponseEntity.notFound().build();
        }

        List<ComponentCodeVersionDTO> pendingVersions = this.versionService.getPendingVersions(projectUUID);
        return ResponseEntity.ok(Map.of("versions", pendingVersions));
    }

    /**
     * Confirms or rejects PENDING component code versions.
     *
     * <p>The path variable accepts either a project UUID or an editing-context UUID.
     */
    @PostMapping("/api/edt/component-code/confirm/{projectId}")
    public ResponseEntity<Map<String, Object>> confirmPendingVersions(
            @PathVariable String projectId,
            @RequestBody ConfirmRequest body) {

        UUID projectUUID = this.resolveProjectId(projectId);
        if (projectUUID == null) {
            return ResponseEntity.notFound().build();
        }

        // Capability check uses the original id parameter (may be editingContextId or projectId).
        // Resolve to actual projectId string for the capability evaluator.
        String resolvedProjectId = projectUUID.toString();
        boolean hasEditCapability = this.capabilityEvaluator.hasCapability(SiriusWebCapabilities.PROJECT, resolvedProjectId, SiriusWebCapabilities.Project.EDIT);
        if (!hasEditCapability) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UUID> acceptedIds = body.acceptedVersionIds() != null ? body.acceptedVersionIds() : List.of();
        List<UUID> rejectedIds = body.rejectedVersionIds() != null ? body.rejectedVersionIds() : List.of();

        this.versionService.confirmPendingVersions(projectUUID, acceptedIds, rejectedIds);

        return ResponseEntity.ok(Map.of("accepted", acceptedIds.size(), "rejected", rejectedIds.size()));
    }

    /** Request body for confirm endpoint. */
    public record ConfirmRequest(List<UUID> acceptedVersionIds, List<UUID> rejectedVersionIds) {}
}
