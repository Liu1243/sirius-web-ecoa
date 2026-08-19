package org.eclipse.sirius.web.application.componentcode.services.api;

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagDTO;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing component code tags.
 *
 * @author lanceliu
 */
public interface IComponentCodeTagService {

    List<ComponentCodeTagDTO> getComponentCodeTags(UUID projectId);

    ComponentCodeTagDTO createComponentCodeTag(UUID projectId, String name, String color);

    void addTagToVersion(UUID versionId, UUID tagId);

    void removeTagFromVersion(UUID versionId, UUID tagId);
}
