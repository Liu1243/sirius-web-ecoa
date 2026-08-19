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
package org.eclipse.sirius.web.edt.representations.compositediagram.nodedescriptions;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.generated.diagram.DeleteToolBuilder;
import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.diagram.EdgeToolBuilder;
import org.eclipse.sirius.components.view.builder.generated.view.ChangeContextBuilder;
import org.eclipse.sirius.components.view.builder.generated.view.CreateInstanceBuilder;
import org.eclipse.sirius.components.view.builder.generated.view.SetValueBuilder;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.INodeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.NodePalette;
import org.eclipse.sirius.components.view.diagram.DeleteTool;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;
import org.eclipse.sirius.web.edt.representations.compositediagram.edgedescriptions.ServiceLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the Component node description for EDT CompositeDiagram.
 * Corresponds to EDTComponentNode in ecoadt.odesign.
 *
 * @author EDT Team
 */
public class ComponentNodeDescriptionProvider implements INodeDescriptionProvider {

    public static final String NAME = "Component";

    public static final String SERVICE_NODE_NAME = "ComponentService";

    public static final String REFERENCE_NODE_NAME = "ComponentReference";

    private static final String AQL_TRUE = "aql:true";

    private final IColorProvider colorProvider;

    public ComponentNodeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public NodeDescription create() {
        var insideLabelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_TEXT))
                .borderSize(0)
                .build();

        var insideLabel = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else (if self.eClass().eAllStructuralFeatures.name->includes('Name') then self.Name else '' endif) endif")
                .style(insideLabelStyle)
                .build();

        var childrenLayoutStrategy = new DiagramBuilders().newFreeFormLayoutStrategyDescription()
                .build();

        var componentNodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_BORDER))
                .borderSize(1)
                .borderRadius(15)
                .borderLineStyle(LineStyle.SOLID)
                .childrenLayoutStrategy(childrenLayoutStrategy)
                .build();

        var deleteTool = this.createDeleteTool();

        return new DiagramBuilders().newNodeDescription()
                .name(NAME)
                .domainType("edtproject::Component")
                .semanticCandidatesExpression("aql:self.Components")
                .insideLabel(insideLabel)
                .style(componentNodeStyle)
                .palette(new DiagramBuilders().newNodePalette()
                        .deleteTool(deleteTool)
                        .build())
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .borderNodesDescriptions(
                        this.createServiceBorderNodeDescription(),
                        this.createReferenceBorderNodeDescription(),
                        this.createPropertyBorderNodeDescription()
                )
                .build();
    }

    private NodeDescription createServiceBorderNodeDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.SERVICE_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.ComponentDefinitionService.name")
                .style(labelStyle)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.SERVICE_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.SERVICE_BORDER))
                .borderSize(1)
                .borderRadius(8)
                .borderLineStyle(LineStyle.SOLID)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(SERVICE_NODE_NAME)
                .domainType("edtproject::ComponentService")
                .semanticCandidatesExpression("aql:self.ComponentServices")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    private NodeDescription createReferenceBorderNodeDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.REFERENCE_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.ComponentDefinitionReference.name")
                .style(labelStyle)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.REFERENCE_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.REFERENCE_BORDER))
                .borderSize(1)
                .borderRadius(8)
                .borderLineStyle(LineStyle.SOLID)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name(REFERENCE_NODE_NAME)
                .domainType("edtproject::ComponentReference")
                .semanticCandidatesExpression("aql:self.ComponentReferences")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    private NodeDescription createPropertyBorderNodeDescription() {
        var labelStyle = new DiagramBuilders().newInsideLabelStyle()
                .showIconExpression(AQL_TRUE)
                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PROPERTY_TEXT))
                .borderSize(0)
                .build();

        var label = new DiagramBuilders().newInsideLabelDescription()
                .labelExpression("aql:self.ComponentDefinitionProperty.name")
                .style(labelStyle)
                .build();

        var nodeStyle = new DiagramBuilders().newRectangularNodeStyleDescription()
                .background(this.colorProvider.getColor(EdtColorPaletteProvider.PROPERTY_BACKGROUND))
                .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PROPERTY_BORDER))
                .borderSize(1)
                .borderRadius(5)
                .borderLineStyle(LineStyle.SOLID)
                .build();

        return new DiagramBuilders().newNodeDescription()
                .name("ComponentProperty")
                .domainType("edtproject::ComponentProperty")
                .semanticCandidatesExpression("aql:self.Properties")
                .insideLabel(label)
                .style(nodeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    private NodePalette createNodePaletteWithEdgeTool(NodeDescription serviceNodeDescription, NodeDescription referenceNodeDescription,
            EdgeDescription serviceLinkEdgeDescription) {
        var createServiceLinkTool = this.createServiceLinkEdgeTool(serviceNodeDescription, referenceNodeDescription, serviceLinkEdgeDescription);
        return new DiagramBuilders().newNodePalette()
                .edgeTools(createServiceLinkTool)
                .build();
    }

    private DeleteTool createDeleteTool() {
        return new DeleteToolBuilder()
                .name("Delete")
                .body(new ChangeContextBuilder()
                        .expression("aql:self.deleteComponentAndRelatedLinks()")
                        .build())
                .build();
    }

    private EdgeTool createServiceLinkEdgeTool(NodeDescription serviceNodeDescription, NodeDescription referenceNodeDescription,
            EdgeDescription serviceLinkEdgeDescription) {
        // Create ServiceLink: source (Reference) -> target (Service) OR source (Service) -> target (Reference)
        // The ServiceLink is created in the Composite container
        var createInstance = new CreateInstanceBuilder()
                .typeName("edtproject::ServiceLink")
                // EDTProject2.ecore defines this containment reference on Composite as 'ServiceLinks' (capital S, L)
                .referenceName("ServiceLinks")
                .variableName("newServiceLink")
                .children(
                        new SetValueBuilder()
                                .featureName("source")
                                .valueExpression("aql:if semanticEdgeSource.eClass().name = 'ComponentReference' then semanticEdgeSource else semanticEdgeTarget endif")
                                .build(),
                        new SetValueBuilder()
                                .featureName("target")
                                .valueExpression("aql:if semanticEdgeTarget.eClass().name = 'ComponentService' then semanticEdgeTarget else semanticEdgeSource endif")
                                .build()
                )
                .build();

        var changeContext = new ChangeContextBuilder()
                // Navigate to Composite container to create ServiceLink
                .expression("aql:semanticEdgeSource.eContainer().eContainer()")
                .children(createInstance)
                .build();

        return new EdgeToolBuilder()
                .name("Create Service Link")
                .iconURLsExpression("aql:'/icons/RequestLink.png'")
                .targetElementDescriptions(serviceNodeDescription, referenceNodeDescription)
                .body(changeContext)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        var optionalComponentNodeDescription = cache.getNodeDescription(NAME);

        optionalComponentNodeDescription.ifPresent(componentNodeDescription -> {
            diagramDescription.getNodeDescriptions().add(componentNodeDescription);

            // Find Service and Reference border nodes
            NodeDescription serviceNodeDescription = null;
            NodeDescription referenceNodeDescription = null;

            for (var borderNode : componentNodeDescription.getBorderNodesDescriptions()) {
                if (SERVICE_NODE_NAME.equals(borderNode.getName())) {
                    serviceNodeDescription = borderNode;
                } else if (REFERENCE_NODE_NAME.equals(borderNode.getName())) {
                    referenceNodeDescription = borderNode;
                }
            }

            // Retrieve the ServiceLink edge description without using a lambda that captures non-final variables
            var optionalServiceLinkEdgeDescription = cache.getEdgeDescription(ServiceLinkEdgeDescriptionProvider.NAME);
            if (optionalServiceLinkEdgeDescription.isPresent()
                    && serviceNodeDescription != null
                    && referenceNodeDescription != null) {
                var serviceLinkEdgeDescription = optionalServiceLinkEdgeDescription.get();
                var palette = this.createNodePaletteWithEdgeTool(serviceNodeDescription, referenceNodeDescription, serviceLinkEdgeDescription);
                serviceNodeDescription.setPalette(palette);
                referenceNodeDescription.setPalette(palette);
            }
        });
    }
}
