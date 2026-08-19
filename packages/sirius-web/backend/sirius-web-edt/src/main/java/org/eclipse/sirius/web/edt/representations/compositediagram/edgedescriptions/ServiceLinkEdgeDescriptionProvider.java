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
package org.eclipse.sirius.web.edt.representations.compositediagram.edgedescriptions;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DeleteToolBuilder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ChangeContextBuilder;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IEdgeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;
import org.eclipse.sirius.web.edt.representations.compositediagram.nodedescriptions.ComponentNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the ServiceLink edge description for EDT CompositeDiagram.
 *
 * @author EDT Team
 */
public class ServiceLinkEdgeDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "ServiceLink";

    private final IColorProvider colorProvider;

    public ServiceLinkEdgeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public EdgeDescription create() {
        var edgeStyle = new DiagramBuilders().newEdgeStyle()
                .color(this.colorProvider.getColor(EdtColorPaletteProvider.EDGE_COLOR))
                .edgeWidth(1)
                .lineStyle(LineStyle.SOLID)
                .showIcon(false)
                .build();

        return new DiagramBuilders().newEdgeDescription()
                .name(NAME)
                .domainType("edtproject::ServiceLink")
                // EDTProject2.ecore defines this containment reference on Composite as 'ServiceLinks' (capital S, L)
                .semanticCandidatesExpression("aql:self.ServiceLinks")
                // Mark as domain-based so that the converter uses the semantic candidates on the Composite
                // (edtproject::ServiceLink instances) instead of relation-based edge discovery.
                .isDomainBasedEdge(true)
                .sourceExpression("aql:if self.eClass().name = 'ServiceLink' then self.source else null endif")
                .targetExpression("aql:if self.eClass().name = 'ServiceLink' then self.target else null endif")
                .centerLabelExpression("")
                .style(edgeStyle)
                .palette(new DiagramBuilders().newEdgePalette()
                        .deleteTool(new DeleteToolBuilder()
                                .name("Delete")
                                .body(new ChangeContextBuilder()
                                        .expression("aql:self.defaultDelete()")
                                        .build())
                                .build())
                        .build())
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getEdgeDescription(NAME).ifPresent(edgeDescription -> {
            cache.getNodeDescription(ComponentNodeDescriptionProvider.NAME).ifPresent(componentNode -> {
                componentNode.getBorderNodesDescriptions().forEach(borderNode -> {
                    if ("ComponentService".equals(borderNode.getName()) || "ComponentReference".equals(borderNode.getName())) {
                        edgeDescription.getSourceDescriptions().add(borderNode);
                        edgeDescription.getTargetDescriptions().add(borderNode);
                    }
                });
            });
            diagramDescription.getEdgeDescriptions().add(edgeDescription);
        });
    }
}
