package org.eclipse.sirius.web.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
/**
 * Configuration properties of the bootstrap administrator.
 *
 * @author Codex
 */
public class SecurityBootstrapAdminProperties {

    private String username = "admin";

    private String password = "admin123456";

    private String displayName = "System Administrator";

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
