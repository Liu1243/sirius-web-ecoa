package org.eclipse.sirius.web.permissions;

import org.eclipse.sirius.web.auth.CurrentUserService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
/**
 * Assigns the creator to newly created projects.
 *
 * @author Codex
 */
public class ProjectCreatedMembershipInitializer {

    private final CurrentUserService currentUserService;

    private final ProjectPermissionService projectPermissionService;

    public ProjectCreatedMembershipInitializer(CurrentUserService currentUserService, ProjectPermissionService projectPermissionService) {
        this.currentUserService = currentUserService;
        this.projectPermissionService = projectPermissionService;
    }

    @EventListener
    public void onProjectCreated(ProjectCreatedEvent event) {
        this.currentUserService.getCurrentUser()
                .ifPresent(user -> this.projectPermissionService.assignOwner(event.project().getId(), user.id()));
    }
}
