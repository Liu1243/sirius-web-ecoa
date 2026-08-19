package org.eclipse.sirius.web.auth;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints used to manage authentication.
 *
 * @author Codex
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;

    private final CurrentUserService currentUserService;

    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    public AuthenticationController(AuthenticationManager authenticationManager, CurrentUserService currentUserService, UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.currentUserService = currentUserService;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/session")
    public SessionUserResponse session() {
        return this.currentUserService.toSessionUserResponse();
    }

    @PostMapping("/login")
    public ResponseEntity<SessionUserResponse> login(@RequestBody LoginRequest loginRequest, HttpSession session) {
        try {
            Authentication authentication = this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return ResponseEntity.ok(this.currentUserService.toSessionUserResponse());
        } catch (BadCredentialsException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public SessionUserResponse logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return this.currentUserService.toSessionUserResponse();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        var currentUser = this.currentUserService.getCurrentUser();
        ResponseEntity<Void> response;
        if (currentUser.isEmpty()) {
            response = ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } else if (!this.isValidChangePasswordRequest(request)) {
            response = ResponseEntity.badRequest().build();
        } else if (!this.hasValidCurrentPassword(currentUser.get().username(), request.currentPassword())) {
            response = ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } else {
            this.userAccountRepository.updatePassword(currentUser.get().id(), this.passwordEncoder.encode(request.newPassword()), Instant.now());
            response = ResponseEntity.noContent().build();
        }
        return response;
    }

    private boolean isValidChangePasswordRequest(ChangePasswordRequest request) {
        return request != null
                && this.hasText(request.currentPassword())
                && this.hasText(request.newPassword())
                && request.newPassword().length() >= 8;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasValidCurrentPassword(String username, String currentPassword) {
        boolean valid = true;
        try {
            this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, currentPassword));
        } catch (BadCredentialsException exception) {
            valid = false;
        }
        return valid;
    }
}
