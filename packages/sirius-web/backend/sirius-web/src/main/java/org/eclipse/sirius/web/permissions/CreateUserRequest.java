package org.eclipse.sirius.web.permissions;

/**
 * The request used to create a user and optionally assign it to a project.
 *
 * @author Codex
 */
public record CreateUserRequest(String username, String displayName, String password, boolean admin, String projectId, ProjectRole role) {
}
