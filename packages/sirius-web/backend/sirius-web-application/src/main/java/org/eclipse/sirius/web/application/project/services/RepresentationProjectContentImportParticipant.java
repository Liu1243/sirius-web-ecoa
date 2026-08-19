/*******************************************************************************
 * Copyright (c) 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.application.project.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.sirius.components.events.ICause;
import org.eclipse.sirius.web.application.UUIDParser;
import org.eclipse.sirius.web.application.project.services.api.IProjectContentImportParticipant;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.RepresentationCompositeIdProvider;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationContentCreationService;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationMetadataCreationService;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.SemanticData;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.events.SemanticDataUpdatedEvent;
import org.eclipse.sirius.web.domain.events.IDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;

/**
 * {@link IProjectContentImportParticipant} in charge of importing representations in a project.
 *
 * <p>Uses the same direct-write strategy as {@code EdtImportSiriusWebZipEventHandler}: the
 * representation JSON that is already stored in the ZIP is serialised, re-mapped to the new
 * semantic-object UUIDs, and written directly to the database.  This avoids:
 * <ul>
 *   <li>Loading the editing context from the database (previously done once per representation).</li>
 *   <li>Re-rendering every diagram from scratch via {@code CreateRepresentationInput} and
 *       {@code DiagramImporterUpdateService} (previously 3 full renders per representation).</li>
 * </ul>
 *
 * @author Arthur Daussy
 */
@Service
public class RepresentationProjectContentImportParticipant implements IProjectContentImportParticipant {

    private static final String ZIP_FOLDER_SEPARATOR = "/";

    private static final String REPRESENTATIONS_FOLDER = "representations";

    /**
     * Matches any UUID in the representation JSON so we can do a single-pass replacement
     * of all semantic-element ID mappings.  A single pass is O(json_size) regardless of
     * the number of mappings, whereas N individual String.replace() calls are O(N * json_size)
     * and cause severe GC pressure when importing large ECOA projects.
     */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Logger logger = LoggerFactory.getLogger(RepresentationProjectContentImportParticipant.class);

    private final ObjectMapper objectMapper;

    private final IRepresentationMetadataCreationService representationMetadataCreationService;

    private final IRepresentationContentCreationService representationContentCreationService;

