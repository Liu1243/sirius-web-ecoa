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
 * Provides the DataLink edge description for EDT Component Implementation Diagram.
 */
public class DataLinkEdgeDescriptionProvider implements IEdgeDescriptionProvider {

    public static final String NAME = "DataLink";

    private final IColorProvider colorProvider;

    public DataLinkEdgeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    @Override
    public EdgeDescription create() {
        var edgeStyle = new DiagramBuilders().newEdgeStyle()
                .color(this.colorProvider.getColor(EdtColorPaletteProvider.DATA_LINK_COLOR))
                .edgeWidth(1)
                .lineStyle(LineStyle.SOLID)
                .showIcon(false)
                .build();

        return new DiagramBuilders().newEdgeDescription()
                .name(NAME)
                .domainType("edtimplementation::DataLink")
                .semanticCandidatesExpression("aql:self.OperationLinks->select(l | l.eClass().eAllSuperTypes.name->includes('DataLink') or l.eClass().name = 'DataLink')")
                .isDomainBasedEdge(true)
                .sourceExpression("aql:self.writer")
                .targetExpression("aql:self.reader")
                .centerLabelExpression("")
                .style(edgeStyle)
                .synchronizationPolicy(SynchronizationPolicy.SYNCHRONIZED)
                .build();
    }

    @Override
    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        cache.getEdgeDescription(NAME).ifPresent(edgeDescription -> {
            // Writer ports belong to ModuleInstance - directly look up by name since they're flat-cached
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.WRITER_PORT_NAME).ifPresent(edgeDescription.getSourceDescriptions()::add);
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.READER_PORT_NAME).ifPresent(edgeDescription.getTargetDescriptions()::add);

            // Reference side (source for read operations)
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.REFERENCE_OPERATION_PORT_NAME).ifPresent(edgeDescription.getSourceDescriptions()::add);
            // Service side (target)
            cache.getNodeDescription(ComponentImplementationNodeDescriptionProvider.SERVICE_OPERATION_PORT_NAME).ifPresent(edgeDescription.getTargetDescriptions()::add);

            diagramDescription.getEdgeDescriptions().add(edgeDescription);
        });
    }
}
