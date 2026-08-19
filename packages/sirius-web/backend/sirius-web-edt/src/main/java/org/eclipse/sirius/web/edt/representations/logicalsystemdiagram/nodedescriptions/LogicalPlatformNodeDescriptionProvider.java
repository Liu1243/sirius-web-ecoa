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
package org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.nodedescriptions;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.INodeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.HeaderSeparatorDisplayMode;
import org.eclipse.sirius.components.view.diagram.InsideLabelPosition;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the LogicalComputingPlatform node description for EDT LogicalSystem Diagram.
 * This is the root container node that holds LogicalComputingNodes.
 *
 * @author EDT Team
 */
public class LogicalPlatformNodeDescriptionProvider implements INodeDescriptionProvider {

    public static final String NAME = "LogicalPlatform";

    public static final String LOGICAL_NODE_NAME = "LogicalNode";

    public static final String PROTECTION_DOMAIN_NAME = "ProtectionDomain";

    public static final String DEPLOYED_MODULE_INSTANCE_NAME = "DeployedModuleInstance";

    public static final String DEPLOYED_TRIGGER_INSTANCE_NAME = "DeployedTriggerInstance";

    private static final String AQL_TRUE = "aql:true";

    private final IColorProvider colorProvider;

    public LogicalPlatformNodeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public NodeDescription create() {
        var insideLabelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .withHeader(true)
                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.IF_CHILDREN)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PLATFORM_TEXT))
                .borderSize(0)
                .build();

        var insideLabel = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.id")
                .position(InsideLabelPosition.TOP_CENTER)
                .style(insideLabelStyle)
                .build();

        var childrenLayoutStrategy = new DiagramBuilders().newFreeFormLayoutStrategyDescription()
                .build();

        var platformNodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.PLATFORM_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PLATFORM_BORDER))
                .borderSize(2)
                .borderRadius(5)
                .borderLineStyle(LineStyle.SOLID)
                .childrenLayoutStrategy(childrenLayoutStrategy)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(NAME)
                .domainType("edtlogical::LogicalComputingPlatform")
                .semanticCandidatesExpression("aql:self.logicalComputingPlatforms")
                .insideLabel(insideLabel)
                .style(platformNodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .childrenDescriptions(
                        this.createLogicalNodeDescription()
                )
                .build();
    }

    private NodeDescription createLogicalNodeDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .withHeader(true)
                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.IF_CHILDREN)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.LOGICAL_NODE_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.id")
                .position(InsideLabelPosition.TOP_CENTER)
                .style(labelStyle)
                .build();

        var childrenLayoutStrategy = new DiagramBuilders().newFreeFormLayoutStrategyDescription()
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.LOGICAL_NODE_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.LOGICAL_NODE_BORDER))
                .borderSize(1)
                .borderRadius(5)
                .borderLineStyle(LineStyle.SOLID)
                .childrenLayoutStrategy(childrenLayoutStrategy)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(LOGICAL_NODE_NAME)
                .domainType("edtlogical::LogicalComputingNode")
                .semanticCandidatesExpression("aql:self.logicalComputingNodes")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .childrenDescriptions(
                        this.createProtectionDomainDescription()
                )
                .build();
    }

    private NodeDescription createProtectionDomainDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .withHeader(true)
                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.NEVER)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PROTECTION_DOMAIN_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.name")
                .position(InsideLabelPosition.TOP_CENTER)
                .style(labelStyle)
                .build();

        var deployedModuleInstanceDescription = this.createDeployedModuleInstanceDescription();
        var deployedTriggerInstanceDescription = this.createDeployedTriggerInstanceDescription();

        var childrenLayoutStrategy = new DiagramBuilders().newListLayoutStrategyDescription()
                .areChildNodesDraggableExpression("aql:false")
                .topGapExpression("12")
                .bottomGapExpression("12")
                .growableNodes(deployedModuleInstanceDescription, deployedTriggerInstanceDescription)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.PROTECTION_DOMAIN_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PROTECTION_DOMAIN_BORDER))
                .borderSize(1)
                .borderRadius(3)
                .borderLineStyle(LineStyle.SOLID)
                .childrenLayoutStrategy(childrenLayoutStrategy)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(PROTECTION_DOMAIN_NAME)
                .domainType("edtdeployment::ProtectionDomain")
                .semanticCandidatesExpression("aql:self.ProtectionDomainLink")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .childrenDescriptions(
                        deployedModuleInstanceDescription,
                        deployedTriggerInstanceDescription
                )
                .build();
    }

    private NodeDescription createDeployedModuleInstanceDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .withHeader(true)
                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.NEVER)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.ModuleInstance.name")
                .position(InsideLabelPosition.TOP_CENTER)
                .style(labelStyle)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_BORDER))
                .borderSize(1)
                .borderRadius(3)
                .borderLineStyle(LineStyle.SOLID)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(DEPLOYED_MODULE_INSTANCE_NAME)
                .domainType("edtdeployment::DeployedModuleInstance")
                .semanticCandidatesExpression("aql:self.deployedModuleInstances")
                .defaultWidthExpression("aql:260")
                .defaultHeightExpression("aql:96")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    private NodeDescription createDeployedTriggerInstanceDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .withHeader(true)
                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.NEVER)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.TriggerInstance.name")
                .position(InsideLabelPosition.TOP_CENTER)
                .style(labelStyle)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.DEPLOYED_INSTANCE_BORDER))
                .borderSize(1)
                .borderRadius(3)
                .borderLineStyle(LineStyle.SOLID)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(DEPLOYED_TRIGGER_INSTANCE_NAME)
                .domainType("edtdeployment::DeployedTriggerInstance")
                .semanticCandidatesExpression("aql:self.deployedTriggerInstances")
                .defaultWidthExpression("aql:260")
                .defaultHeightExpression("aql:96")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        var optionalPlatformNodeDescription = cache.getNodeDescription(NAME);
        optionalPlatformNodeDescription.ifPresent(platformNodeDescription -> {
            diagramDescription.getNodeDescriptions().add(platformNodeDescription);
        });
    }
}
