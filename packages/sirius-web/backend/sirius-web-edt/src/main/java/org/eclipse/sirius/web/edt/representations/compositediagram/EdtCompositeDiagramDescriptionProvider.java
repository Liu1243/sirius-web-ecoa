/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
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
package org.eclipse.sirius.web.edt.representations.compositediagram;

import java.util.List;

import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.DefaultViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IDiagramElementDescriptionProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.ArrangeLayoutDirection;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.eclipse.sirius.components.view.diagram.DiagramPalette;
import org.eclipse.sirius.web.edt.representations.compositediagram.edgedescriptions.ServiceLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.compositediagram.nodedescriptions.ComponentNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.compositediagram.tools.CreateComponentToolProvider;

/**
 * Provides the view model for EDT CompositeDiagram.
 * Replaces the odesign CompositeDiagram definition from Sirius Desktop.
 *
 * @author EDT Team
 */
public class EdtCompositeDiagramDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String NAME = "EDT Composite Diagram";

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        var compositeDiagramDescription = DiagramFactory.eINSTANCE.createDiagramDescription();
        compositeDiagramDescription.setName(NAME);
        compositeDiagramDescription.setDomainType("edtproject::Composite");
        compositeDiagramDescription.setTitleExpression("aql:(if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif) + ' - Composite Diagram'");
        compositeDiagramDescription.setAutoLayout(true);
        compositeDiagramDescription.setArrangeLayoutDirection(ArrangeLayoutDirection.RIGHT);

        var cache = new DefaultViewDiagramElementFinder();

        List<IDiagramElementDescriptionProvider<?>> diagramElementDescriptionProviders = List.of(
                new ComponentNodeDescriptionProvider(colorProvider),
                new ServiceLinkEdgeDescriptionProvider(colorProvider)
        );

        diagramElementDescriptionProviders.forEach(provider -> {
            var description = provider.create();
            cache.put(description);
        });
        diagramElementDescriptionProviders.forEach(provider ->
            provider.link(compositeDiagramDescription, cache));

        compositeDiagramDescription.setPalette(this.diagramPalette(cache));

        return compositeDiagramDescription;
    }

    private DiagramPalette diagramPalette(IViewDiagramElementFinder cache) {
        // Creation tools - Legend is displayed as fixed UI in frontend
        // Note: EdgeTool (Create ServiceLink) is only available on node palettes,
        // not diagram-level palette. It's configured on Service/Reference border nodes.
        var createComponentTool = new CreateComponentToolProvider().create(cache);

        return new DiagramBuilders().newDiagramPalette()
                .nodeTools(createComponentTool)
                .build();
    }
}

