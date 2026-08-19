package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.nodedescriptions;

import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.DiagramDescription;
import org.eclipse.sirius.components.view.diagram.HeaderSeparatorDisplayMode;
import org.eclipse.sirius.components.view.diagram.InsideLabelPosition;
import org.eclipse.sirius.components.view.diagram.LineStyle;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;

/**
 * Provides the Node descriptions for EDT Component Implementation Diagram.
 */
public class ComponentImplementationNodeDescriptionProvider {

    public static final String COMPONENT_IMPLEMENTATION_CONTAINER_NAME = "ComponentImplementationContainer";

    public static final String MODULE_INSTANCE_NAME = "ModuleInstance";
    public static final String TRIGGER_INSTANCE_NAME = "TriggerInstance";
    public static final String DYNAMIC_TRIGGER_INSTANCE_NAME = "DynamicTriggerInstance";
    
    public static final String EXTERNAL_NAME = "External";
    public static final String SERVICE_NAME = "Service";
    public static final String REFERENCE_NAME = "Reference";

    public static final String WRITER_PORT_NAME = "WriterOperationInstance";
    public static final String READER_PORT_NAME = "ReaderOperationInstance";
    public static final String CLIENT_PORT_NAME = "ClientOperationInstance";
    public static final String SERVER_PORT_NAME = "ServerOperationInstance";
    public static final String SENDER_PORT_NAME = "SenderOperationInstance";
    public static final String RECEIVER_PORT_NAME = "ReceiverOperationInstance";

    public static final String TRIGGER_SENDER_PORT_NAME = "TriggerSenderPort";
    public static final String DYNAMIC_TRIGGER_SENDER_PORT_NAME = "DynamicTriggerSenderPort";
    public static final String DYNAMIC_TRIGGER_RECEIVER_PORT_NAME = "DynamicTriggerReceiverPort";

    public static final String EXTERNAL_OPERATION_PORT_NAME = "ExternalOperationPort";
    public static final String SERVICE_OPERATION_PORT_NAME = "ServiceOperationPort";
    public static final String REFERENCE_OPERATION_PORT_NAME = "ReferenceOperationPort";

    private final IColorProvider colorProvider;
    private final DiagramBuilders builder = new DiagramBuilders();

    public ComponentImplementationNodeDescriptionProvider(IColorProvider colorProvider) {
        this.colorProvider = Objects.requireNonNull(colorProvider);
    }

    public List<NodeDescription> createNodes() {
        return List.of(
            createComponentImplementationContainerNode()
        );
    }

