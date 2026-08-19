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

import java.util.List;

import org.eclipse.sirius.components.view.builder.generated.form.FormBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.form.WidgetDescription;
import org.springframework.stereotype.Service;

/**
 * Used to return the widgets used to display the details view of a named element.
 *
 * @author managerial
 */
@Service("edtNamedElementWidgetsProvider")
public class NamedElementWidgetsProvider {

    public List<WidgetDescription> getWidgets(IColorProvider colorProvider, String featureName) {
        var nameWidget = this.getNameWidget(colorProvider, featureName);
        return List.of(nameWidget);
    }

    private WidgetDescription getNameWidget(IColorProvider colorProvider, String featureName) {
        var nameStyleDescription = new FormBuilders().newTextareaDescriptionStyle()
                .build();

        return new FormBuilders().newTextAreaDescription()
                .name("Name")
                .labelExpression("Name")
                .helpExpression("aql:'edtTip.common.name'")
                .valueExpression("aql:self." + featureName)
                .diagnosticsExpression("aql:if self." + featureName + " <> null and self." + featureName + " <> '' then null else 'ERROR: The name is required' endif")
                .style(nameStyleDescription)
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(
                                        new ViewBuilders().newSetValue()
                                                .featureName(featureName)
                                                .valueExpression("aql:newValue")
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    /*
    private RichTextDescription getDescriptionWidget() {
        return new FormBuilders().newRichTextDescription()
                .name("Description")
                .labelExpression("Description")
                .valueExpression("aql:self.description")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(
                                        new ViewBuilders().newSetValue()
                                                .featureName("description")
                                                .valueExpression("aql:newValue")
                                                .build()
                                )
                                .build()
                )
                .build();
    }
    */
}
