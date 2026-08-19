package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.EventLinkEdgeDescriptionProvider;

public class CreateEventLinkToolProvider {
    private static final String IS_SENDER_EXPRESSION =
            "aql:resolvedSender <> null and (resolvedSender.eClass().eAllSuperTypes.name->includes('EventLinkSender') or resolvedSender.eClass().name = 'EventLinkSender')";

    private static final String IS_RECEIVER_MODULE_EXPRESSION =
            "aql:resolvedReceiver <> null and (resolvedReceiver.eClass().eAllSuperTypes.name->includes('EventReceiverInstance') or resolvedReceiver.eClass().name = 'EventReceiverInstance')";

    private static final String IS_RECEIVER_DEFINITION_EXPRESSION =
            "aql:resolvedReceiver <> null and (resolvedReceiver.eClass().eAllSuperTypes.name->includes('EventDefinitionInstance') or resolvedReceiver.eClass().name = 'EventDefinitionInstance')";

    private static final String RESOLVE_SENDER_EXPRESSION =
            "aql:if semanticEdgeSource.eClass().eAllSuperTypes.name->includes('EventLinkSender') or semanticEdgeSource.eClass().name = 'EventLinkSender' "
                    + "or semanticEdgeSource.eClass().name = 'EventSenderInstance' or semanticEdgeSource.eClass().name = 'DynamicTriggerEventSenderInstance' "
                    + "then semanticEdgeSource "
                    + "else if semanticEdgeSource.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeSource.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('EventLinkSender') or i.eClass().name = 'EventSenderInstance' or i.eClass().name = 'DynamicTriggerEventSenderInstance')->first() "
                    + "else semanticEdgeSource endif endif";

    private static final String RESOLVE_RECEIVER_EXPRESSION =
            "aql:if semanticEdgeTarget.eClass().eAllSuperTypes.name->includes('EventLinkReceiver') or semanticEdgeTarget.eClass().name = 'EventLinkReceiver' "
                    + "or semanticEdgeTarget.eClass().name = 'EventReceiverInstance' or semanticEdgeTarget.eClass().name = 'DynamicTriggerEventReceiverInstance' "
                    + "then semanticEdgeTarget "
                    + "else if semanticEdgeTarget.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeTarget.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('EventLinkReceiver') or i.eClass().name = 'EventReceiverInstance' or i.eClass().name = 'DynamicTriggerEventReceiverInstance')->first() "
                    + "else semanticEdgeTarget endif endif";

    public EdgeTool create(IViewDiagramElementFinder cache) {
        var edgeDescription = cache.getEdgeDescription(EventLinkEdgeDescriptionProvider.NAME).orElse(null);

        var createModuleToModuleLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::EventLinkActivatableFifo")
                .referenceName("OperationLinks")
                .variableName("newEventLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("sender")
                                .valueExpression("aql:resolvedSender")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("receiver")
                                .valueExpression("aql:resolvedReceiver")
                                .build()
                )
                .build();

        var createModuleToDefinitionLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::EventLinkToDefinitionOperation")
                .referenceName("OperationLinks")
                .variableName("newEventLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("sender")
                                .valueExpression("aql:resolvedSender")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("receiver")
                                .valueExpression("aql:resolvedReceiver")
                                .build()
                )
                .build();

        var eventLinkCreation = new ViewBuilders().newLet()
                .variableName("resolvedSender")
                .valueExpression(RESOLVE_SENDER_EXPRESSION)
                .children(
                        new ViewBuilders().newLet()
                                .variableName("resolvedReceiver")
                                .valueExpression(RESOLVE_RECEIVER_EXPRESSION)
                                .children(
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_RECEIVER_DEFINITION_EXPRESSION)
                                                .children(
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_SENDER_EXPRESSION)
                                                                .children(createModuleToDefinitionLink)
                                                                .build())
                                                .build(),
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_RECEIVER_MODULE_EXPRESSION)
                                                .children(
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_SENDER_EXPRESSION)
                                                                .children(createModuleToModuleLink)
                                                                .build())
                                                .build())
                                .build())
                .build();

        var changeContext = new ViewBuilders().newChangeContext()
                // Navigate to ComponentImplementation taking into account whether the source port is directly under ComponentImplementation (depth 1) or in a Module/Trigger/etc (depth 2)
                .expression("aql:if semanticEdgeSource.eContainer().eClass().name = 'ComponentImplementation' then semanticEdgeSource.eContainer() else semanticEdgeSource.eContainer().eContainer() endif")
                .children(eventLinkCreation)
                .build();

        // Edge tool requires proper target element descriptions matching the permissible start/end graph descriptions
        var targetDescriptions = new java.util.LinkedHashSet<org.eclipse.sirius.components.view.diagram.NodeDescription>();
        if (edgeDescription != null) {
            edgeDescription.getSourceDescriptions().stream()
                    .filter(org.eclipse.sirius.components.view.diagram.NodeDescription.class::isInstance)
                    .map(org.eclipse.sirius.components.view.diagram.NodeDescription.class::cast)
                    .forEach(targetDescriptions::add);
            edgeDescription.getTargetDescriptions().stream()
                    .filter(org.eclipse.sirius.components.view.diagram.NodeDescription.class::isInstance)
                    .map(org.eclipse.sirius.components.view.diagram.NodeDescription.class::cast)
                    .forEach(targetDescriptions::add);
        }

        return new DiagramBuilders().newEdgeTool()
                .name("Create new EventLink")
                .iconURLsExpression("aql:'/icons/24x24/EventLink24.png'")
                .targetElementDescriptions(targetDescriptions.toArray(org.eclipse.sirius.components.view.diagram.NodeDescription[]::new))
                .body(changeContext)
                .build();
    }
}
