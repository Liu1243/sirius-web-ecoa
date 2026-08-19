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

/**
 * Interface for looking up application users by username.
 * Used to decouple CurrentUserService from the concrete UserAccountRepository.
 *
 * @author Codex
 */
public interface IUserRepository {

    Optional<AppUser> findByUsername(String username);
}
