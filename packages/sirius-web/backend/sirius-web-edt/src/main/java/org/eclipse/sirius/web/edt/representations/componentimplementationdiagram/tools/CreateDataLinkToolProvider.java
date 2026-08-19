package org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.edgedescriptions.DataLinkEdgeDescriptionProvider;

public class CreateDataLinkToolProvider {
    private static final String IS_DATA_WRITER_EXPRESSION =
            "aql:resolvedWriter <> null and (resolvedWriter.eClass().eAllSuperTypes.name->includes('DataWriterInstance') or resolvedWriter.eClass().name = 'DataWriterInstance')";

    private static final String IS_DATA_READER_EXPRESSION =
            "aql:resolvedReader <> null and (resolvedReader.eClass().eAllSuperTypes.name->includes('DataReaderInstance') or resolvedReader.eClass().name = 'DataReaderInstance')";

    private static final String IS_DATA_SERVICE_EXPRESSION =
            "aql:resolvedReader <> null and (resolvedReader.eClass().eAllSuperTypes.name->includes('VersionedDataServiceInstance') or resolvedReader.eClass().name = 'VersionedDataServiceInstance')";

    private static final String RESOLVE_WRITER_EXPRESSION =
            "aql:if semanticEdgeSource.eClass().name = 'DataWriterInstance' then semanticEdgeSource "
                    + "else if semanticEdgeSource.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeSource.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('DataWriterInstance') or i.eClass().name = 'DataWriterInstance')->first() "
                    + "else semanticEdgeSource endif endif";

    private static final String RESOLVE_READER_EXPRESSION =
            "aql:if semanticEdgeTarget.eClass().name = 'DataReaderInstance' then semanticEdgeTarget "
                    + "else if semanticEdgeTarget.eClass().eAllStructuralFeatures.name->includes('Operations') "
                    + "then semanticEdgeTarget.Operations->select(i | i.eClass().eAllSuperTypes.name->includes('DataReaderInstance') or i.eClass().name = 'DataReaderInstance')->first() "
                    + "else semanticEdgeTarget endif endif";

    public EdgeTool create(IViewDiagramElementFinder cache) {
        var edgeDescription = cache.getEdgeDescription(DataLinkEdgeDescriptionProvider.NAME).orElse(null);

        var createModuleToModuleLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::DataLinkActivatableFifo")
                .referenceName("OperationLinks")
                .variableName("newDataLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("writer")
                                .valueExpression(RESOLVE_WRITER_EXPRESSION)
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("reader")
                                .valueExpression(RESOLVE_READER_EXPRESSION)
                                .build()
                )
                .build();

        var createModuleToServiceLink = new ViewBuilders().newCreateInstance()
                .typeName("edtimplementation::DataLinkToServiceOperation")
                .referenceName("OperationLinks")
                .variableName("newDataLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("writer")
                                .valueExpression("aql:resolvedWriter")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("reader")
                                .valueExpression("aql:resolvedReader")
                                .build()
                )
                .build();

        var dataLinkCreation = new ViewBuilders().newLet()
                .variableName("resolvedWriter")
                .valueExpression(RESOLVE_WRITER_EXPRESSION)
                .children(
                        new ViewBuilders().newLet()
                                .variableName("resolvedReader")
                                .valueExpression(RESOLVE_READER_EXPRESSION)
                                .children(
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_DATA_SERVICE_EXPRESSION)
                                                .children(
                                                        new ViewBuilders().newIf()
                                                                .conditionExpression(IS_DATA_WRITER_EXPRESSION)
                                                                .children(createModuleToServiceLink)
                                                                .build())
                                                .build(),
                                        new ViewBuilders().newIf()
                                                .conditionExpression(IS_DATA_READER_EXPRESSION)
                                                .children(createModuleToModuleLink)
                                                .build())
                                .build())
                .build();

        var changeContext = new ViewBuilders().newChangeContext()
                // Navigate to ComponentImplementation regardless of source nesting level (typically depth 1 or 2)
                .expression("aql:if semanticEdgeSource.eContainer().eClass().name = 'ComponentImplementation' then semanticEdgeSource.eContainer() else semanticEdgeSource.eContainer().eContainer() endif")
                .children(dataLinkCreation)
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
                .name("Create new DataLink")
                .iconURLsExpression("aql:'/icons/24x24/DataLink24.png'")
                .targetElementDescriptions(targetDescriptions.toArray(org.eclipse.sirius.components.view.diagram.NodeDescription[]::new))
                .body(changeContext)
                .build();
    }
}
