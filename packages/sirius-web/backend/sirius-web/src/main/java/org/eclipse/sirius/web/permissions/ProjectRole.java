package org.eclipse.sirius.web.permissions;

/**
 * The role granted on a project.
 *
 * @author Codex
 */
public enum ProjectRole {
    ACCESS;

    public boolean canView() {
        return true;
    }

    public boolean canEdit() {
        return true;
    }

    public boolean canManage() {
        return false;
    }
}
