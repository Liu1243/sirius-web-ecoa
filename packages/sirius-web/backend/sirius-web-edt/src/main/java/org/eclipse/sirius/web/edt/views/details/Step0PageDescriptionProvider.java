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
 * Used to provide the custom page description for Step0 (Types).
 *
 * @author EDT Team
 */
@Service("edtStep0PageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class Step0PageDescriptionProvider implements IPageDescriptionProvider {

    private static final String FOLDER_ICON = "aql:Sequence{'/icons/Folder.svg'}";

    private final IEdtMessageService messageService;

    public Step0PageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription();
        var typesGroupDescription = this.getTypesGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Step0 - Types")
                .domainType("edtproject:Step0")
                .labelExpression("aql:'0-Types'")
                .groups(
                        corePropertiesGroupDescription,
                        typesGroupDescription
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

    private GroupDescription getTypesGroupDescription() {
        var typesGroupDescription = new FormBuilders().newGroupDescription()
                .name("Types")
                .labelExpression(this.messageService.types())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        typesGroupDescription.getChildren().add(this.getBasicTypesWidget());
        typesGroupDescription.getChildren().add(this.getEcoaPredefinedTypesWidget());
        typesGroupDescription.getChildren().add(this.getLibrariesWidget());

        return typesGroupDescription;
    }

    private TreeDescription getBasicTypesWidget() {
        return new FormBuilders().newTreeDescription()
                .name("Basic Types")
                .labelExpression(this.messageService.basicTypes())
                .childrenExpression("aql:if self.eClass().name = 'Step0' then self.BasicTypes else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }

    private TreeDescription getEcoaPredefinedTypesWidget() {
        return new FormBuilders().newTreeDescription()
                .name("ECOA Predefined Types")
                .labelExpression(this.messageService.ecoaPredefinedTypes())
                .childrenExpression("aql:if self.eClass().name = 'Step0' then self.EcoaPredefinedTypes else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }

    private TreeDescription getLibrariesWidget() {
        return new FormBuilders().newTreeDescription()
                .name("Libraries")
                .labelExpression(this.messageService.libraries())
                .childrenExpression("aql:if self.eClass().name = 'Step0' then self.Types else Sequence{} endif")
                .treeItemLabelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .treeItemBeginIconExpression(FOLDER_ICON)
                .isCheckableExpression("aql:false")
                .isTreeItemSelectableExpression("aql:false")
                .build();
    }
}
