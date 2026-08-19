package org.eclipse.sirius.web.permissions;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Instant;
import java.util.List;

import org.eclipse.sirius.web.auth.CurrentUserService;
import org.eclipse.sirius.web.auth.UserAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints used to administrate users.
 *
 * @author Codex
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {

    private final CurrentUserService currentUserService;

    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    public UserAdministrationController(CurrentUserService currentUserService, UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.currentUserService = currentUserService;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserSummaryResponse> getUsers() {
        this.requireGlobalAdministrator();
        return this.userAccountRepository.findAllUsers();
    }

    @GetMapping("/{userId}")
    public UserSummaryResponse getUser(@PathVariable String userId) {
        this.requireGlobalAdministrator();
        return this.userAccountRepository.findById(userId)
                .map(user -> new UserSummaryResponse(user.id(), user.username(), user.displayName(), user.admin(), user.active()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    @PutMapping("/{userId}")
    public UserSummaryResponse updateUser(@PathVariable String userId, @RequestBody UpdateUserRequest request) {
        var currentUser = this.requireGlobalAdministrator();
        if (request == null || !this.hasText(request.username()) || !this.hasText(request.displayName())) {
            throw new ResponseStatusException(BAD_REQUEST, "Username and display name are required");
        }
        if (this.hasText(request.password()) && request.password().length() < 8) {
            throw new ResponseStatusException(BAD_REQUEST, "The password must be at least 8 characters long");
        }

        var existingUser = this.userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        this.preventRemovingLastAdministrator(existingUser.admin(), request.admin(), existingUser.active(), currentUser.id(), existingUser.id());

        if (this.userAccountRepository.existsByDisplayNameExcluding(request.displayName().trim(), userId)) {
            throw new ResponseStatusException(CONFLICT, "The display name already exists");
        }

        try {
            this.userAccountRepository.updateUser(userId, request.username().trim(), request.displayName().trim(), request.admin(), Instant.now());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "The username already exists", exception);
        }

        if (this.hasText(request.password())) {
            this.userAccountRepository.updatePassword(userId, this.passwordEncoder.encode(request.password().trim()), Instant.now());
        }

        return this.userAccountRepository.findById(userId)
                .map(user -> new UserSummaryResponse(user.id(), user.username(), user.displayName(), user.admin(), user.active()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The user could not be loaded"));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String userId) {
        var currentUser = this.requireGlobalAdministrator();
        var existingUser = this.userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (!existingUser.active()) {
            return;
        }
        if (currentUser.id().equals(existingUser.id())) {
            throw new ResponseStatusException(FORBIDDEN, "You cannot deactivate your own account");
        }
        this.preventRemovingLastAdministrator(existingUser.admin(), false, existingUser.active(), currentUser.id(), existingUser.id());

        this.userAccountRepository.deactivateUser(userId, Instant.now());
    }

    @DeleteMapping("/{userId}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDeleteUser(@PathVariable String userId) {
        var currentUser = this.requireGlobalAdministrator();
        var existingUser = this.userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (currentUser.id().equals(existingUser.id())) {
            throw new ResponseStatusException(FORBIDDEN, "You cannot delete your own account");
        }
        this.preventRemovingLastAdministrator(existingUser.admin(), false, existingUser.active(), currentUser.id(), existingUser.id());

        this.userAccountRepository.deleteUserPermanently(userId);
    }

    private org.eclipse.sirius.web.auth.AppUser requireGlobalAdministrator() {
        var currentUser = this.currentUserService.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (!currentUser.admin()) {
            throw new ResponseStatusException(FORBIDDEN, "Only a global administrator can manage users");
        }
        return currentUser;
    }

    private void preventRemovingLastAdministrator(boolean wasAdmin, boolean willBeAdmin, boolean active, String currentUserId, String targetUserId) {
        if (!active || !wasAdmin || willBeAdmin) {
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(FORBIDDEN, "You cannot remove your own administrator rights");
        }
        if (this.userAccountRepository.countAdministrators() <= 1) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one active administrator must remain");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
