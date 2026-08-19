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
package org.eclipse.sirius.web.edt.representations.compositediagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.INodeToolProvider;
import org.eclipse.sirius.components.view.diagram.NodeContainmentKind;
import org.eclipse.sirius.components.view.diagram.NodeTool;
import org.eclipse.sirius.web.edt.representations.compositediagram.nodedescriptions.ComponentNodeDescriptionProvider;

/**
 * Provides the Create Component tool for EDT CompositeDiagram.
 *
 * @author EDT Team
 */
public class CreateComponentToolProvider implements INodeToolProvider {

    @Override
    public NodeTool create(IViewDiagramElementFinder cache) {
        var componentNodeDescription = cache.getNodeDescription(ComponentNodeDescriptionProvider.NAME).orElse(null);

        var createInstance = new ViewBuilders().newCreateInstance()
                .typeName("edtproject::Component")
                .referenceName("Components")
                .variableName("newComponent")
                .children(
                        new DiagramBuilders().newCreateView()
                                .elementDescription(componentNodeDescription)
                                .semanticElementExpression("aql:newComponent")
                                .parentViewExpression("aql:selectedNode")
                                .containmentKind(NodeContainmentKind.CHILD_NODE)
                                .build()
                )
                .build();

        var setName = new ViewBuilders().newSetValue()
                .featureName("Name")
                .valueExpression("aql:'Component' + self.eContainer().Components->size()")
                .build();
        

        createInstance.getChildren().add(setName);

        
        return new DiagramBuilders().newNodeTool()
                .name("Create Component")
                .iconURLsExpression("aql:'/icons/component.png'")
                .body(
                         new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(createInstance)
                                .build()
                )
                .build();
    }
}
