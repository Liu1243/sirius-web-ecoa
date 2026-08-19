package org.eclipse.sirius.web.application.componentcode.services;

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeHistoryDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentHistoryEntryDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.ComponentCodeVersion;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IComponentCodeVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for managing component code versions.
 *
 * @author lanceliu
 */
@Service
@Transactional
public class ComponentCodeVersionService implements IComponentCodeVersionService {

    private final IComponentCodeVersionRepository versionRepository;

    public ComponentCodeVersionService(IComponentCodeVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ComponentCodeHistoryDTO getComponentCodeHistory(UUID projectId) {
        List<ComponentCodeVersion> versions = versionRepository
            .findOfficialByProjectId(projectId);

        Map<String, ComponentHistoryEntryDTO> componentMap = new LinkedHashMap<>();

        for (ComponentCodeVersion version : versions) {
            String compId = version.getComponentId();
            componentMap.computeIfAbsent(compId, k -> new ComponentHistoryEntryDTO(
                version.getComponentId(),
                version.getComponentName(),
                new ArrayList<>()
            ));
            componentMap.get(compId).versions().add(toDTO(version));
        }

        return new ComponentCodeHistoryDTO(new ArrayList<>(componentMap.values()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComponentCodeVersionDTO> getComponentCodeVersion(UUID versionId) {
        return versionRepository.findById(versionId).map(this::toDTO);
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ComponentCodeVersionDTO createComponentCodeVersion(UUID projectId, String componentId, String componentName,
                                                               String versionName, String codeContent, String commitMessage,
                                                               String author, String modelVersionId) {
        if (versionRepository.existsByProjectIdAndComponentIdAndVersionName(projectId, componentId, versionName)) {
            throw new IllegalArgumentException("Version name already exists for this component");
        }

        ComponentCodeVersion entity = ComponentCodeVersion.newBuilder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .componentId(componentId)
            .componentName(componentName)
            .versionName(versionName)
            .codeContent(codeContent)
            .commitMessage(commitMessage)
            .author(author)
            .createdAt(Instant.now())
            .modelVersionId(modelVersionId)
            .build();

        ComponentCodeVersion saved = versionRepository.save(entity);
        return toDTO(saved);
    }

    @Override
    public void deleteComponentCodeVersion(UUID versionId) {
        versionRepository.deleteById(versionId);
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ComponentCodeVersionDTO createPendingComponentCodeVersion(UUID projectId, String componentId, String componentName,
                                                                      String versionName, String codeContent, String commitMessage,
                                                                      String author, String modelVersionId) {
        String resolvedVersionName = resolveUniqueVersionName(projectId, componentId, versionName);

        ComponentCodeVersion entity = ComponentCodeVersion.newBuilder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .componentId(componentId)
            .componentName(componentName)
            .versionName(resolvedVersionName)
            .codeContent(codeContent)
            .commitMessage(commitMessage)
            .author(author)
            .createdAt(Instant.now())
            .modelVersionId(modelVersionId)
            .importStatus("PENDING")
            .build();

        ComponentCodeVersion saved = versionRepository.save(entity);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentCodeVersionDTO> getPendingVersions(UUID projectId) {
        return versionRepository.findPendingByProjectId(projectId, "PENDING").stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Override
    public void confirmPendingVersions(UUID projectId, List<UUID> acceptedVersionIds, List<UUID> rejectedVersionIds) {
        for (UUID versionId : acceptedVersionIds) {
            versionRepository.findByIdAndProjectId(versionId, projectId).ifPresent(version -> {
                version.setImportStatus(null);
                versionRepository.save(version);
            });
        }
        for (UUID versionId : rejectedVersionIds) {
            versionRepository.findByIdAndProjectId(versionId, projectId).ifPresent(version -> {
                version.setImportStatus("REJECTED");
                versionRepository.save(version);
            });
        }
    }

    /**
     * Resolves a unique version name by appending "-imported" suffix if the name already exists.
     * Keeps incrementing counter (-imported-2, -imported-3, ...) until unique.
     */
    private String resolveUniqueVersionName(UUID projectId, String componentId, String requestedVersionName) {
        if (!versionRepository.existsByProjectIdAndComponentIdAndVersionName(projectId, componentId, requestedVersionName)) {
            return requestedVersionName;
        }
        String candidate = requestedVersionName + "-imported";
        if (!versionRepository.existsByProjectIdAndComponentIdAndVersionName(projectId, componentId, candidate)) {
            return candidate;
        }
        int counter = 2;
        while (versionRepository.existsByProjectIdAndComponentIdAndVersionName(projectId, componentId, candidate + "-" + counter)) {
            counter++;
        }
        return candidate + "-" + counter;
    }

    private ComponentCodeVersionDTO toDTO(ComponentCodeVersion entity) {
        List<ComponentCodeTagDTO> tags = Collections.emptyList(); // Tags loaded separately

        return new ComponentCodeVersionDTO(
            entity.getId(),
            entity.getComponentId(),
            entity.getComponentName(),
            entity.getVersionName(),
            entity.getCommitMessage(),
            entity.getAuthor(),
            entity.getCreatedAt(),
            entity.getModelVersionId(),
            tags,
            entity.getCodeContent()
        );
    }
}
