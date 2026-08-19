package org.eclipse.sirius.web.permissions;

import java.util.Optional;

import org.eclipse.sirius.web.application.capability.SiriusWebCapabilities;
import org.eclipse.sirius.web.application.capability.services.CapabilityVote;
import org.eclipse.sirius.web.application.capability.services.api.ICapabilityVoter;
import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.springframework.stereotype.Service;

@Service
public class ProjectPermissionCapabilityVoter implements ICapabilityVoter {

    private final CurrentUserService currentUserService;

    private final ProjectMembershipRepository projectMembershipRepository;

    public ProjectPermissionCapabilityVoter(CurrentUserService currentUserService, ProjectMembershipRepository projectMembershipRepository) {
        this.currentUserService = currentUserService;
        this.projectMembershipRepository = projectMembershipRepository;
    }

    @Override
    public CapabilityVote vote(String type, String identifier, String capability) {
        Optional<AppUser> currentUser = this.currentUserService.getCurrentUser();

        if (SiriusWebCapabilities.PROJECT.equals(type)) {
            return this.voteProjectCapability(currentUser, identifier, capability);
        }
        if (SiriusWebCapabilities.PROJECT_SETTINGS.equals(type)) {
            return this.canManageProject(currentUser, identifier) && SiriusWebCapabilities.ProjectSettings.VIEW.equals(capability) ? CapabilityVote.GRANTED : CapabilityVote.DENIED;
        }
        if (type != null && type.startsWith(SiriusWebCapabilities.PROJECT_SETTINGS + '#')) {
            return this.voteProjectSettingsTabCapability(currentUser, type, identifier, capability);
        }
        if (SiriusWebCapabilities.LIBRARY.equals(type) && SiriusWebCapabilities.Library.LIST.equals(capability)) {
            return currentUser.isPresent() ? CapabilityVote.GRANTED : CapabilityVote.DENIED;
        }
        return CapabilityVote.ABSTAIN;
    }

    private CapabilityVote voteProjectCapability(Optional<AppUser> currentUser, String projectId, String capability) {
        if (currentUser.isEmpty()) {
            return CapabilityVote.DENIED;
        }

        AppUser user = currentUser.get();
        if (user.admin()) {
            return CapabilityVote.GRANTED;
        }

        if (projectId == null) {
            return switch (capability) {
                case SiriusWebCapabilities.Project.LIST, SiriusWebCapabilities.Project.CREATE, SiriusWebCapabilities.Project.UPLOAD -> CapabilityVote.GRANTED;
                default -> CapabilityVote.DENIED;
            };
        }

        Optional<ProjectRole> role = this.projectMembershipRepository.findRole(projectId, user.id());
        if (role.isEmpty()) {
            return CapabilityVote.DENIED;
        }

        return switch (capability) {
            case SiriusWebCapabilities.Project.VIEW,
                    SiriusWebCapabilities.Project.DOWNLOAD,
                    SiriusWebCapabilities.Project.EDIT,
                    SiriusWebCapabilities.Project.DUPLICATE,
                    SiriusWebCapabilities.Project.RENAME,
                    SiriusWebCapabilities.Project.DELETE -> CapabilityVote.GRANTED;
            default -> CapabilityVote.DENIED;
        };
    }

    private boolean canManageProject(Optional<AppUser> currentUser, String projectId) {
        if (currentUser.isEmpty() || projectId == null) {
            return false;
        }
        AppUser user = currentUser.get();
        return user.admin() || this.projectMembershipRepository.findRole(projectId, user.id()).filter(ProjectRole::canManage).isPresent();
    }

    private CapabilityVote voteProjectSettingsTabCapability(Optional<AppUser> currentUser, String type, String projectId, String capability) {
        if (!SiriusWebCapabilities.ProjectSettingsTab.VIEW.equals(capability)) {
            return CapabilityVote.DENIED;
        }
        if (SiriusWebCapabilities.PROJECT_SETTINGS_MEMBERS_TAB.equals(type)) {
            return this.canManageProject(currentUser, projectId) ? CapabilityVote.GRANTED : CapabilityVote.DENIED;
        }
        return this.canViewProject(currentUser, projectId) ? CapabilityVote.GRANTED : CapabilityVote.DENIED;
    }

    private boolean canViewProject(Optional<AppUser> currentUser, String projectId) {
        if (currentUser.isEmpty() || projectId == null) {
            return false;
        }
        AppUser user = currentUser.get();
        return user.admin() || this.projectMembershipRepository.findRole(projectId, user.id()).filter(ProjectRole::canView).isPresent();
    }
}
