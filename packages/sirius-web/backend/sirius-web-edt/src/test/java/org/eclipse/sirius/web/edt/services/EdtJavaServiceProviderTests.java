/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.junit.jupiter.api.Test;

public class EdtJavaServiceProviderTests {

    @Test
    public void givenEdtViewWhenQueryingServicesThenDeletionAndValidationServicesAreRegistered() {
        var provider = new EdtJavaServiceProvider();
        var edtDiagram = DiagramFactory.eINSTANCE.createDiagramDescription();
        edtDiagram.setDomainType("edtproject::Composite");

        View view = new ViewBuilders().newView()
                .descriptions(edtDiagram)
                .build();

        assertThat(provider.getServiceClasses(view))
                .contains(EdtDeletionService.class, EdtValidationService.class);
    }

    @Test
    public void givenNonEdtViewWhenQueryingServicesThenNoEdtServicesAreRegistered() {
        var provider = new EdtJavaServiceProvider();
        var otherDiagram = DiagramFactory.eINSTANCE.createDiagramDescription();
        otherDiagram.setDomainType("papaya:Project");

        View view = new ViewBuilders().newView()
                .descriptions(otherDiagram)
                .build();

        assertThat(provider.getServiceClasses(view)).isEmpty();
    }
}
