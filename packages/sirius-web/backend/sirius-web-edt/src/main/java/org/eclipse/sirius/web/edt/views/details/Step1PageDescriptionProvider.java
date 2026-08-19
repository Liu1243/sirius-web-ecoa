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
 * Used to provide the custom page description for Step1 (Services).
 *
 * @author EDT Team
 */
@Service("edtStep1PageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class Step1PageDescriptionProvider implements IPageDescriptionProvider {

    private static final String FOLDER_ICON = "aql:Sequence{'/icons/Folder.svg'}";

    private final IEdtMessageService messageService;

    public Step1PageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription();
        var servicesGroupDescription = this.getServicesGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Step1 - Services")
                .domainType("edtproject:Step1")
                .labelExpression("aql:'1-Services'")
                .groups(
                        corePropertiesGroupDescription,
                        servicesGroupDescription
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

    private GroupDescription getServicesGroupDescription() {
        var servicesGroupDescription = new FormBuilders().newGroupDescription()
                .name("Services")
                .labelExpression(this.messageService.services())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        servicesGroupDescription.getChildren().add(this.getServiceDefinitionsWidget());

        return servicesGroupDescription;
    }

    private TreeDescription getServiceDefinitionsWidget() {
        return new FormBuilders().newTreeDescription()
                .name("Service Definitions")
                .labelExpression(this.messageService.serviceDefinitions())
                .childrenExpression("aql:if self.eClass().name = 'Step1' then self.Services else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
