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
package org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.tools;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.INodeToolProvider;
import org.eclipse.sirius.components.view.diagram.NodeContainmentKind;
import org.eclipse.sirius.components.view.diagram.NodeTool;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.nodedescriptions.LogicalPlatformNodeDescriptionProvider;

/**
 * Provides the tool to create a new LogicalComputingNode within a LogicalComputingPlatform.
 *
 * @author EDT Team
 */
public class CreateNodeToolProvider implements INodeToolProvider {

    @Override
    public NodeTool create(IViewDiagramElementFinder cache) {
        var platformNodeDescription = cache.getNodeDescription(LogicalPlatformNodeDescriptionProvider.NAME).orElse(null);
        var logicalNodeDescription = platformNodeDescription != null
                ? platformNodeDescription.getChildrenDescriptions().stream()
                        .filter(child -> LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME.equals(child.getName()))
                        .findFirst()
                        .orElse(null)
                : null;

        var createInstance = new ViewBuilders().newCreateInstance()
                .typeName("edtlogical::LogicalComputingNode")
                .referenceName("logicalComputingNodes")
                .variableName("newNode")
                .children(
                        new DiagramBuilders().newCreateView()
                                .elementDescription(logicalNodeDescription)
                                .semanticElementExpression("aql:newNode")
                                .parentViewExpression("aql:selectedNode")
                                .containmentKind(NodeContainmentKind.CHILD_NODE)
                                .build()
                )
                .build();

        var setId = new ViewBuilders().newSetValue()
                .featureName("id")
                .valueExpression("aql:'Node_' + self.logicalComputingNodes->size()")
                .build();

        createInstance.getChildren().add(setId);

        return new DiagramBuilders().newNodeTool()
                .name("New Logical Computing Node")
                .iconURLsExpression("aql:'/icons/edt/LogicalComputingNode.svg'")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(createInstance)
                                .build()
                )
                .build();
    }
}
