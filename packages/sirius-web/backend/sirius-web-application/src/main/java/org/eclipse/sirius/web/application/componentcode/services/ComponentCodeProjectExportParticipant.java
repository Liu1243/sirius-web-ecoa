package org.eclipse.sirius.web.application.componentcode.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.api.IProjectExportParticipant;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IComponentCodeTagRepository;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IVersionTagRepository;
import org.eclipse.sirius.web.domain.boundedcontexts.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports selected component code versions into the project ZIP under component-code/.
 */
@Service
public class ComponentCodeProjectExportParticipant implements IProjectExportParticipant {

    private static final String COMPONENT_CODE_DIR = "component-code";
    private static final String MANIFEST_FILE = "component-code-manifest.json";

    private final IComponentCodeVersionService versionService;
    private final IComponentCodeTagRepository tagRepository;
    private final IVersionTagRepository versionTagRepository;
    private final ObjectMapper objectMapper;
    private final ComponentCodeExportContext componentCodeExportContext;
    private final Logger logger = LoggerFactory.getLogger(ComponentCodeProjectExportParticipant.class);

    public ComponentCodeProjectExportParticipant(IComponentCodeVersionService versionService,
                                                  IComponentCodeTagRepository tagRepository,
                                                  IVersionTagRepository versionTagRepository,
                                                  ObjectMapper objectMapper,
                                                  ComponentCodeExportContext componentCodeExportContext) {
        this.versionService = Objects.requireNonNull(versionService);
        this.tagRepository = Objects.requireNonNull(tagRepository);
        this.versionTagRepository = Objects.requireNonNull(versionTagRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.componentCodeExportContext = Objects.requireNonNull(componentCodeExportContext);
    }

    @Override
    public Map<String, Object> exportData(Project project, String editingContextId, ZipOutputStream outputStream) {
        List<UUID> selectedVersionIds = this.componentCodeExportContext.get();
        if (selectedVersionIds.isEmpty()) {
            return Map.of();
        }

        UUID projectId;
        try {
            projectId = UUID.fromString(project.getId());
        } catch (IllegalArgumentException e) {
            this.logger.warn("Cannot parse project id: {}", project.getId());
            return Map.of();
        }

        List<VersionExportData> versionsToExport = new ArrayList<>();

        for (UUID versionId : selectedVersionIds) {
            Optional<ComponentCodeVersionDTO> optVersion = this.versionService.getComponentCodeVersion(versionId);
            if (optVersion.isEmpty()) {
                continue;
            }
            ComponentCodeVersionDTO version = optVersion.get();

            // Load tags for this version
            List<ComponentCodeTagDTO> tags = loadTagsForVersion(versionId, projectId);

            // Write files.json entry
            String zipPath = project.getName() + "/" + COMPONENT_CODE_DIR + "/"
                    + version.componentId() + "/" + version.versionName() + "/files.json";
            try {
                outputStream.putNextEntry(new ZipEntry(zipPath));
                outputStream.write(version.codeContent().getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            } catch (IOException e) {
                this.logger.warn("Failed to write component code zip entry {}: {}", zipPath, e.getMessage());
                continue;
            }

            versionsToExport.add(new VersionExportData(
                version.componentId(),
                version.componentName(),
                version.versionName(),
                version.commitMessage(),
                version.author(),
                version.createdAt(),
                version.modelVersionId(),
                tags
            ));
        }

        // Write component-code-manifest.json
        if (!versionsToExport.isEmpty()) {
            try {
                Map<String, Object> manifest = Map.of("versions", versionsToExport);
                byte[] manifestBytes = this.objectMapper.writeValueAsBytes(manifest);
                String manifestPath = project.getName() + "/" + COMPONENT_CODE_DIR + "/" + MANIFEST_FILE;
                outputStream.putNextEntry(new ZipEntry(manifestPath));
                outputStream.write(manifestBytes);
                outputStream.closeEntry();
            } catch (IOException e) {
                this.logger.warn("Failed to write component-code-manifest.json: {}", e.getMessage());
            }
        }

        return Map.of();
    }

    private List<ComponentCodeTagDTO> loadTagsForVersion(UUID versionId, UUID projectId) {
        return versionTagRepository.findByVersionId(versionId).stream()
            .map(vt -> tagRepository.findById(vt.getTagId()).orElse(null))
            .filter(Objects::nonNull)
            .map(tag -> new ComponentCodeTagDTO(tag.getId(), tag.getName(), tag.getColor()))
            .toList();
    }

    /** Inner record for manifest serialization */
    public record VersionExportData(
        String componentId,
        String componentName,
        String versionName,
        String commitMessage,
        String author,
        String createdAt,
        String modelVersionId,
        List<ComponentCodeTagDTO> tags
    ) {}
}
