package org.eclipse.sirius.web.auth;

/**
 * The request used to change the current user's password.
 *
 * @author Codex
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
