package org.eclipse.sirius.web.permissions;

/**
 * The request used to update an existing user.
 *
 * @author Codex
 */
public record UpdateUserRequest(String username, String displayName, boolean admin, String password) {
}
