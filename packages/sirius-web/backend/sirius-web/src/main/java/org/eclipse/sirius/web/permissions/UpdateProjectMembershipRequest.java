package org.eclipse.sirius.web.permissions;

/**
 * The request used to update a project membership.
 *
 * @author Codex
 */
public record UpdateProjectMembershipRequest(ProjectRole role) {
}
