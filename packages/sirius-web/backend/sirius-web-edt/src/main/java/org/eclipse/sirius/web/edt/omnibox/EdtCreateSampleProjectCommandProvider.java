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

import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.collaborative.omnibox.api.IWorkbenchOmniboxCommandProvider;
import org.eclipse.sirius.components.collaborative.omnibox.dto.OmniboxCommand;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.springframework.stereotype.Service;

/**
 * Provides the sample project creation command for EDT projects.
 *
 * @author EDT Team
 */
@Service
public class EdtCreateSampleProjectCommandProvider implements IWorkbenchOmniboxCommandProvider {

    public static final String CREATE_SAMPLE_PROJECT_COMMAND_ID = "create_edt_sample_project";

    private final IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate;

    public EdtCreateSampleProjectCommandProvider(IEdtCapableEditingContextPredicate edtCapableEditingContextPredicate) {
        this.edtCapableEditingContextPredicate = Objects.requireNonNull(edtCapableEditingContextPredicate);
    }

    @Override
    public List<OmniboxCommand> getCommands(String editingContextId, List<String> selectedObjectIds, String query) {
        if (this.edtCapableEditingContextPredicate.test(editingContextId)) {
            return List.of(new OmniboxCommand(CREATE_SAMPLE_PROJECT_COMMAND_ID, "Create Sample EDT Project", List.of("/omnibox/create.svg"), "Create a sample EDT project with basic structure"));
        }
        return List.of();
    }
}
