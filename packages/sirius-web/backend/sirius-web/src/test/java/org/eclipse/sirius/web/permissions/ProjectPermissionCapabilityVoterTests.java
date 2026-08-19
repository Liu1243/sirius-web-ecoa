package org.eclipse.sirius.web.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.eclipse.sirius.web.application.capability.SiriusWebCapabilities;
import org.eclipse.sirius.web.application.capability.services.CapabilityVote;
import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.junit.jupiter.api.Test;

public class ProjectPermissionCapabilityVoterTests {

    @Test
    public void accessMembersCannotViewProjectSettings() {
        String projectId = "project-1";
        AppUser user = new AppUser("user-1", "user", "User", "hash", false, true);

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        ProjectMembershipRepository projectMembershipRepository = mock(ProjectMembershipRepository.class);

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(user));
        when(projectMembershipRepository.findRole(projectId, user.id())).thenReturn(Optional.of(ProjectRole.ACCESS));

        ProjectPermissionCapabilityVoter voter = new ProjectPermissionCapabilityVoter(currentUserService, projectMembershipRepository);

        assertThat(voter.vote(SiriusWebCapabilities.PROJECT_SETTINGS, projectId, SiriusWebCapabilities.ProjectSettings.VIEW)).isEqualTo(CapabilityVote.DENIED);
        assertThat(voter.vote(SiriusWebCapabilities.PROJECT_SETTINGS_MEMBERS_TAB, projectId, SiriusWebCapabilities.ProjectSettingsTab.VIEW)).isEqualTo(CapabilityVote.DENIED);
    }
}
