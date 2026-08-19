package org.eclipse.sirius.web.application.componentcode.services;

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagDTO;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.ComponentCodeTag;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.VersionTag;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IComponentCodeTagRepository;
import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IVersionTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for managing component code tags.
 *
 * @author lanceliu
 */
@Service
@Transactional
public class ComponentCodeTagService implements IComponentCodeTagService {

    private final IComponentCodeTagRepository tagRepository;
    private final IVersionTagRepository versionTagRepository;

    public ComponentCodeTagService(IComponentCodeTagRepository tagRepository, IVersionTagRepository versionTagRepository) {
        this.tagRepository = tagRepository;
        this.versionTagRepository = versionTagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentCodeTagDTO> getComponentCodeTags(UUID projectId) {
        return tagRepository.findByProjectIdOrderByNameAsc(projectId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Override
    public ComponentCodeTagDTO createComponentCodeTag(UUID projectId, String name, String color) {
        if (tagRepository.existsByProjectIdAndName(projectId, name)) {
            throw new IllegalArgumentException("Tag name already exists for this project");
        }

        ComponentCodeTag entity = ComponentCodeTag.newBuilder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .name(name)
            .color(color)
            .createdAt(Instant.now())
            .build();

        ComponentCodeTag saved = tagRepository.save(entity);
        return toDTO(saved);
    }

    @Override
    public void addTagToVersion(UUID versionId, UUID tagId) {
        if (!versionTagRepository.existsByVersionIdAndTagId(versionId, tagId)) {
            VersionTag entity = VersionTag.newBuilder()
                .versionId(versionId)
                .tagId(tagId)
                .build();
            versionTagRepository.save(entity);
        }
    }

    @Override
    public void removeTagFromVersion(UUID versionId, UUID tagId) {
        versionTagRepository.deleteById(new VersionTag.VersionTagId(versionId, tagId));
    }

    private ComponentCodeTagDTO toDTO(ComponentCodeTag entity) {
        return new ComponentCodeTagDTO(entity.getId(), entity.getName(), entity.getColor());
    }
}
