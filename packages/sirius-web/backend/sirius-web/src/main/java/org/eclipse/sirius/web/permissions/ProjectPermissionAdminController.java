package org.eclipse.sirius.web.permissions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.UserAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class ProjectPermissionAdminController {

    private final ProjectPermissionService projectPermissionService;

    private final ProjectMembershipRepository projectMembershipRepository;

    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    public ProjectPermissionAdminController(ProjectPermissionService projectPermissionService, ProjectMembershipRepository projectMembershipRepository, UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.projectPermissionService = projectPermissionService;
        this.projectMembershipRepository = projectMembershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/projects/{projectId}/permissions")
    public ProjectPermissionsResponse getProjectPermissions(@PathVariable String projectId) {
        this.requirePermissionManager(projectId);
        List<ProjectMembershipResponse> memberships = this.projectMembershipRepository.findMemberships(projectId);
        List<UserSummaryResponse> users = this.userAccountRepository.findAllActiveUsers();
        return new ProjectPermissionsResponse(projectId, memberships, users);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryResponse createUser(@RequestBody CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank() || request.displayName() == null || request.displayName().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, display name and password are required");
        }
        if (request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The initial password must be at least 8 characters long");
        }

        var currentUser = this.projectPermissionService.requireAuthenticatedUser();
        if (request.admin() && !currentUser.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a global administrator can create another administrator");
        }
        if (request.projectId() != null && !request.projectId().isBlank()) {
            this.requirePermissionManager(request.projectId());
        } else if (!currentUser.admin() && !request.admin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A project must be selected when creating a non-admin user");
        }

        if (this.userAccountRepository.existsByDisplayName(request.displayName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The display name already exists");
        }

        String newUserId;
        try {
            newUserId = this.userAccountRepository.createUser(
                    request.username().trim(),
                    request.displayName().trim(),
                    this.passwordEncoder.encode(request.password()),
                    request.admin(),
                    Instant.now());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The username already exists", exception);
        }

        if (request.projectId() != null && !request.projectId().isBlank()) {
            ProjectRole role = request.role() == null ? ProjectRole.ACCESS : request.role();
            this.projectPermissionService.updateMembership(request.projectId(), newUserId, role);
        }

        return this.userAccountRepository.findById(newUserId)
                .map(user -> new UserSummaryResponse(user.id(), user.username(), user.displayName(), user.admin(), user.active()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be loaded"));
    }

    @PutMapping("/projects/{projectId}/permissions/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertMembership(@PathVariable String projectId, @PathVariable String userId, @RequestBody UpdateProjectMembershipRequest request) {
        System.out.println("[DEBUG] upsertMembership START");
        this.requirePermissionManager(projectId);
        this.userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        this.projectPermissionService.updateMembership(projectId, userId, request.role());
        System.out.println("[DEBUG] upsertMembership END");
    }

    @DeleteMapping("/projects/{projectId}/permissions/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMembership(@PathVariable String projectId, @PathVariable String userId) {
        this.requirePermissionManager(projectId);
        this.projectPermissionService.deleteMembership(projectId, userId);
    }

    private AppUser requirePermissionManager(String projectId) {
        System.out.println("[DEBUG] requirePermissionManager START");
        if (!this.projectMembershipRepository.projectExists(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        AppUser currentUser = this.projectPermissionService.requireAuthenticatedUser();
        System.out.println("[DEBUG] currentUser: " + currentUser.username() + ", admin: " + currentUser.admin());
        if (currentUser.admin() || this.projectPermissionService.canManageProject(Optional.of(currentUser), projectId)) {
            System.out.println("[DEBUG] Permission GRANTED");
            return currentUser;
        }
        System.out.println("[DEBUG] Permission DENIED - throwing 403");
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to manage this project's permissions");
    }
}
