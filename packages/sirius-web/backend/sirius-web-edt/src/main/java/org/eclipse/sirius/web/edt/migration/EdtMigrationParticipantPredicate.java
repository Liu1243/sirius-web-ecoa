/*******************************************************************************
 * Copyright (c) 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.migration;

import java.util.Objects;

import org.eclipse.sirius.web.application.editingcontext.services.api.IEditingContextMigrationParticipantPredicate;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.springframework.stereotype.Service;

/**
 * Used to indicate that the EDT editing context will need migration participants.
 *
 * @author EDT Team
 */
@Service
public class EdtMigrationParticipantPredicate implements IEditingContextMigrationParticipantPredicate {

    private final IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate;

    public EdtMigrationParticipantPredicate(IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate) {
        this.edtCapableEditingContextPredicate = Objects.requireNonNull(edtCapableEditingContextPredicate);
    }

    @Override
    public boolean test(String editingContextId) {
        return this.edtCapableEditingContextPredicate.test(editingContextId);
    }
}
