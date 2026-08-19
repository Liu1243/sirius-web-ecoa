package org.eclipse.sirius.web.permissions;

/**
 * The response describing a project membership.
 *
 * @author Codex
 */
public record ProjectMembershipResponse(String userId, String username, String displayName, boolean admin, ProjectRole role) {
}
