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
package org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.edgedescriptions;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IEdgeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.nodedescriptions.LogicalPlatformNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the NodeLink edge description for EDT LogicalSystem Diagram.
 * Connects LogicalComputingNode nodes within a platform.
 *
 * @author EDT Team
 */
public class NodeLinkEdgeDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "NodeLink";

    private final IColorProvider colorProvider;

    public NodeLinkEdgeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public EdgeDescription create() {
        var edgeStyle = new DiagramBuilders().newEdgeStyle()
                .color(this.colorProvider.getColor(EdtColorPaletteProvider.NODE_LINK_COLOR))
                .edgeWidth(1)
                .lineStyle(LineStyle.SOLID)
                .showIcon(false)
                .build();

        return new DiagramBuilders().newEdgeDescription()
                .name(NAME)
                .domainType("edtlogical::LogicalComputingNodeLink")
                .semanticCandidatesExpression("aql:self.logicalComputingPlatforms->collect(p | p.logicalComputingNodeLinks)->flatten()")
                .isDomainBasedEdge(true)
                .sourceExpression("aql:self.from")
                .targetExpression("aql:self.to")
                .centerLabelExpression("aql:self.id")
                .style(edgeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getEdgeDescription(NAME).ifPresent(edgeDescription -> {
            cache.getNodeDescription(LogicalPlatformNodeDescriptionProvider.NAME).ifPresent(platformNode -> {
                // Find the LogicalNode child description
                platformNode.getChildrenDescriptions().stream()
                    .filter(child -> LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME.equals(child.getName()))
                    .findFirst()
                    .ifPresent(logicalNode -> {
                        edgeDescription.getSourceDescriptions().add(logicalNode);
                        edgeDescription.getTargetDescriptions().add(logicalNode);
                    });
            });
            diagramDescription.getEdgeDescriptions().add(edgeDescription);
        });
    }
}
