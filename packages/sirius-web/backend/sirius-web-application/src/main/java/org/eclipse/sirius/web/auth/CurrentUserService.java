/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.auth;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
/**
 * Used to resolve the currently authenticated user.
 *
 * @author Codex
 */
public class CurrentUserService {

    private final IUserRepository userRepository;

    public CurrentUserService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return this.userRepository.findByUsername(authentication.getName());
    }

    public SessionUserResponse toSessionUserResponse() {
        return this.getCurrentUser()
                .map(user -> new SessionUserResponse(true, user.id(), user.username(), user.displayName(), user.admin()))
                .orElseGet(() -> new SessionUserResponse(false, null, null, null, false));
    }
}
