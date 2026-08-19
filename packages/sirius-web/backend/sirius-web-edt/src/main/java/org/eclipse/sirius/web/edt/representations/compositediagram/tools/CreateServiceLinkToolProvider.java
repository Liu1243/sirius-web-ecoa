/*******************************************************************************
 * Copyright (c) 2025 Obeo.
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
package org.eclipse.sirius.web.edt.representations.compositediagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.diagram.EdgeTool;
import org.eclipse.sirius.web.edt.representations.compositediagram.edgedescriptions.ServiceLinkEdgeDescriptionProvider;

/**
 * Provides the Create ServiceLink tool for EDT CompositeDiagram.
 *
 * @author EDT Team
 */
public class CreateServiceLinkToolProvider {

    public EdgeTool create(IViewDiagramElementFinder cache) {
        var edgeDescription = cache.getEdgeDescription(ServiceLinkEdgeDescriptionProvider.NAME).orElse(null);

        var createInstance = new ViewBuilders().newCreateInstance()
                .typeName("edtproject::ServiceLink")
                // EDTProject2.ecore defines this containment reference on Composite as 'ServiceLinks' (capital S, L)
                .referenceName("ServiceLinks")
                .variableName("newServiceLink")
                .children(
                        new ViewBuilders().newSetValue()
                                .featureName("source")
                                .valueExpression("aql:source")
                                .build(),
                        new ViewBuilders().newSetValue()
                                .featureName("target")
                                .valueExpression("aql:target")
                                .build(),
                        new DiagramBuilders().newCreateView()
                                .elementDescription(edgeDescription)
                                .semanticElementExpression("aql:newServiceLink")
                                .parentViewExpression("aql:sourceView.eContainer()")
                                .build()
                )
                .build();

        return new DiagramBuilders().newEdgeTool()
                .name("Create Service Link")
                .iconURLsExpression("aql:'/icons/edt/ServiceLink.svg'")
                .targetElementDescriptions(edgeDescription)
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:source.eContainer().eContainer()")
                                .children(createInstance)
                                .build()
                )
                .build();
    }
}
