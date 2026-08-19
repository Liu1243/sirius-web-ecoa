package org.eclipse.sirius.web.permissions;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
/**
 * Services used to manage project permissions.
 *
 * @author Codex
 */
public class ProjectPermissionService {

    private final ProjectMembershipRepository projectMembershipRepository;

    private final CurrentUserService currentUserService;

    public ProjectPermissionService(ProjectMembershipRepository projectMembershipRepository, CurrentUserService currentUserService) {
        this.projectMembershipRepository = projectMembershipRepository;
        this.currentUserService = currentUserService;
    }

    public boolean canAccessProject(Optional<AppUser> currentUser, String projectId) {
        return currentUser
                .map(user -> user.admin() || this.projectMembershipRepository.findRole(projectId, user.id()).isPresent())
                .orElse(false);
    }

    public boolean canManageProject(Optional<AppUser> currentUser, String projectId) {
        return currentUser
                .map(user -> user.admin() || this.projectMembershipRepository.findRole(projectId, user.id()).filter(ProjectRole::canManage).isPresent())
                .orElse(false);
    }

    public AppUser requireAuthenticatedUser() {
        var user = this.currentUserService.getCurrentUser();
        System.out.println("[DEBUG] requireAuthenticatedUser - user present: " + user.isPresent());
        return user.orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authentication is required"));
    }

    public AppUser requireProjectAdministrator(String projectId) {
        if (!this.projectMembershipRepository.projectExists(projectId)) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }
        AppUser currentUser = this.requireAuthenticatedUser();
        if (!this.canManageProject(Optional.of(currentUser), projectId)) {
            throw new ResponseStatusException(FORBIDDEN, "You are not allowed to manage this project's permissions");
        }
        return currentUser;
    }

    public void updateMembership(String projectId, String userId, ProjectRole role) {
        this.projectMembershipRepository.upsertMembership(projectId, userId, ProjectRole.ACCESS, Instant.now());
    }

    public void deleteMembership(String projectId, String userId) {
        this.projectMembershipRepository.deleteMembership(projectId, userId);
    }

    public void assignOwner(String projectId, String userId) {
        this.projectMembershipRepository.upsertMembership(projectId, userId, ProjectRole.ACCESS, Instant.now());
    }
}
