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
import org.eclipse.sirius.components.view.builder.generated.reference.ReferenceBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.form.GroupDescription;
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.sirius.components.view.form.PageDescription;
import org.eclipse.sirius.components.view.widget.reference.ReferenceWidgetDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.eclipse.sirius.web.edt.views.details.api.IPageDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Used to provide the custom page description for edt components.
 *
 * @author managerial
 */
@Service("edtComponentPageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class ComponentPageDescriptionProvider implements IPageDescriptionProvider {

    private final NamedElementWidgetsProvider namedElementWidgetsProvider;

    private final IEdtMessageService messageService;

    public ComponentPageDescriptionProvider(NamedElementWidgetsProvider namedElementWidgetsProvider, IEdtMessageService messageService) {
        this.namedElementWidgetsProvider = Objects.requireNonNull(namedElementWidgetsProvider);
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = this.getCorePropertiesGroupDescription(colorProvider);
        var usedLibrariesGroupDescription = this.getUsedLibrariesGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Component")
                .domainType("edtimplementation:ComponentImplementation")
                .labelExpression("aql:self.name")
                .groups(
                        corePropertiesGroupDescription,
                        usedLibrariesGroupDescription
                )
                .build();
    }

    private GroupDescription getCorePropertiesGroupDescription(IColorProvider colorProvider) {
        var group = new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        group.getChildren().add(this.newComponentDefinitionReferenceWidget());
        group.getChildren().addAll(this.namedElementWidgetsProvider.getWidgets(colorProvider, "name"));

        return group;
    }

    private GroupDescription getUsedLibrariesGroupDescription() {
        var group = new FormBuilders().newGroupDescription()
                .name("Used Libraries")
                .labelExpression(this.messageService.usedLibraries())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        group.getChildren().add(this.newUsedLibrariesReferenceWidget());

        return group;
    }

    private ReferenceWidgetDescription newComponentDefinitionReferenceWidget() {
        var style = new ReferenceBuilders().newReferenceWidgetDescriptionStyle().build();

        return new ReferenceBuilders().newReferenceWidgetDescription()
                .name("Component Definition")
                .labelExpression(this.messageService.componentDefinition())
                .referenceOwnerExpression("aql:self")
                .referenceNameExpression("aql:'componentDefinition'")
                .style(style)
                .build();
    }

    private ReferenceWidgetDescription newUsedLibrariesReferenceWidget() {
        var style = new ReferenceBuilders().newReferenceWidgetDescriptionStyle().build();

        return new ReferenceBuilders().newReferenceWidgetDescription()
                .name("Used Libraries")
                .labelExpression(this.messageService.usedLibraries())
                .referenceOwnerExpression("aql:self")
                .referenceNameExpression("aql:'usedLibraries'")
                .style(style)
                .build();
    }
}
