package org.eclipse.sirius.web.permissions;

/**
 * The summary of a user exposed by the administration API.
 *
 * @author Codex
 */
public record UserSummaryResponse(String id, String username, String displayName, boolean admin, boolean active) {
}
