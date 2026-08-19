package org.eclipse.sirius.web.auth;

/**
 * The login request payload.
 *
 * @author Codex
 */
public record LoginRequest(String username, String password) {
}
