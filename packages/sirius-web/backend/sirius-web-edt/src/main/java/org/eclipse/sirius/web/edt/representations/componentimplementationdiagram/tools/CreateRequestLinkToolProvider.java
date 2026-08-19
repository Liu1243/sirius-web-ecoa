package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.RequestLinkEdgeDescriptionProvider;

public class CreateRequestLinkToolProvider {
    private static final String IS_REQUEST_CLIENT_EXPRESSION =
            "aql:resolvedClient <> null and (resolvedClient.eClass().eAllSuperTypes.name->includes('RequestClientInstance') or resolvedClient.eClass().name = 'RequestClientInstance')";

    private static final String IS_REQUEST_SERVICE_EXPRESSION =
            "aql:resolvedClient <> null and (resolvedClient.eClass().eAllSuperTypes.name->includes('RequestServiceInstance') or resolvedClient.eClass().name = 'RequestServiceInstance')";

    private static final String IS_REQUEST_SERVER_EXPRESSION =
            "aql:resolvedServer <> null and (resolvedServer.eClass().eAllSuperTypes.name->includes('RequestServerInstance') or resolvedServer.eClass().name = 'RequestServerInstance')";

    private static final String IS_REQUEST_REFERENCE_EXPRESSION =
            "aql:resolvedServer <> null and (resolvedServer.eClass().eAllSuperTypes.name->includes('RequestReferenceInstance') or resolvedServer.eClass().name = 'RequestReferenceInstance')";

    private static final String RESOLVE_CLIENT_EXPRESSION =
            "aql:if semanticEdgeSource.eClass().name = 'RequestClientInstance' then semanticEdgeSource "
                    + "else if semanticEdgeSource.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeSource.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('RequestClientInstance') or i.eClass().name = 'RequestClientInstance')->first() "
                    + "else semanticEdgeSource endif endif";

    private static final String RESOLVE_SERVER_EXPRESSION =
            "aql:if semanticEdgeTarget.eClass().name = 'RequestServerInstance' then semanticEdgeTarget "
                    + "else if semanticEdgeTarget.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeTarget.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('RequestServerInstance') or i.eClass().name = 'RequestServerInstance')->first() "
                    + "else semanticEdgeTarget endif endif";

    public EdgeTool create(IViewDiagramElementFinder cache) {
        var edgeDescription = cache.getEdgeDescription(RequestLinkEdgeDescriptionProvider.NAME).orElse(null);

        var createModuleToModuleLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::RequestLinkActivatingActivatableFifo")
                .referenceName("OperationLinks")
                .variableName("newRequestLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("client")
                                .valueExpression(RESOLVE_CLIENT_EXPRESSION)
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("server")
                                .valueExpression(RESOLVE_SERVER_EXPRESSION)
                                .build()
                )
                .build();

        var createServiceToModuleLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::RequestLinkActivatableFifo")
                .referenceName("OperationLinks")
                .variableName("newRequestLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("client")
                                .valueExpression("aql:resolvedClient")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("server")
                                .valueExpression("aql:resolvedServer")
                                .build()
                )
                .build();

        var createModuleToReferenceLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::RequestLinkActivatingToReferenceOperation")
                .referenceName("OperationLinks")
                .variableName("newRequestLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("client")
                                .valueExpression("aql:resolvedClient")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("server")
                                .valueExpression("aql:resolvedServer")
                                .build()
                )
                .build();

        var requestLinkCreation = new ViewBuilders().newLet()
                .variableName("resolvedClient")
                .valueExpression(RESOLVE_CLIENT_EXPRESSION)
                .children(
                        new ViewBuilders().newLet()
                                .variableName("resolvedServer")
                                .valueExpression(RESOLVE_SERVER_EXPRESSION)
                                .children(
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_REQUEST_REFERENCE_EXPRESSION)
                                                .children(
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_REQUEST_CLIENT_EXPRESSION)
                                                                .children(createModuleToReferenceLink)
                                                                .build())
                                                .build(),
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_REQUEST_SERVER_EXPRESSION)
                                                .children(
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_REQUEST_SERVICE_EXPRESSION)
                                                                .children(createServiceToModuleLink)
                                                                .build(),
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_REQUEST_CLIENT_EXPRESSION)
                                                                .children(createModuleToModuleLink)
                                                                .build())
                                                .build())
                                .build())
                .build();

        var changeContext = new ViewBuilders().newChangeContext()
                // Navigate to ComponentImplementation regardless of source nesting level (typically depth 1 or 2)
                .expression("aql:if semanticEdgeSource.eContainer().eClass().name = 'ComponentImplementation' then semanticEdgeSource.eContainer() else semanticEdgeSource.eContainer().eContainer() endif")
                .children(requestLinkCreation)
                .build();

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
                .name("Create new RequestLink")
                .iconURLsExpression("aql:'/icons/24x24/RequestLink24.png'")
                .targetElementDescriptions(targetDescriptions.toArray(org.eclipse.sirius.components.view.diagram.NodeDescription[]::new))
                .body(changeContext)
                .build();
    }
}
