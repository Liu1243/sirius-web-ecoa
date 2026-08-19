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
 * Used to provide the custom page description for edt projects.
 *
 * @author managerial
 */
@Service("edtProjectPageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class ProjectPageDescriptionProvider implements IPageDescriptionProvider {

    private final NamedElementWidgetsProvider namedElementWidgetsProvider;

    private final IEdtMessageService messageService;

    public ProjectPageDescriptionProvider(NamedElementWidgetsProvider namedElementWidgetsProvider, IEdtMessageService messageService) {
        this.namedElementWidgetsProvider = Objects.requireNonNull(namedElementWidgetsProvider);
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription(colorProvider);
        var additionalInformationGroupDescription = this.getAdditionalInformationGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Project")
                .domainType("edtproject:Project")
                .labelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .groups(
                        corePropertiesGroupDescription,
                        additionalInformationGroupDescription
                )
                .build();
    }

    private GroupDescription getCorePropertiesGroupDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        corePropertiesGroupDescription.getChildren().addAll(this.namedElementWidgetsProvider.getWidgets(colorProvider, "name"));

        return corePropertiesGroupDescription;
    }

    private GroupDescription getAdditionalInformationGroupDescription() {
        var additionalInformationGroupDescription = new FormBuilders().newGroupDescription()
                .name("Additional Information")
                .labelExpression("Additional Information")
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        additionalInformationGroupDescription.getChildren().add(this.getAllStepsWidget());

        return additionalInformationGroupDescription;
    }

    private TreeDescription getAllStepsWidget() {
        return new FormBuilders().newTreeDescription()
                .name("All Steps")
                .labelExpression("All Steps")
                .childrenExpression("aql:self.steps.step0->union(self.steps.step1->asSequence())->union(self.steps.step2->asSequence())->union(self.steps.step3->asSequence())->union(self.steps.step4->asSequence())->union(self.steps.step5->asSequence())->reject(s | s = null)")
                .treeItemLabelExpression("aql:self.eClass().name")
                .treeItemBeginIconExpression("aql:Sequence{'/icons/Folder.svg'}")
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
