package org.eclipse.sirius.web.application.componentcode.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.InitializeProjectInput;
import org.eclipse.sirius.web.application.project.services.ProjectZipContent;
import org.eclipse.sirius.web.application.project.services.api.IProjectContentImportParticipant;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.events.ProjectSemanticDataCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.events.SemanticDataCreatedEvent;
import org.eclipse.sirius.web.domain.events.IDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Imports component code versions from the component-code/ directory of a project ZIP.
 * Versions are saved with PENDING status until the user confirms them in the UI.
 */
@Service
public class ComponentCodeProjectImportParticipant implements IProjectContentImportParticipant {

    private static final String COMPONENT_CODE_DIR = "component-code";
    private static final String MANIFEST_FILE = "component-code/component-code-manifest.json";

    private final IComponentCodeVersionService versionService;
    private final IComponentCodeTagService tagService;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(ComponentCodeProjectImportParticipant.class);

    public ComponentCodeProjectImportParticipant(IComponentCodeVersionService versionService,
                                                  IComponentCodeTagService tagService,
                                                  ObjectMapper objectMapper) {
        this.versionService = Objects.requireNonNull(versionService);
        this.tagService = Objects.requireNonNull(tagService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public boolean canHandle(IDomainEvent event) {
        return event instanceof ProjectSemanticDataCreatedEvent projectSemanticDataCreatedEvent
                && projectSemanticDataCreatedEvent.causedBy() instanceof SemanticDataCreatedEvent semanticDataCreatedEvent
                && semanticDataCreatedEvent.causedBy() instanceof ProjectCreatedEvent projectCreatedEvent
                && projectCreatedEvent.causedBy() instanceof InitializeProjectInput;
    }

    @Override
    public void handle(IDomainEvent event, ProjectZipContent projectContent) {
        if (!(event instanceof ProjectSemanticDataCreatedEvent projectSemanticDataCreatedEvent)) {
            return;
        }
        if (!(projectSemanticDataCreatedEvent.causedBy() instanceof SemanticDataCreatedEvent semanticDataCreatedEvent)) {
            return;
        }
        if (!(semanticDataCreatedEvent.causedBy() instanceof ProjectCreatedEvent projectCreatedEvent)) {
            return;
        }

        String projectIdStr = projectCreatedEvent.project().getId();
        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdStr);
        } catch (IllegalArgumentException e) {
            this.logger.warn("Cannot parse project id during component code import: {}", projectIdStr);
            return;
        }

        // Check if ZIP contains component-code-manifest.json
        String manifestKey = projectContent.projectName() + "/" + MANIFEST_FILE;
        ByteArrayOutputStream manifestStream = projectContent.files().get(manifestKey);
        if (manifestStream == null) {
            // No component code in this ZIP — silently skip
            return;
        }

        try {
            Map<String, Object> manifest = this.objectMapper.readValue(
                manifestStream.toByteArray(),
                new TypeReference<Map<String, Object>>() {}
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> versions = (List<Map<String, Object>>) manifest.get("versions");
            if (versions == null || versions.isEmpty()) {
                return;
            }

            this.logger.info("[COMP-IMPORT] Starting import of {} component code versions for project {}",
                    versions.size(), projectId);
            long compStart = System.currentTimeMillis();
            for (Map<String, Object> versionData : versions) {
                importVersion(projectId, projectContent, versionData);
            }
            this.logger.info("[COMP-IMPORT] All {} versions imported in {}ms",
                    versions.size(), System.currentTimeMillis() - compStart);

        } catch (IOException e) {
            this.logger.warn("Failed to parse component-code-manifest.json during import: {}", e.getMessage());
        }
    }

    private void importVersion(UUID projectId, ProjectZipContent projectContent, Map<String, Object> versionData) {
        String componentId = (String) versionData.get("componentId");
        String componentName = (String) versionData.get("componentName");
        String versionName = (String) versionData.get("versionName");
        String commitMessage = (String) versionData.get("commitMessage");
        String author = (String) versionData.get("author");
        String modelVersionId = (String) versionData.get("modelVersionId");

        if (componentId == null || versionName == null) {
            this.logger.warn("Skipping component code version with missing componentId or versionName");
            return;
        }

        // Read files.json from ZIP
        String filesKey = projectContent.projectName() + "/" + COMPONENT_CODE_DIR + "/"
                + componentId + "/" + versionName + "/files.json";
        ByteArrayOutputStream filesStream = projectContent.files().get(filesKey);
        if (filesStream == null) {
            this.logger.warn("Missing files.json for component {} version {}, skipping", componentId, versionName);
            return;
        }

        String codeContent = new String(filesStream.toByteArray(), StandardCharsets.UTF_8);

        // Create PENDING version (resolveUniqueVersionName happens inside service)
        var createdVersion = this.versionService.createPendingComponentCodeVersion(
            projectId, componentId,
            componentName != null ? componentName : componentId,
            versionName, codeContent, commitMessage,
            author != null ? author : "imported",
            modelVersionId
        );

        // Import tags
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) versionData.get("tags");
        if (tags != null) {
            for (Map<String, Object> tagData : tags) {
                String tagName = (String) tagData.get("name");
                String tagColor = (String) tagData.get("color");
                if (tagName == null) {
                    continue;
                }
                // Find or create tag
                UUID tagId;
                try {
                    var existingTags = this.tagService.getComponentCodeTags(projectId);
                    var existingTag = existingTags.stream()
                        .filter(t -> tagName.equals(t.name()))
                        .findFirst();
                    if (existingTag.isPresent()) {
                        tagId = existingTag.get().id();
                    } else {
                        var newTag = this.tagService.createComponentCodeTag(projectId, tagName,
                            tagColor != null ? tagColor : "#808080");
                        tagId = newTag.id();
                    }
                    this.tagService.addTagToVersion(createdVersion.id(), tagId);
                } catch (Exception e) {
                    this.logger.warn("Failed to import tag {} for version {}: {}", tagName, createdVersion.id(), e.getMessage());
                }
            }
        }
    }
}