    private NodeDescription createComponentImplementationContainerNode() {
        return builder.newNodeDescription()
                .name(COMPONENT_IMPLEMENTATION_CONTAINER_NAME)
                .domainType("edtimplementation::ComponentImplementation")
                .semanticCandidatesExpression("aql:Sequence{self}") // Bind to the diagram root element itself
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:1200")
                .defaultHeightExpression("aql:800")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.TOP_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .bold(true)
                                .withHeader(true)
                                .showIconExpression("aql:true")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_TEXT))
                                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.NEVER)
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_BACKGROUND))
                        .borderColor(colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_BORDER))
                        .borderSize(1)
                        .borderRadius(15)
                        .borderLineStyle(LineStyle.SOLID)
                        .childrenLayoutStrategy(builder.newFreeFormLayoutStrategyDescription().build())
                        .build())
                .childrenDescriptions(
                        createModuleInstanceNode(),
                        createTriggerInstanceNode(),
                        createDynamicTriggerInstanceNode()
                )
                .borderNodesDescriptions(
                        createExternalNode(),
                        createServiceNode(),
                        createReferenceNode()
                )
                .build();
    }

    private NodeDescription createModuleInstanceNode() {
        List<NodeDescription> borders = List.of(
            createPortNode(WRITER_PORT_NAME, "edtimplementation::DataWriterInstance", "aql:'W'", EdtColorPaletteProvider.WRITER_PORT_BACKGROUND),
            createPortNode(READER_PORT_NAME, "edtimplementation::DataReaderInstance", "aql:'R'", EdtColorPaletteProvider.READER_PORT_BACKGROUND),
            createPortNode(CLIENT_PORT_NAME, "edtimplementation::RequestClientInstance", "aql:'C'", EdtColorPaletteProvider.CLIENT_PORT_BACKGROUND),
            createPortNode(SERVER_PORT_NAME, "edtimplementation::RequestServerInstance", "aql:'SR'", EdtColorPaletteProvider.SERVER_PORT_BACKGROUND),
            createPortNode(SENDER_PORT_NAME, "edtimplementation::EventSenderInstance", "aql:'S'", EdtColorPaletteProvider.SENDER_PORT_BACKGROUND),
            createPortNode(RECEIVER_PORT_NAME, "edtimplementation::EventReceiverInstance", "aql:'R'", EdtColorPaletteProvider.RECEIVER_PORT_BACKGROUND)
        );

        return builder.newNodeDescription()
                .name(MODULE_INSTANCE_NAME)
                .domainType("edtimplementation::ModuleInstance")
                .semanticCandidatesExpression("aql:self.instances->select(i | i.eClass().eAllSuperTypes.name->includes('ModuleInstance') or i.eClass().name = 'ModuleInstance')")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:300")
                .defaultHeightExpression("aql:140")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.TOP_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .bold(false)
                                .withHeader(true)
                                .showIconExpression("aql:true")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.MODULE_INSTANCE_TEXT))
                                .headerSeparatorDisplayMode(HeaderSeparatorDisplayMode.NEVER)
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(colorProvider.getColor(EdtColorPaletteProvider.MODULE_INSTANCE_BACKGROUND))
                        .borderColor(colorProvider.getColor(EdtColorPaletteProvider.MODULE_INSTANCE_BORDER))
                        .borderSize(1)
                        .build())
                .borderNodesDescriptions(borders.toArray(NodeDescription[]::new))
                .build();
    }

    private NodeDescription createTriggerInstanceNode() {
        List<NodeDescription> borders = List.of(
            builder.newNodeDescription()
                .name(TRIGGER_SENDER_PORT_NAME)
                .domainType("edtimplementation::OperationInstance")
                .semanticCandidatesExpression(operationsExpression())
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:'S'")
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_TEXT))
                                .borderSize(0).build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(this.colorProvider.getColor(EdtColorPaletteProvider.SENDER_PORT_BACKGROUND))
                        .borderSize(1).borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_BORDER)).build())
                .build()
        );

        return builder.newNodeDescription()
                .name(TRIGGER_INSTANCE_NAME)
                .domainType("edtimplementation::TriggerInstance")
                .semanticCandidatesExpression("aql:self.instances->select(i | i.eClass().name = 'TriggerInstance')")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:240")
                .defaultHeightExpression("aql:96")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.TOP_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:true")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.TRIGGER_INSTANCE_TEXT))
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(colorProvider.getColor(EdtColorPaletteProvider.TRIGGER_INSTANCE_BACKGROUND))
                        .borderColor(colorProvider.getColor(EdtColorPaletteProvider.TRIGGER_INSTANCE_BORDER))
                        .borderSize(1).build())
                .borderNodesDescriptions(borders.toArray(NodeDescription[]::new))
                .build();
    }

    private NodeDescription createDynamicTriggerInstanceNode() {
        List<NodeDescription> borders = List.of(
            createDynamicTriggerPortNode(DYNAMIC_TRIGGER_SENDER_PORT_NAME, "edtimplementation::DynamicTriggerEventSenderInstance", "DynamicTriggerEventSenderInstance", "aql:'S'", EdtColorPaletteProvider.SENDER_PORT_BACKGROUND),
            createDynamicTriggerPortNode(DYNAMIC_TRIGGER_RECEIVER_PORT_NAME, "edtimplementation::DynamicTriggerEventReceiverInstance", "DynamicTriggerEventReceiverInstance", "aql:'R'", EdtColorPaletteProvider.RECEIVER_PORT_BACKGROUND)
        );

        return builder.newNodeDescription()
                .name(DYNAMIC_TRIGGER_INSTANCE_NAME)
                .domainType("edtimplementation::DynamicTriggerInstance")
                .semanticCandidatesExpression("aql:self.instances->select(i | i.eClass().name = 'DynamicTriggerInstance')")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:260")
                .defaultHeightExpression("aql:110")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.TOP_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:true")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.DYNAMIC_TRIGGER_INSTANCE_TEXT))
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(colorProvider.getColor(EdtColorPaletteProvider.DYNAMIC_TRIGGER_INSTANCE_BACKGROUND))
                        .borderColor(colorProvider.getColor(EdtColorPaletteProvider.DYNAMIC_TRIGGER_INSTANCE_BORDER))
                        .borderSize(1).build())
                .borderNodesDescriptions(borders.toArray(NodeDescription[]::new))
                .build();
    }

    private NodeDescription createExternalNode() {
        List<NodeDescription> borders = List.of(
            builder.newNodeDescription()
                .name(EXTERNAL_OPERATION_PORT_NAME)
                .domainType("edtimplementation::OperationInstance") 
                .semanticCandidatesExpression("aql:Sequence{self}") 
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:24")
                .defaultHeightExpression("aql:24")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression(endpointPortLabelExpression())
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_TEXT))
                                .borderSize(0).build())
                        .build()) 
                .style(builder.newRectangularNodeStyleDescription()
                        .background(this.colorProvider.getColor(EdtColorPaletteProvider.RECEIVER_PORT_BACKGROUND))
                        .borderSize(1)
                        .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_BORDER))
                        .build())
                .build()
        );

        return builder.newNodeDescription()
                .name(EXTERNAL_NAME)
                .domainType("edtimplementation::ExternalSenderOperation") 
                .semanticCandidatesExpression("aql:self.ExternalSenders")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:150")
                .defaultHeightExpression("aql:48")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:true")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.EXTERNAL_TEXT))
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(colorProvider.getColor(EdtColorPaletteProvider.EXTERNAL_BACKGROUND))
                        .borderColor(colorProvider.getColor(EdtColorPaletteProvider.EXTERNAL_BORDER))
                        .borderSize(1)
                        .build())
                .borderNodesDescriptions(borders.toArray(NodeDescription[]::new))
                .build();
    }

    private NodeDescription createServiceNode() {
        var servicePort = createLinkedComponentPortNode(SERVICE_OPERATION_PORT_NAME, servicePortLabelExpression());

        return builder.newNodeDescription()
                .name(SERVICE_NAME)
                .domainType("edtimplementation::ServiceOfLinkedComponentDefinition")
                .semanticCandidatesExpression("aql:self.ComponentDefinitionServices")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:160")
                .defaultHeightExpression("aql:72")
                .keepAspectRatio(true)
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .fontSize(18)
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.SERVICE_TEXT))
                                .borderSize(0)
                                .build())
                        .build())
                .style(builder.newImageNodeStyleDescription()
                        .shape("/icons/component_service.png")
                        .borderColor(colorProvider.getColor("black"))
                        .borderSize(0)
                        .childrenLayoutStrategy(builder.newFreeFormLayoutStrategyDescription()
                                .onWestAtCreationBorderNodes(servicePort)
                                .build())
                        .build())
                .borderNodesDescriptions(servicePort)
                .build();
    }

    private NodeDescription createReferenceNode() {
        var referencePort = createLinkedComponentPortNode(REFERENCE_OPERATION_PORT_NAME, referencePortLabelExpression());

        return builder.newNodeDescription()
                .name(REFERENCE_NAME)
                .domainType("edtimplementation::ReferenceOfLinkedComponentDefinition")
                .semanticCandidatesExpression("aql:self.ComponentDefinitionReferences")
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:160")
                .defaultHeightExpression("aql:72")
                .keepAspectRatio(true)
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression("aql:self.name")
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .fontSize(18)
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.REFERENCE_TEXT))
                                .borderSize(0)
                                .build())
                        .build())
                .style(builder.newImageNodeStyleDescription()
                        .shape("/icons/component_reference.png")
                        .borderColor(colorProvider.getColor("black"))
                        .borderSize(0)
                        .childrenLayoutStrategy(builder.newFreeFormLayoutStrategyDescription()
                                .onWestAtCreationBorderNodes(referencePort)
                                .build())
                        .build())
                .borderNodesDescriptions(referencePort)
                .build();
    }

    private String endpointPortLabelExpression() {
        return "aql:"
                + "if self.eClass().name->includes('Writer') then 'W' "
                + "else if self.eClass().name->includes('Reader') or self.eClass().name->includes('Receiver') "
                + "or self.eClass().name->includes('Reference') or self.eClass().name->includes('Service') then 'R' "
                + "else if self.eClass().name->includes('Client') then 'C' "
                + "else if self.eClass().name->includes('Server') then 'SR' "
                + "else if self.eClass().name->includes('Sender') then 'S' "
                + "else '' endif endif endif endif endif";
    }

    private String operationsExpression() {
        return "aql:"
                + "if self.eClass().eAllStructuralFeatures.name->includes('operations') then self.operations "
                + "else if self.eClass().eAllStructuralFeatures.name->includes('Operations') then self.Operations "
                + "else Sequence{} endif endif";
    }

    private String servicePortLabelExpression() {
        return "aql:"
                + "if self.eClass().name->includes('RequestService') then 'S' "
                + "else if self.eClass().name->includes('VersionedDataService') then 'R' "
                + "else if self.eClass().name = 'EventDefinitionInstance' "
                + "and self.eClass().eAllStructuralFeatures.name->includes('SDOperationRef') "
                + "and self.SDOperationRef <> null "
                + "and self.SDOperationRef.eClass().name = 'EventReceived' then 'R' "
                + "else if self.eClass().name = 'EventDefinitionInstance' "
                + "and self.eClass().eAllStructuralFeatures.name->includes('SDOperationRef') "
                + "and self.SDOperationRef <> null "
                + "and self.SDOperationRef.eClass().name = 'EventSent' then 'S' "
                + "else '' endif endif endif endif";
    }

    private String referencePortLabelExpression() {
        return "aql:"
                + "if self.eClass().name->includes('RequestReference') then 'R' "
                + "else if self.eClass().name->includes('VersionedDataReference') then 'W' "
                + "else if self.eClass().name = 'EventDefinitionInstance' "
                + "and self.eClass().eAllStructuralFeatures.name->includes('SDOperationRef') "
                + "and self.SDOperationRef <> null "
                + "and self.SDOperationRef.eClass().name = 'EventReceived' then 'R' "
                + "else if self.eClass().name = 'EventDefinitionInstance' "
                + "and self.eClass().eAllStructuralFeatures.name->includes('SDOperationRef') "
                + "and self.SDOperationRef <> null "
                + "and self.SDOperationRef.eClass().name = 'EventSent' then 'S' "
                + "else '' endif endif endif endif";
    }

    private NodeDescription createPortNode(String name, String domainClass, String label, String bgColor) {
        return createPortNodeWithExpression(name, domainClass,
                "aql:(" + operationsExpression().replace("aql:", "") + ")->select(i | i.eClass().eAllSuperTypes.name->includes('" + domainClass.replace("edtimplementation::", "") + "') or i.eClass().name = '" + domainClass.replace("edtimplementation::", "") + "')",
                label, bgColor);
    }

    /**
     * DynamicTriggerInstance.Operations contains DynamicTriggerEventSenderInstance / DynamicTriggerEventReceiverInstance.
     * These types inherit directly from EventLinkSender/EventLinkReceiver, NOT from EventSenderInstance/EventReceiverInstance.
     * So we need a dedicated factory method that filters them by their own concrete class name.
     */
    private NodeDescription createDynamicTriggerPortNode(String name, String domainClass, String concreteClassName, String label, String bgColor) {
        return createPortNodeWithExpression(name, domainClass,
                "aql:(" + operationsExpression().replace("aql:", "") + ")->select(i | i.eClass().eAllSuperTypes.name->includes('" + concreteClassName + "') or i.eClass().name = '" + concreteClassName + "')",
                label, bgColor);
    }

    private NodeDescription createPortNodeWithExpression(String name, String domainClass, String semanticCandidates, String label, String bgColor) {
        return builder.newNodeDescription()
                .name(name)
                .domainType(domainClass)
                .semanticCandidatesExpression(semanticCandidates)
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression(label)
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_TEXT))
                                .borderSize(0).build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(this.colorProvider.getColor(bgColor))
                        .borderSize(1)
                        .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_BORDER))
                        .build())
                .build();
    }

    private NodeDescription createLinkedComponentPortNode(String name, String labelExpression) {
        return builder.newNodeDescription()
                .name(name)
                .domainType("edtimplementation::OperationInstance")
                .semanticCandidatesExpression(operationsExpression())
                .synchronizationPolicy(org.eclipse.sirius.components.view.diagram.SynchronizationPolicy.SYNCHRONIZED)
                .defaultWidthExpression("aql:30")
                .defaultHeightExpression("aql:30")
                .insideLabel(builder.newInsideLabelDescription()
                        .labelExpression(labelExpression)
                        .position(InsideLabelPosition.MIDDLE_CENTER)
                        .style(builder.newInsideLabelStyle()
                                .fontSize(18)
                                .bold(true)
                                .showIconExpression("aql:false")
                                .labelColor(this.colorProvider.getColor(EdtColorPaletteProvider.PORT_TEXT))
                                .borderSize(0)
                                .build())
                        .build())
                .style(builder.newRectangularNodeStyleDescription()
                        .background(this.colorProvider.getColor(EdtColorPaletteProvider.SENDER_PORT_BACKGROUND))
                        .borderSize(2)
                        .borderColor(this.colorProvider.getColor(EdtColorPaletteProvider.COMPONENT_BORDER))
                        .build())
                .build();
    }

    public void link(DiagramDescription diagramDescription, IViewDiagramElementFinder cache) {
        // By adding all nodes to the cache, we implicitly tell Sirius about the structure
        cache.getNodeDescriptions().forEach(diagramDescription.getNodeDescriptions()::add);
    }
}
