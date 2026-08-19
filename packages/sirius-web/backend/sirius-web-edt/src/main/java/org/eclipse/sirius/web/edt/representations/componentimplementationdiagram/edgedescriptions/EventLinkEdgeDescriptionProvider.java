package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions;

import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IEdgeDescriptionProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.SynchronizationPolicy;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.nodedescriptions.ComponentImplementationNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the EventLink edge description for EDT Component Implementation Diagram.
 */
public class EventLinkEdgeDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "EventLink";

    private final IColorProvider colorProvider;

    public EventLinkEdgeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public EdgeDescription create() {
        var edgeStyle = new DiagramBuilders().newEdgeStyle()
                .color(this.colorProvider.getColor(EdtColorPaletteProvider.EVENT_LINK_COLOR))
                .edgeWidth(1)
                .lineStyle(LineStyle.SOLID)
                .showIcon(false)
                .build();

        return new DiagramBuilders().newEdgeDescription()
                .name(NAME)
                .domainType("edtimplementation::EventLink")
                .semanticCandidatesExpression("aql:self.OperationLinks->select(l | l.eClass().eAllSuperTypes.name->includes('EventLink') or l.eClass().name = 'EventLink')")
                .isDomainBasedEdge(true)
                .sourceExpression("aql:self.sender")
                .targetExpression("aql:self.receiver")
                .centerLabelExpression("")
                .style(edgeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getEdgeDescription(NAME).ifPresent(edgeDescription -> {
            // ModuleInstance ports
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.SENDER_PORT_NAME).ifPresent(edgeDescription.getSourceDescriptions()::add);
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.RECEIVER_PORT_NAME).ifPresent(edgeDescription.getTargetDescriptions()::add);

            // TriggerInstance port
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.TRIGGER_SENDER_PORT_NAME).ifPresent(edgeDescription.getSourceDescriptions()::add);

            // DynamicTriggerInstance ports
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.DYNAMIC_TRIGGER_SENDER_PORT_NAME).ifPresent(edgeDescription.getSourceDescriptions()::add);
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.DYNAMIC_TRIGGER_RECEIVER_PORT_NAME).ifPresent(edgeDescription.getTargetDescriptions()::add);

            // External operation ports (can be both source and target)
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.EXTERNAL_OPERATION_PORT_NAME).ifPresent(n -> {
                edgeDescription.getSourceDescriptions().add(n);
                edgeDescription.getTargetDescriptions().add(n);
            });

            // Service and Reference ports (can be both source and target)
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.SERVICE_OPERATION_PORT_NAME).ifPresent(n -> {
                edgeDescription.getSourceDescriptions().add(n);
                edgeDescription.getTargetDescriptions().add(n);
            });

            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.REFERENCE_OPERATION_PORT_NAME).ifPresent(n -> {
                edgeDescription.getSourceDescriptions().add(n);
                edgeDescription.getTargetDescriptions().add(n);
            });

            diagramDescription.getEdgeDescriptions().add(edgeDescription);
        });
    }
}
