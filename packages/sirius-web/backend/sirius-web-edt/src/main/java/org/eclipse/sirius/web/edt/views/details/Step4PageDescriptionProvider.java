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
 * Used to provide the custom page description for Step4 (ComponentImplementations).
 *
 * @author EDT Team
 */
@Service("edtStep4PageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class Step4PageDescriptionProvider implements IPageDescriptionProvider {

    private static final String FOLDER_ICON = "aql:Sequence{'/icons/Folder.svg'}";

    private final IEdtMessageService messageService;

    public Step4PageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription();
        var implementationsGroupDescription = this.getImplementationsGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Step4 - ComponentImplementations")
                .domainType("edtproject:Step4")
                .labelExpression("aql:'4-ComponentImplementations'")
                .groups(
                        corePropertiesGroupDescription,
                        implementationsGroupDescription
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

    private GroupDescription getImplementationsGroupDescription() {
        var groupDescription = new FormBuilders().newGroupDescription()
                .name("Component Implementations")
                .labelExpression(this.messageService.componentImplementations())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        groupDescription.getChildren().add(this.getComponentImplementationsWidget());

        return groupDescription;
    }

    private TreeDescription getComponentImplementationsWidget() {
        return new FormBuilders().newTreeDescription()
                .name("All Component Implementations")
                .labelExpression(this.messageService.allComponentImplementations())
                .childrenExpression("aql:if self.eClass().name = 'Step4' then self.ComponentImplementations else Sequence{} endif")
                .treeItemLabelExpression("aql:self.name")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
