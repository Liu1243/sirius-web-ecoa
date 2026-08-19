package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram;

import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.DefaultViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IDiagramElementDescriptionProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.ArrangeLayoutDirection;
import org.eclipse.sirius.components.view.diagram.DiagramFactory;
import org.eclipse.sirius.components.view.diagram.DiagramPalette;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.DataLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.EventLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.RequestLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.nodedescriptions.ComponentImplementationNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools.CreateDataLinkToolProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools.CreateEventLinkToolProvider;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools.CreateRequestLinkToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the view model for EDT Component Implementation Diagram.
 */
public class EdtComponentImplementationDiagramDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String NAME = "EDT Component Implementation Diagram";

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtComponentImplementationDiagramDescriptionProvider.class);

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        var diagramDescription = DiagramFactory.eINSTANCE.createDiagramDescription();
        diagramDescription.setName(NAME);
        diagramDescription.setDomainType("edtimplementation::ComponentImplementation");
        diagramDescription.setTitleExpression(
                "aql:(if self.eClass().eAllStructuralFeatures.name->includes('name') then self.name else '' endif) + ' - Component Implementation Diagram'");
        diagramDescription.setAutoLayout(true);
        diagramDescription.setArrangeLayoutDirection(ArrangeLayoutDirection.RIGHT);

        var cache = new DefaultViewDiagramElementFinder();

        var nodeProvider = new ComponentImplementationNodeDescriptionProvider(colorProvider);
        // IMPORTANT: call createNodes() only ONCE and reuse the same list.
        // Calling it twice creates different object instances, causing identity mismatch
        // between what's in the cache and what's in the diagramDescription → NPE in EdgeComponent.hasCandidates
        var topLevelNodes = nodeProvider.createNodes();
        topLevelNodes.forEach(node -> {
            putNodeRecursively(node, cache);
        });
        
        List<IDiagramElementDescriptionProvider<?>> edgeProviders = List.of(
                new DataLinkEdgeDescriptionProvider(colorProvider),
                new RequestLinkEdgeDescriptionProvider(colorProvider),
                new EventLinkEdgeDescriptionProvider(colorProvider)
        );

        edgeProviders.forEach(provider -> {
            var description = provider.create();
            cache.put(description);
        });

        // Reuse the SAME node instances (not a second call to createNodes())
        topLevelNodes.forEach(diagramDescription.getNodeDescriptions()::add);
        edgeProviders.forEach(provider -> provider.link(diagramDescription, cache));

        var dataLinkTool = new CreateDataLinkToolProvider().create(cache);
        var requestLinkTool = new CreateRequestLinkToolProvider().create(cache);
        var eventLinkTool = new CreateEventLinkToolProvider().create(cache);

        var dataLinkPalette = new DiagramBuilders().newNodePalette()
                .edgeTools(dataLinkTool)
                .build();
        var requestLinkPalette = new DiagramBuilders().newNodePalette()
                .edgeTools(requestLinkTool)
                .build();
        var eventLinkPalette = new DiagramBuilders().newNodePalette()
                .edgeTools(eventLinkTool)
                .build();
        var genericPalette = new DiagramBuilders().newNodePalette()
                .edgeTools(
                        EcoreUtil.copy(dataLinkTool),
                        EcoreUtil.copy(requestLinkTool),
                        EcoreUtil.copy(eventLinkTool))
                .build();

        diagramDescription.getNodeDescriptions().forEach(node -> this.assignPaletteRecursively(node, dataLinkPalette, requestLinkPalette, eventLinkPalette, genericPalette));

        diagramDescription.setPalette(this.diagramPalette());

        return diagramDescription;
    }

    private void putNodeRecursively(NodeDescription node, DefaultViewDiagramElementFinder cache) {
        cache.put(node);
        node.getBorderNodesDescriptions().forEach(border -> putNodeRecursively(border, cache));
        node.getChildrenDescriptions().forEach(child -> putNodeRecursively(child, cache));
    }

    private void assignPaletteRecursively(NodeDescription node, org.eclipse.sirius.components.view.diagram.NodePalette dataLinkPalette,
            org.eclipse.sirius.components.view.diagram.NodePalette requestLinkPalette,
            org.eclipse.sirius.components.view.diagram.NodePalette eventLinkPalette,
            org.eclipse.sirius.components.view.diagram.NodePalette genericPalette) {
        String nodeName = node.getName();
        if (nodeName != null) {
            switch (nodeName) {
                case ComponentImplementationNodeDescriptionProvider.WRITER_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.READER_PORT_NAME:
                    node.setPalette(EcoreUtil.copy(dataLinkPalette));
                    LOGGER.info("Assigned data palette to nodeName={} edgeTools={}", nodeName, node.getPalette() != null ? node.getPalette().getEdgeTools().size() : -1);
                    break;
                case ComponentImplementationNodeDescriptionProvider.CLIENT_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.SERVER_PORT_NAME:
                    node.setPalette(EcoreUtil.copy(requestLinkPalette));
                    LOGGER.info("Assigned request palette to nodeName={} edgeTools={}", nodeName, node.getPalette() != null ? node.getPalette().getEdgeTools().size() : -1);
                    break;
                case ComponentImplementationNodeDescriptionProvider.SENDER_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.RECEIVER_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.TRIGGER_SENDER_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.DYNAMIC_TRIGGER_SENDER_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.DYNAMIC_TRIGGER_RECEIVER_PORT_NAME:
                    node.setPalette(EcoreUtil.copy(eventLinkPalette));
                    LOGGER.info("Assigned event palette to nodeName={} edgeTools={}", nodeName, node.getPalette() != null ? node.getPalette().getEdgeTools().size() : -1);
                    break;
                case ComponentImplementationNodeDescriptionProvider.EXTERNAL_OPERATION_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.SERVICE_OPERATION_PORT_NAME:
                case ComponentImplementationNodeDescriptionProvider.REFERENCE_OPERATION_PORT_NAME:
                    node.setPalette(EcoreUtil.copy(genericPalette));
                    LOGGER.info("Assigned generic palette to nodeName={} edgeTools={}", nodeName, node.getPalette() != null ? node.getPalette().getEdgeTools().size() : -1);
                    break;
                default:
                    break;
            }
        }

        node.getBorderNodesDescriptions().forEach(borderNode -> this.assignPaletteRecursively(borderNode, dataLinkPalette, requestLinkPalette, eventLinkPalette, genericPalette));
        node.getChildrenDescriptions().forEach(childNode -> this.assignPaletteRecursively(childNode, dataLinkPalette, requestLinkPalette, eventLinkPalette, genericPalette));
    }

    private DiagramPalette diagramPalette() {
        return new DiagramBuilders().newDiagramPalette()
                .build();
    }
}