    public RepresentationProjectContentImportParticipant(
            ObjectMapper objectMapper,
            IRepresentationMetadataCreationService representationMetadataCreationService,
            IRepresentationContentCreationService representationContentCreationService) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.representationMetadataCreationService = Objects.requireNonNull(representationMetadataCreationService);
        this.representationContentCreationService = Objects.requireNonNull(representationContentCreationService);
    }

    @Override
    public boolean canHandle(IDomainEvent event) {
        return event instanceof SemanticDataUpdatedEvent
                && event.causedBy() instanceof CopySemanticDataCause copySemanticCause
                && copySemanticCause.causedBy() instanceof InitializeProjectInput;
    }

    @Override
    public void handle(IDomainEvent event, ProjectZipContent projectContent) {
        if (!(event instanceof SemanticDataUpdatedEvent semanticDataUpdatedEvent)) {
            return;
        }
        if (!(event.causedBy() instanceof CopySemanticDataCause copySemanticCause)) {
            return;
        }
        if (!(copySemanticCause.causedBy() instanceof InitializeProjectInput initializeProjectInput)) {
            return;
        }

        var optSemanticDataId = new UUIDParser().parse(semanticDataUpdatedEvent.semanticData().getId().toString());
        optSemanticDataId.ifPresent(semanticDataId ->
                this.importRepresentations(
                        initializeProjectInput,
                        semanticDataId,
                        projectContent,
                        copySemanticCause.documentIds(),
                        copySemanticCause.semanticDataIds()));
    }

    // -----------------------------------------------------------------------
    // Fast path: write representation content directly to DB
    // -----------------------------------------------------------------------

    /**
     * Imports all representations found in the ZIP into the newly created project.
     *
     * <p>No editing context is loaded from the database and no diagram rendering is performed.
     * The representation JSON from the ZIP is re-mapped and written directly via the
     * {@link IRepresentationMetadataCreationService} and {@link IRepresentationContentCreationService}.
     */
    private void importRepresentations(
            ICause cause,
            UUID semanticDataId,
            ProjectZipContent projectContent,
            Map<String, String> documentIdMapping,
            Map<String, String> semanticIdMapping) {

        var semanticDataRef = AggregateReference.<SemanticData, UUID>to(semanticDataId);

        var allRepresentations = this.getRepresentationImportData(projectContent);
        this.logger.info("[REP-IMPORT] Starting import of {} representations, semanticIdMappingSize={}",
                allRepresentations.size(), semanticIdMapping.size());
        long importStart = System.currentTimeMillis();

        for (RepresentationImportData representationImportData : allRepresentations) {
            try {
                long repStart = System.currentTimeMillis();
                this.importSingleRepresentation(
                        cause, semanticDataId, semanticDataRef,
                        representationImportData, projectContent,
                        documentIdMapping, semanticIdMapping);
                this.logger.info("[REP-IMPORT] Imported '{}' in {}ms",
                        representationImportData.label(), System.currentTimeMillis() - repStart);
            } catch (Exception e) {
                this.logger.warn("Failed to import representation '{}' ({}): {}",
                        representationImportData.label(), representationImportData.kind(), e.getMessage(), e);
            }
        }
        this.logger.info("[REP-IMPORT] All {} representations imported in {}ms total",
                allRepresentations.size(), System.currentTimeMillis() - importStart);
    }

    private void importSingleRepresentation(
            ICause cause,
            UUID semanticDataId,
            AggregateReference<SemanticData, UUID> semanticDataRef,
            RepresentationImportData representationImportData,
            ProjectZipContent projectContent,
            Map<String, String> documentIdMapping,
            Map<String, String> semanticIdMapping) throws JsonProcessingException {

        // --- Resolve target object and description IDs from the ZIP manifest ------
        Map<?, ?> manifest = this.getRepresentationManifest(representationImportData, projectContent);

        String targetObjectURI = Optional.ofNullable(manifest.get(ProjectZipContent.TARGET_OBJECT_URI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(representationImportData.targetObjectId());

        String descriptionId = Optional.ofNullable(manifest.get(ProjectZipContent.DESCRIPTION_URI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(representationImportData.descriptionId());

        // Remap the target-object URI to the new document / semantic-element IDs.
        String targetObjectId = this.getNewObjectId(targetObjectURI, documentIdMapping, semanticIdMapping);

        // --- Assign fresh IDs for this representation in the target project -------
        UUID newRepMetadataId = UUID.randomUUID();
        String compositeId = new RepresentationCompositeIdProvider().getId(semanticDataId, newRepMetadataId);

        // --- Serialise and remap the representation content ----------------------
        // The IRepresentation object already contains the full diagram/form JSON.
        // We need to replace:
        //   1. The representation's own ID (old → newRepMetadataId)
        //   2. All semantic-element UUIDs (via semanticIdMapping)
        //
        // We do this in a SINGLE PASS over the JSON string using a UUID regex, rather than
        // calling String.replace() once per mapping entry.  The naive N-pass approach is
        // O(N * json_size) and causes severe GC pressure for large ECOA projects that have
        // thousands of model elements, blocking the HTTP thread for minutes.
        String oldRepId = representationImportData.id() != null ? representationImportData.id().toString() : null;
        String contentJson = this.objectMapper.writeValueAsString(representationImportData.representation());

        Map<String, String> allMappings = new HashMap<>(semanticIdMapping);
        if (oldRepId != null && !oldRepId.isEmpty()) {
            allMappings.put(oldRepId, newRepMetadataId.toString());
        }
        if (!allMappings.isEmpty()) {
            contentJson = this.replaceUuids(contentJson, allMappings);
        }

        // --- Persist metadata then content ---------------------------------------
        RepresentationMetadata repMetadata = RepresentationMetadata
                .newRepresentationMetadata(compositeId)
                .representationMetadataId(newRepMetadataId)
                .semanticData(semanticDataRef)
                .kind(representationImportData.kind())
                .label(representationImportData.label())
                .descriptionId(descriptionId)
                .targetObjectId(targetObjectId)
                .iconURLs(List.of())
                .documentation("")
                .build(cause);

        this.representationMetadataCreationService.create(repMetadata);
        this.representationContentCreationService.create(
                cause, semanticDataRef, AggregateReference.to(newRepMetadataId),
                contentJson, "", "");

        this.logger.info("Imported representation '{}' ({}) → metadata id {}",
                representationImportData.label(), representationImportData.kind(), newRepMetadataId);
    }

    // -----------------------------------------------------------------------
    // ZIP parsing helpers (unchanged from original)
    // -----------------------------------------------------------------------

    private List<RepresentationImportData> getRepresentationImportData(ProjectZipContent projectZipContent) {
        String representationsFolderInZip = projectZipContent.projectName() + ZIP_FOLDER_SEPARATOR + REPRESENTATIONS_FOLDER + ZIP_FOLDER_SEPARATOR;
        List<ByteArrayOutputStream> representationDescriptorsContent = projectZipContent.files().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(representationsFolderInZip))
                .map(Map.Entry::getValue)
                .toList();

        return this.getRepresentationImportData(representationDescriptorsContent);
    }

    private List<RepresentationImportData> getRepresentationImportData(List<ByteArrayOutputStream> streams) {
        List<RepresentationImportData> representations = new ArrayList<>();
        for (ByteArrayOutputStream outputStream : streams) {
            try {
                byte[] bytes = outputStream.toByteArray();
                RepresentationSerializedImportData serialized = this.objectMapper.readValue(bytes, RepresentationSerializedImportData.class);
                representations.add(new RepresentationImportData(
                        serialized.id(),
                        serialized.projectId(),
                        serialized.descriptionId(),
                        serialized.targetObjectId(),
                        serialized.label(),
                        serialized.kind(),
                        serialized.representation()));
            } catch (IOException exception) {
                this.logger.warn("Unable to parse representation from ZIP: {}", exception.getMessage(), exception);
            }
        }
        return representations;
    }

    private Map<?, ?> getRepresentationManifest(RepresentationImportData representationImportData, ProjectZipContent projectZipContent) {
        Object representationsFromManifest = projectZipContent.manifest().get(ProjectZipContent.REPRESENTATIONS);
        UUID representationId = representationImportData.id();
        if (representationsFromManifest instanceof Map && representationId != null) {
            Object representationFromManifest = ((Map<?, ?>) representationsFromManifest).get(representationId.toString());
            if (representationFromManifest instanceof Map) {
                return (Map<?, ?>) representationFromManifest;
            }
        }
        return new HashMap<>();
    }

    private String getNewObjectId(String targetObjectURI, Map<String, String> documentIdMapping, Map<String, String> semanticIdMapping) {
        String objectId;

        String oldDocumentId = getDocumentId(targetObjectURI);
        String newDocumentId = documentIdMapping.get(oldDocumentId);
        if (newDocumentId != null) {
            objectId = targetObjectURI.replace(oldDocumentId, newDocumentId);
        } else {
            objectId = targetObjectURI;
        }

        String oldSemanticElementId = URI.create(targetObjectURI).getFragment();
        String newSemanticElementId = semanticIdMapping.get(oldSemanticElementId);
        if (newSemanticElementId != null) {
            objectId = objectId.replace(oldSemanticElementId, newSemanticElementId);
        }
        return objectId;
    }

    private String getDocumentId(String targetObjectURI) {
        return URI.create(targetObjectURI).getPath().substring(1);
    }

    /**
     * Replaces UUIDs in {@code json} in a single pass over the string.
     * UUIDs not present in {@code idMapping} are left unchanged.
     *
     * <p>Using a single regex scan is O(json_size) regardless of mapping size, whereas
     * calling {@code String.replace()} once per entry is O(mapping_size * json_size).
     */
    private String replaceUuids(String json, Map<String, String> idMapping) {
        Matcher matcher = UUID_PATTERN.matcher(json);
        StringBuilder sb = new StringBuilder(json.length());
        while (matcher.find()) {
            String found = matcher.group();
            String replacement = idMapping.getOrDefault(found, found);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
