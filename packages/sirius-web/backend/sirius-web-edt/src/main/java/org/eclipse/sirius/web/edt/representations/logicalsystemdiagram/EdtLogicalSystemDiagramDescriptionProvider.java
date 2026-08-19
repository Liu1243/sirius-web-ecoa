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
package org.eclipse.sirius.web.edt.representations.logicalsystemdiagram;

import java.util.List;

import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.DefaultViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IDiagramElementDescriptionProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.ArrangeLayoutDirection;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.edgedescriptions.NodeLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.edgedescriptions.PlatformLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.nodedescriptions.LogicalPlatformNodeDescriptionProvider;

/**
 * Provides the view model for EDT Logical System Diagram.
 * Replaces the odesign LogicalSystemDeployment definition from Sirius Desktop.
 *
 * @author EDT Team
 */
public class EdtLogicalSystemDiagramDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String NAME = "EDT Logical System Diagram";

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        var logicalSystemDiagramDescription = DiagramFactory.eINSTANCE.createDiagramDescription();
        logicalSystemDiagramDescription.setName(NAME);
        logicalSystemDiagramDescription.setDomainType("edtlogical::LogicalSystem");
        logicalSystemDiagramDescription.setTitleExpression("aql:self.id + ' - Logical System Diagram'");
        logicalSystemDiagramDescription.setAutoLayout(true);
        logicalSystemDiagramDescription.setArrangeLayoutDirection(ArrangeLayoutDirection.RIGHT);

        var cache = new DefaultViewDiagramElementFinder();

        List<IDiagramElementDescriptionProvider<?>> diagramElementDescriptionProviders = List.of(
                new LogicalPlatformNodeDescriptionProvider(colorProvider),
                new PlatformLinkEdgeDescriptionProvider(colorProvider),
                new NodeLinkEdgeDescriptionProvider(colorProvider)
        );

        diagramElementDescriptionProviders.forEach(provider -> {
            var description = provider.create();
            cache.put(description);
        });
        diagramElementDescriptionProviders.forEach(provider ->
            provider.link(logicalSystemDiagramDescription, cache));

        return logicalSystemDiagramDescription;
    }
}
