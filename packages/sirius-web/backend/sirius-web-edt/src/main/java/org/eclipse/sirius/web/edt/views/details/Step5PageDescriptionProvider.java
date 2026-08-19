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
 * Used to provide the custom page description for Step5 (Integration).
 *
 * @author EDT Team
 */
@Service("edtStep5PageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class Step5PageDescriptionProvider implements IPageDescriptionProvider {

    private static final String FOLDER_ICON = "aql:Sequence{'/icons/Folder.svg'}";

    private final IEdtMessageService messageService;

    public Step5PageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription();
        var integrationGroupDescription = this.getIntegrationGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Step5 - Integration")
                .domainType("edtproject:Step5")
                .labelExpression("aql:'5-Integration'")
                .groups(
                        corePropertiesGroupDescription,
                        integrationGroupDescription
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

    private GroupDescription getIntegrationGroupDescription() {
        var groupDescription = new FormBuilders().newGroupDescription()
                .name("Integration")
                .labelExpression(this.messageService.integration())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        groupDescription.getChildren().add(this.getLogicalSystemWidget());
        groupDescription.getChildren().add(this.getDeploymentWidget());

        return groupDescription;
    }

    private TreeDescription getLogicalSystemWidget() {
        return new FormBuilders().newTreeDescription()
                .name("Logical System")
                .labelExpression(this.messageService.logicalSystem())
                .childrenExpression("aql:if self.eClass().name = 'Step5' then self.LogicalSystem else Sequence{} endif")
                .treeItemLabelExpression("aql:self.id")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }

    private TreeDescription getDeploymentWidget() {
        return new FormBuilders().newTreeDescription()
                .name("Deployment")
                .labelExpression(this.messageService.deployment())
                .childrenExpression("aql:if self.eClass().name = 'Step5' then self.Deployment else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
