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
package org.eclipse.sirius.web.edt.omnibox;

import org.eclipse.sirius.components.collaborative.omnibox.api.IWorkbenchOmniboxCommandProvider;
import org.eclipse.sirius.components.collaborative.omnibox.dto.OmniboxCommand;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Provides the show documentation command for EDT projects.
 *
 * @author EDT Team
 */
@Service
public class EdtShowDocumentationCommandProvider implements IWorkbenchOmniboxCommandProvider {

    private final IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate;

    public EdtShowDocumentationCommandProvider(IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate) {
        this.edtCapableEditingContextPredicate = Objects.requireNonNull(edtCapableEditingContextPredicate);
    }

    @Override
    public List<OmniboxCommand> getCommands(String editingContextId, List<String> selectedObjectIds, String query) {
        List<OmniboxCommand> result = List.of();
        if (this.edtCapableEditingContextPredicate.test(editingContextId)) {
            result = List.of(new OmniboxCommand("showEdtDocumentation", "Show EDT Documentation", List.of("/omnibox/show-documentation.svg"), "Navigate to EDT documentation"));
        }
        return result;
    }

}
