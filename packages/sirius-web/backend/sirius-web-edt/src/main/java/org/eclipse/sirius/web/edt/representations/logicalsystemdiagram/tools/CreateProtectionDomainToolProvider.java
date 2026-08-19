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
 * Provides the tool to create a new ProtectionDomain within a LogicalComputingNode.
 * The ProtectionDomain is linked to the node via the ProtectionDomainLink/executeOnComputingNode bidirectional reference.
 *
 * @author EDT Team
 */
public class CreateProtectionDomainToolProvider implements INodeToolProvider {

    @Override
    public NodeTool create(IViewDiagramElementFinder cache) {
        var platformNodeDescription = cache.getNodeDescription(LogicalPlatformNodeDescriptionProvider.NAME).orElse(null);
        var protectionDomainDescription = platformNodeDescription != null
                ? findProtectionDomainDescription(platformNodeDescription)
                : null;

        // Create the ProtectionDomain in the Deployment model and set the bidirectional reference
        var createInstance = new ViewBuilders().newCreateInstance()
                .typeName("edtdeployment::ProtectionDomain")
                .referenceName("protectionDomains")
                .variableName("newProtectionDomain")
                .children(
                        new DiagramBuilders().newCreateView()
                                .elementDescription(protectionDomainDescription)
                                .semanticElementExpression("aql:newProtectionDomain")
                                .parentViewExpression("aql:selectedNode")
                                .containmentKind(NodeContainmentKind.CHILD_NODE)
                                .build()
                )
                .build();

        // Set the name
        var setName = new ViewBuilders().newSetValue()
                .featureName("name")
                .valueExpression("aql:'ProtectionDomain_' + self.eContainer().protectionDomains->size()")
                .build();

        // Set the executeOnComputingNode reference (bidirectional link)
        var setExecuteOnNode = new ViewBuilders().newSetValue()
                .featureName("executeOnComputingNode")
                .valueExpression("aql:self.eContainer().eContainer()") // The LogicalComputingNode
                .build();

        createInstance.getChildren().add(setName);
        createInstance.getChildren().add(setExecuteOnNode);

        // Navigate to the Deployment container first
        var findDeployment = new ViewBuilders().newChangeContext()
                .expression("aql:self.eResource().allContents(edtdeployment::Deployment)->first()")
                .children(createInstance)
                .build();

        return new DiagramBuilders().newNodeTool()
                .name("New Protection Domain")
                .iconURLsExpression("aql:'/icons/edt/ProtectionDomain.svg'")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(findDeployment)
                                .build()
                )
                .build();
    }

    private org.eclipse.sirius.components.view.diagram.NodeDescription findProtectionDomainDescription(
            org.eclipse.sirius.components.view.diagram.NodeDescription platformNode) {
        // Navigate: Platform -> LogicalNode -> ProtectionDomain
        for (var logicalNode : platformNode.getChildrenDescriptions()) {
            if (LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME.equals(logicalNode.getName())) {
                for (var protectionDomain : logicalNode.getChildrenDescriptions()) {
                    if (LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME.equals(protectionDomain.getName())) {
                        return protectionDomain;
                    }
                }
            }
        }
        return null;
    }
}
