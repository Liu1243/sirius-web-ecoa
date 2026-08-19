package org.eclipse.sirius.web.application.componentcode.services.api;

import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeHistoryDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing component code versions.
 *
 * @author lanceliu
 */
public interface IComponentCodeVersionService {

    ComponentCodeHistoryDTO getComponentCodeHistory(UUID projectId);

    Optional<ComponentCodeVersionDTO> getComponentCodeVersion(UUID versionId);

    /**
     * Creates a new component code version.
     *
     * @param projectId       the project ID
     * @param componentId     the component ID
     * @param componentName   the component name
     * @param versionName     the version name
     * @param codeContent     the code content
     * @param commitMessage   the commit message
     * @param author          the author
     * @param modelVersionId  the model version ID
     * @return the created version DTO
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    ComponentCodeVersionDTO createComponentCodeVersion(UUID projectId, String componentId, String componentName,
                                                       String versionName, String codeContent, String commitMessage,
                                                       String author, String modelVersionId);

    void deleteComponentCodeVersion(UUID versionId);

    /**
     * Creates a new component code version with PENDING import status.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    ComponentCodeVersionDTO createPendingComponentCodeVersion(UUID projectId, String componentId, String componentName,
                                                              String versionName, String codeContent, String commitMessage,
                                                              String author, String modelVersionId);

    /**
     * Returns all PENDING versions for a project (import not yet confirmed).
     */
    List<ComponentCodeVersionDTO> getPendingVersions(UUID projectId);

    /**
     * Confirms accepted pending versions (sets import_status to NULL) and rejects others (sets to REJECTED).
     */
    void confirmPendingVersions(UUID projectId, List<UUID> acceptedVersionIds, List<UUID> rejectedVersionIds);
}
