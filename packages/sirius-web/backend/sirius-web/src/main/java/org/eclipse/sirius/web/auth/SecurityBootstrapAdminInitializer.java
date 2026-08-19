package org.eclipse.sirius.web.auth;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
/**
 * Creates the bootstrap administrator on startup when none exists.
 * Also ensures the bootstrap administrator has admin privileges.
 *
 * @author Codex
 */
public class SecurityBootstrapAdminInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;

    private final PasswordEncoder passwordEncoder;

    private final SecurityBootstrapAdminProperties properties;

    public SecurityBootstrapAdminInitializer(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, SecurityBootstrapAdminProperties properties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var existingUser = this.userAccountRepository.findByUsername(this.properties.getUsername());
        if (existingUser.isPresent()) {
            // If bootstrap user exists but is not admin, fix it and update password
            if (!existingUser.get().admin()) {
                this.userAccountRepository.updateUser(
                        existingUser.get().id(),
                        existingUser.get().username(),
                        existingUser.get().displayName(),
                        true,
                        Instant.now()
                );
            }
            // Always update password to ensure it matches the configured bootstrap password
            this.userAccountRepository.updatePassword(
                    existingUser.get().id(),
                    this.passwordEncoder.encode(this.properties.getPassword()),
                    Instant.now()
            );
        } else if (this.userAccountRepository.countAdministrators() == 0) {
            // Only create bootstrap admin if no admins exist and no bootstrap user exists
            this.userAccountRepository.createUser(this.properties.getUsername(), this.properties.getDisplayName(), this.passwordEncoder.encode(this.properties.getPassword()), true, Instant.now());
        }
    }
}
