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
package org.eclipse.sirius.web.edt.views.details;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.generated.form.FormBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.form.GroupDescription;
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.sirius.components.view.form.PageDescription;
import org.eclipse.sirius.components.view.form.TreeDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.eclipse.sirius.web.edt.views.details.api.IPageDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Used to provide the custom page description for Step2 (ComponentDefinitions).
 *
 * @author EDT Team
 */
@Service("edtStep2PageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class Step2PageDescriptionProvider implements IPageDescriptionProvider {

    private static final String FOLDER_ICON = "aql:Sequence{'/icons/Folder.svg'}";

    private final IEdtMessageService messageService;

    public Step2PageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription();
        var componentDefinitionsGroupDescription = this.getComponentDefinitionsGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Step2 - ComponentDefinitions")
                .domainType("edtproject:Step2")
                .labelExpression("aql:'2-ComponentDefinitions'")
                .groups(
                        corePropertiesGroupDescription,
                        componentDefinitionsGroupDescription
                )
                .build();
    }

    private GroupDescription getCorePropertiesGroupDescription() {
        return new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();
    }

    private GroupDescription getComponentDefinitionsGroupDescription() {
        var groupDescription = new FormBuilders().newGroupDescription()
                .name("Component Definitions")
                .labelExpression(this.messageService.componentDefinitions())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        groupDescription.getChildren().add(this.getComponentDefinitionsWidget());

        return groupDescription;
    }

    private TreeDescription getComponentDefinitionsWidget() {
        return new FormBuilders().newTreeDescription()
                .name("All Component Definitions")
                .labelExpression(this.messageService.allComponentDefinitions())
                .childrenExpression("aql:if self.eClass().name = 'Step2' then self.ComponentDefinitions else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
