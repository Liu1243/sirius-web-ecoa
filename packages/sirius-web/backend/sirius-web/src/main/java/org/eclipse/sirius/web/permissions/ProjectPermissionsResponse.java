package org.eclipse.sirius.web.permissions;

import java.util.List;

/**
 * The response containing the users and memberships of a project.
 *
 * @author Codex
 */
public record ProjectPermissionsResponse(String projectId, List<ProjectMembershipResponse> memberships, List<UserSummaryResponse> users) {
}
