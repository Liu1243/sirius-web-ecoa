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
 * Provides tools to create DeployedModuleInstance and DeployedTriggerInstance within a ProtectionDomain.
 *
 * @author EDT Team
 */
public class CreateDeployedInstanceToolProvider implements INodeToolProvider {

    private final boolean createModuleInstance;

    /**
     * Constructor.
     * @param createModuleInstance true to create DeployedModuleInstance, false to create DeployedTriggerInstance
     */
    public CreateDeployedInstanceToolProvider(boolean createModuleInstance) {
        this.createModuleInstance = createModuleInstance;
    }

    @Override
    public NodeTool create(IViewDiagramElementFinder cache) {
        var platformNodeDescription = cache.getNodeDescription(LogicalPlatformNodeDescriptionProvider.NAME).orElse(null);
        var instanceDescription = platformNodeDescription != null
                ? findDeployedInstanceDescription(platformNodeDescription)
                : null;

        String typeName = this.createModuleInstance 
                ? "edtdeployment::DeployedModuleInstance" 
                : "edtdeployment::DeployedTriggerInstance";
        String referenceName = this.createModuleInstance 
                ? "deployedModuleInstances" 
                : "deployedTriggerInstances";
        String toolName = this.createModuleInstance 
                ? "New Deployed Module Instance" 
                : "New Deployed Trigger Instance";
        String iconPath = this.createModuleInstance 
                ? "/icons/edt/DeployedModuleInstance.svg" 
                : "/icons/edt/DeployedTriggerInstance.svg";
        String priorityFeature = this.createModuleInstance 
                ? "modulePriority" 
                : "triggerPriority";

        var createInstance = new ViewBuilders().newCreateInstance()
                .typeName(typeName)
                .referenceName(referenceName)
                .variableName("newInstance")
                .children(
                        new DiagramBuilders().newCreateView()
                                .elementDescription(instanceDescription)
                                .semanticElementExpression("aql:newInstance")
                                .parentViewExpression("aql:selectedNode")
                                .containmentKind(NodeContainmentKind.CHILD_NODE)
                                .build()
                )
                .build();

        // Set default priority
        var setPriority = new ViewBuilders().newSetValue()
                .featureName(priorityFeature)
                .valueExpression("aql:0")
                .build();

        createInstance.getChildren().add(setPriority);

        return new DiagramBuilders().newNodeTool()
                .name(toolName)
                .iconURLsExpression("aql:'" + iconPath + "'")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(createInstance)
                                .build()
                )
                .build();
    }

    private org.eclipse.sirius.components.view.diagram.NodeDescription findDeployedInstanceDescription(
            org.eclipse.sirius.components.view.diagram.NodeDescription platformNode) {
        String targetName = this.createModuleInstance 
                ? LogicalPlatformNodeDescriptionProvider.DEPLOYED_MODULE_INSTANCE_NAME
                : LogicalPlatformNodeDescriptionProvider.DEPLOYED_TRIGGER_INSTANCE_NAME;
        
        // Navigate: Platform -> LogicalNode -> ProtectionDomain -> DeployedInstance
        for (var logicalNode : platformNode.getChildrenDescriptions()) {
            if (LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME.equals(logicalNode.getName())) {
                for (var protectionDomain : logicalNode.getChildrenDescriptions()) {
                    if (LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME.equals(protectionDomain.getName())) {
                        for (var deployedInstance : protectionDomain.getChildrenDescriptions()) {
                            if (targetName.equals(deployedInstance.getName())) {
                                return deployedInstance;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
