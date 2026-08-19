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
package org.eclipse.sirius.web.edt.views.explorer;

import edtimplementation.*;
import edtinterface.ServiceDefinition;
import edtproject.*;
import edttype.*;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.application.views.explorer.services.api.IExplorerTreeItemAlteredContentProvider;
import org.springframework.stereotype.Service;
import temp.InsertionPolicies;

import java.util.ArrayList;
import java.util.List;


/**
 * Provides altered tree content for EDT project to organize elements
 * and flatten the Steps hierarchy. This implementation uses virtual grouping nodes
 * to organize types under Step0.
 *
 * @author EDT Team
 */
@Service
public class EdtExplorerTreeItemAlteredContentProvider implements IExplorerTreeItemAlteredContentProvider {

    private final IIdentityService identityService;
    private final EdtExplorerServices edtExplorerServices;

    public EdtExplorerTreeItemAlteredContentProvider(IIdentityService identityService, EdtExplorerServices edtExplorerServices) {
        this.identityService = identityService;
        this.edtExplorerServices = edtExplorerServices;
    }

    @Override
    public boolean canHandle(Object object, List<String> activeFilterIds) {
        return object instanceof Steps
                || object instanceof Step0
                || object instanceof Step1
                || object instanceof Step2
                || object instanceof Step3
                || object instanceof Step4
                || object instanceof Step5
                || object instanceof ComponentImplementation
                || object instanceof ModuleType
                || object instanceof InsertionPolicies
                || object instanceof ServiceDefinition
                || object instanceof EdtVirtualGroupNode;
    }

    @Override
    public List<Object> apply(List<Object> computedChildren, VariableManager variableManager) {
        // If computedChildren is empty, it means the node is not expanded (not in expandedIds),
        // so we should return empty list to support collapse behavior.
        if (computedChildren.isEmpty()) {
            return computedChildren;
        }

        Object self = variableManager.get(VariableManager.SELF, Object.class).orElse(null);
        List<Object> result = computedChildren;

        if (self instanceof Steps steps) {
            result = this.getStepsChildren(steps);
        } else if (self instanceof Step0 step0) {
            result = this.getStep0Children(step0);
        } else if (self instanceof Step1 step1) {
            result = this.getStep1Children(step1);
        } else if (self instanceof Step2 step2) {
            result = this.getStep2Children(step2);
        } else if (self instanceof Step3 step3) {
            result = this.getStep3Children(step3);
        } else if (self instanceof Step4 step4) {
            result = this.getStep4Children(step4);
        } else if (self instanceof ComponentImplementation componentImplementation) {
            result = this.getComponentImplementationChildren(componentImplementation);
        } else if (self instanceof ModuleType moduleType) {
            result = this.getModuleTypeChildren(moduleType);
        } else if (self instanceof InsertionPolicies insertionPolicies) {
            result = this.getInsertionPoliciesChildren(insertionPolicies, computedChildren);
        } else if (self instanceof Step5 step5) {
            result = this.getStep5Children(step5);
        } else if (self instanceof ServiceDefinition serviceDefinition) {
            result = this.getServiceDefinitionChildren(serviceDefinition, computedChildren);
        } else if (self instanceof EdtVirtualGroupNode virtualGroupNode) {
            result = this.getVirtualGroupChildren(virtualGroupNode);
        }

        return result;
    }

    /**
     * Get children for Steps node - returns Step0-Step5 directly.
     * This effectively removes the Steps folder from the tree.
     */
    private List<Object> getStepsChildren(Steps steps) {
        List<Object> children = new ArrayList<>();

        if (steps.getStep0() != null) {
            children.add(steps.getStep0());
        }
        if (steps.getStep1() != null) {
            children.add(steps.getStep1());
        }
        if (steps.getStep2() != null) {
            children.add(steps.getStep2());
        }
        if (steps.getStep3() != null) {
            children.add(steps.getStep3());
        }
        if (steps.getStep4() != null) {
            children.add(steps.getStep4());
        }
        if (steps.getStep5() != null) {
            children.add(steps.getStep5());
        }

        return children;
    }

    /**
     * Get children for Step0 node - returns virtual group nodes for Basic Types and ECOA Predefined Types.
     */
    private List<Object> getStep0Children(Step0 step0) {
        List<Object> children = new ArrayList<>();
        String step0Id = this.identityService.getId(step0);

        // Create virtual group for Basic Types
        List<Object> basicTypes = new ArrayList<>(step0.getBasicTypes());
        if (!basicTypes.isEmpty()) {
            EdtVirtualGroupNode basicTypesGroup = new EdtVirtualGroupNode(
                    step0Id + EdtVirtualGroupNode.BASIC_TYPES_ID_SUFFIX,
                    EdtVirtualGroupNode.BASIC_TYPES_LABEL,
                    step0,
                    basicTypes
            );
            this.edtExplorerServices.registerVirtualNode(basicTypesGroup);
            children.add(basicTypesGroup);
        }

        // Create virtual group for ECOA Predefined Types
        List<Object> predefinedTypes = new ArrayList<>(step0.getEcoaPredefinedTypes());
        if (!predefinedTypes.isEmpty()) {
            
            // Sub-groups for Predefined Types
            List<Object> recordTypes = new ArrayList<>();
            List<Object> arrayTypes = new ArrayList<>();
            List<Object> simpleTypes = new ArrayList<>();
            List<Object> enumTypes = new ArrayList<>();

            for (Object obj : predefinedTypes) {
                if (obj instanceof RecordPredefined) {
                    recordTypes.add(obj);
                } else if (obj instanceof ArrayPredefined) {
                    arrayTypes.add(obj);
                } else if (obj instanceof SimplePredefined) {
                    simpleTypes.add(obj);
                } else if (obj instanceof EnumPredefined) {
                    enumTypes.add(obj);
                }
            }

            List<Object> predefinedTypesGroupChildren = new ArrayList<>();
            String predefinedGroupId = step0Id + EdtVirtualGroupNode.ECOA_PREDEFINED_TYPES_ID_SUFFIX;
            
            // Record Group
            if (!recordTypes.isEmpty()) {
                EdtVirtualGroupNode recordGroup = new EdtVirtualGroupNode(
                        step0Id + EdtVirtualGroupNode.RECORD_ID_SUFFIX,
                        EdtVirtualGroupNode.RECORD_LABEL,
                        step0, // Should be predefinedTypesGroup ideally
                        recordTypes,
                        EdtVirtualGroupNode.RECORD_CHILD_TYPE
                );
                this.edtExplorerServices.registerVirtualNode(recordGroup);
                predefinedTypesGroupChildren.add(recordGroup);
            }

            // Array Group
            if (!arrayTypes.isEmpty()) {
                EdtVirtualGroupNode arrayGroup = new EdtVirtualGroupNode(
                        step0Id + EdtVirtualGroupNode.ARRAY_ID_SUFFIX,
                        EdtVirtualGroupNode.ARRAY_LABEL,
                        step0,
                        arrayTypes,
                        EdtVirtualGroupNode.ARRAY_CHILD_TYPE
                );
                this.edtExplorerServices.registerVirtualNode(arrayGroup);
                predefinedTypesGroupChildren.add(arrayGroup);
            }

            // Simple Group
            if (!simpleTypes.isEmpty()) {
                EdtVirtualGroupNode simpleGroup = new EdtVirtualGroupNode(
                        step0Id + EdtVirtualGroupNode.SIMPLE_ID_SUFFIX,
                        EdtVirtualGroupNode.SIMPLE_LABEL,
                        step0,
                        simpleTypes,
                        EdtVirtualGroupNode.SIMPLE_CHILD_TYPE
                );
                this.edtExplorerServices.registerVirtualNode(simpleGroup);
                predefinedTypesGroupChildren.add(simpleGroup);
            }

            // Enum Group
            if (!enumTypes.isEmpty()) {
                 EdtVirtualGroupNode enumGroup = new EdtVirtualGroupNode(
                        step0Id + EdtVirtualGroupNode.ENUM_ID_SUFFIX,
                        EdtVirtualGroupNode.ENUM_LABEL,
                        step0,
                        enumTypes,
                        EdtVirtualGroupNode.ENUM_CHILD_TYPE
                );
                this.edtExplorerServices.registerVirtualNode(enumGroup);
                predefinedTypesGroupChildren.add(enumGroup);
            }

            // Create the main group containing subgroups
            if (!predefinedTypesGroupChildren.isEmpty()) {
                EdtVirtualGroupNode predefinedTypesGroup = new EdtVirtualGroupNode(
                        predefinedGroupId,
                        EdtVirtualGroupNode.ECOA_PREDEFINED_TYPES_LABEL,
                        step0,
                        predefinedTypesGroupChildren
                );
                this.edtExplorerServices.registerVirtualNode(predefinedTypesGroup);
                children.add(predefinedTypesGroup);
            }
        }

        // Add Libraries (Types)
        if (step0.getTypes() != null) {
            children.addAll(step0.getTypes());
        }

        return children;
    }

    /**
     * Get children for virtual group node - returns the stored children.
     */
    private List<Object> getVirtualGroupChildren(EdtVirtualGroupNode virtualGroupNode) {
        return new ArrayList<>(virtualGroupNode.children());
    }

    /**
     * Get children for Step1 node - returns Services in a virtual group folder.
     */
    private List<Object> getStep1Children(Step1 step1) {
        List<Object> children = new ArrayList<>();
        String step1Id = this.identityService.getId(step1);

        List<Object> services = new ArrayList<>(step1.getServices());
        EdtVirtualGroupNode servicesGroup = new EdtVirtualGroupNode(
                step1Id + EdtVirtualGroupNode.SERVICES_ID_SUFFIX,
                EdtVirtualGroupNode.SERVICES_LABEL,
                step1,
                services,
                EdtVirtualGroupNode.SERVICES_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(servicesGroup);
        children.add(servicesGroup);

        return children;
    }

    /**
     * Get children for Step2 node - returns ComponentDefinitions in a virtual group folder.
     */
    private List<Object> getStep2Children(Step2 step2) {
        List<Object> children = new ArrayList<>();
        String step2Id = this.identityService.getId(step2);

        List<Object> componentDefinitions = new ArrayList<>(step2.getComponentDefinitions());
        EdtVirtualGroupNode componentDefsGroup = new EdtVirtualGroupNode(
                step2Id + EdtVirtualGroupNode.COMPONENT_DEFINITIONS_ID_SUFFIX,
                EdtVirtualGroupNode.COMPONENT_DEFINITIONS_LABEL,
                step2,
                componentDefinitions,
                EdtVirtualGroupNode.COMPONENT_DEFINITIONS_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(componentDefsGroup);
        children.add(componentDefsGroup);

        return children;
    }

    /**
     * Get children for Step3 node - shows the Composite directly (at most one exists).
     */
    private List<Object> getStep3Children(Step3 step3) {
        List<Object> children = new ArrayList<>();
        if (step3.getInitialAssembly() != null) {
            children.add(step3.getInitialAssembly());
        }
        return children;
    }

    /**
     * Get children for Step4 node - returns ComponentImplementations directly.
     */
    private List<Object> getStep4Children(Step4 step4) {
        return new ArrayList<>(step4.getComponentImplementations());
    }

    /**
     * Get children for ComponentImplementation node.
     * Creates virtual group nodes for ModuleTypes, ModuleImplementations,
     * ModuleInstances, TriggerInstances, DynamicTriggerInstances,
     * ExternalOperations, and Service QoS.
     */
    private List<Object> getComponentImplementationChildren(ComponentImplementation ci) {
        List<Object> children = new ArrayList<>();
        String ciId = this.identityService.getId(ci);

        // ModuleTypes group
        List<Object> moduleTypes = new ArrayList<>(ci.getModuleTypes());
        EdtVirtualGroupNode moduleTypesGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.MODULE_TYPES_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_TYPES_LABEL,
                ci,
                moduleTypes,
                EdtVirtualGroupNode.MODULE_TYPES_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(moduleTypesGroup);
        children.add(moduleTypesGroup);

        // ModuleImplementations group
        List<Object> moduleImplementations = new ArrayList<>(ci.getModuleImplementations());
        EdtVirtualGroupNode moduleImplGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.MODULE_IMPLEMENTATIONS_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_IMPLEMENTATIONS_LABEL,
                ci,
                moduleImplementations,
                EdtVirtualGroupNode.MODULE_IMPLEMENTATIONS_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(moduleImplGroup);
        children.add(moduleImplGroup);

        // ModuleInstances group (filter from instances)
        List<Object> moduleInstances = new ArrayList<>();
        List<Object> triggerInstances = new ArrayList<>();
        List<Object> dynamicTriggerInstances = new ArrayList<>();
        for (Instance instance : ci.getInstances()) {
            if (instance instanceof ModuleInstance) {
                moduleInstances.add(instance);
            } else if (instance instanceof DynamicTriggerInstance) {
                dynamicTriggerInstances.add(instance);
            } else if (instance instanceof TriggerInstance) {
                triggerInstances.add(instance);
            }
        }

        EdtVirtualGroupNode moduleInstancesGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.MODULE_INSTANCES_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_INSTANCES_LABEL,
                ci,
                moduleInstances,
                EdtVirtualGroupNode.MODULE_INSTANCES_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(moduleInstancesGroup);
        children.add(moduleInstancesGroup);

        // TriggerInstances group
        EdtVirtualGroupNode triggerInstancesGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.TRIGGER_INSTANCES_ID_SUFFIX,
                EdtVirtualGroupNode.TRIGGER_INSTANCES_LABEL,
                ci,
                triggerInstances,
                EdtVirtualGroupNode.TRIGGER_INSTANCES_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(triggerInstancesGroup);
        children.add(triggerInstancesGroup);

        // DynamicTriggerInstances group
        EdtVirtualGroupNode dynamicTriggerGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.DYNAMIC_TRIGGER_INSTANCES_ID_SUFFIX,
                EdtVirtualGroupNode.DYNAMIC_TRIGGER_INSTANCES_LABEL,
                ci,
                dynamicTriggerInstances,
                EdtVirtualGroupNode.DYNAMIC_TRIGGER_INSTANCES_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(dynamicTriggerGroup);
        children.add(dynamicTriggerGroup);

        // ExternalOperations group
        List<Object> externalOps = new ArrayList<>(ci.getExternalSenders());
        EdtVirtualGroupNode externalOpsGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.EXTERNAL_OPERATIONS_ID_SUFFIX,
                EdtVirtualGroupNode.EXTERNAL_OPERATIONS_LABEL,
                ci,
                externalOps
        );
        this.edtExplorerServices.registerVirtualNode(externalOpsGroup);
        children.add(externalOpsGroup);

        // Service QoS group
        List<Object> serviceQos = new ArrayList<>(ci.getAssociatedServiceQos());
        EdtVirtualGroupNode serviceQosGroup = new EdtVirtualGroupNode(
                ciId + EdtVirtualGroupNode.SERVICE_QOS_ID_SUFFIX,
                EdtVirtualGroupNode.SERVICE_QOS_LABEL,
                ci,
                serviceQos,
                EdtVirtualGroupNode.SERVICE_QOS_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(serviceQosGroup);
        children.add(serviceQosGroup);

        // Insertion Policies node (single object, shown directly if present)
        if (ci.getInsertionPolicyList() != null) {
            children.add(ci.getInsertionPolicyList());
        }

        return children;
    }

    /**
     * Get children for ModuleType node.
     * Creates virtual groups for Properties, Operations, and Pinfo.
     */
    private List<Object> getModuleTypeChildren(ModuleType moduleType) {
        List<Object> children = new ArrayList<>();
        String mtId = this.identityService.getId(moduleType);

        // Properties virtual group (with child creation support)
        List<Object> properties = new ArrayList<>(moduleType.getProperties());
        EdtVirtualGroupNode propertiesGroup = new EdtVirtualGroupNode(
                mtId + EdtVirtualGroupNode.MODULE_TYPE_PROPERTIES_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_TYPE_PROPERTIES_LABEL,
                moduleType,
                properties,
                EdtVirtualGroupNode.MODULE_TYPE_PROPERTY_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(propertiesGroup);
        children.add(propertiesGroup);

        // Operations virtual group
        List<Object> operations = new ArrayList<>(moduleType.getOperations());
        EdtVirtualGroupNode operationsGroup = new EdtVirtualGroupNode(
                mtId + EdtVirtualGroupNode.MODULE_TYPE_OPERATIONS_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_TYPE_OPERATIONS_LABEL,
                moduleType,
                operations
        );
        this.edtExplorerServices.registerVirtualNode(operationsGroup);
        children.add(operationsGroup);

        // Pinfo virtual group
        List<Object> pinfoList = new ArrayList<>(moduleType.getPinfo());
        EdtVirtualGroupNode pinfoGroup = new EdtVirtualGroupNode(
                mtId + EdtVirtualGroupNode.MODULE_TYPE_PINFO_ID_SUFFIX,
                EdtVirtualGroupNode.MODULE_TYPE_PINFO_LABEL,
                moduleType,
                pinfoList
        );
        this.edtExplorerServices.registerVirtualNode(pinfoGroup);
        children.add(pinfoGroup);

        return children;
    }

    /**
     * Get children for InsertionPolicies node - returns computed children as-is.
     */
    private List<Object> getInsertionPoliciesChildren(InsertionPolicies insertionPolicies, List<Object> computedChildren) {
        return new ArrayList<>(computedChildren);
    }

    /**
     * Get children for Step5 node - returns deployment-related elements in virtual group folders.
     */
    private List<Object> getStep5Children(Step5 step5) {
        List<Object> children = new ArrayList<>();
        String step5Id = this.identityService.getId(step5);

        // Logical System group
        List<Object> logicalSystemChildren = new ArrayList<>();
        if (step5.getLogicalSystem() != null) {
            logicalSystemChildren.add(step5.getLogicalSystem());
        }
        String logicalSystemChildType = logicalSystemChildren.isEmpty() ? EdtVirtualGroupNode.LOGICAL_SYSTEM_CHILD_TYPE : null;
        EdtVirtualGroupNode logicalSystemGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.LOGICAL_SYSTEM_ID_SUFFIX,
                EdtVirtualGroupNode.LOGICAL_SYSTEM_LABEL,
                step5,
                logicalSystemChildren,
                logicalSystemChildType
        );
        this.edtExplorerServices.registerVirtualNode(logicalSystemGroup);
        children.add(logicalSystemGroup);

        // Deployment group
        List<Object> deploymentChildren = new ArrayList<>();
        if (step5.getDeployment() != null) {
            deploymentChildren.add(step5.getDeployment());
        }
        String deploymentChildType = deploymentChildren.isEmpty() ? EdtVirtualGroupNode.DEPLOYMENT_CHILD_TYPE : null;
        EdtVirtualGroupNode deploymentGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.DEPLOYMENT_ID_SUFFIX,
                EdtVirtualGroupNode.DEPLOYMENT_LABEL,
                step5,
                deploymentChildren,
                deploymentChildType
        );
        this.edtExplorerServices.registerVirtualNode(deploymentGroup);
        children.add(deploymentGroup);

        // Final Assembly group
        List<Object> finalAssemblyChildren = new ArrayList<>();
        if (step5.getFinalAssembly() != null) {
            finalAssemblyChildren.add(step5.getFinalAssembly());
        }
        String finalAssemblyChildType = finalAssemblyChildren.isEmpty() ? EdtVirtualGroupNode.FINAL_ASSEMBLY_CHILD_TYPE : null;
        EdtVirtualGroupNode finalAssemblyGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.FINAL_ASSEMBLY_ID_SUFFIX,
                EdtVirtualGroupNode.FINAL_ASSEMBLY_LABEL,
                step5,
                finalAssemblyChildren,
                finalAssemblyChildType
        );
        this.edtExplorerServices.registerVirtualNode(finalAssemblyGroup);
        children.add(finalAssemblyGroup);

        // UDP Bindings group - hidden from treeview (model data preserved)
        List<Object> udpBindingsChildren = new ArrayList<>(step5.getUDPBindings());
        EdtVirtualGroupNode udpBindingsGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.UDP_BINDINGS_ID_SUFFIX,
                EdtVirtualGroupNode.UDP_BINDINGS_LABEL,
                step5,
                udpBindingsChildren,
                EdtVirtualGroupNode.UDP_BINDING_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(udpBindingsGroup);
        children.add(udpBindingsGroup);

        // Cross Platform View group - hidden from treeview (model data preserved)
        List<Object> crossPlatformChildren = new ArrayList<>();
        if (step5.getCrossPlatformView() != null) {
            crossPlatformChildren.add(step5.getCrossPlatformView());
        }
        String crossPlatformChildType = crossPlatformChildren.isEmpty() ? EdtVirtualGroupNode.CROSS_PLATFORM_VIEW_CHILD_TYPE : null;
        EdtVirtualGroupNode crossPlatformGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.CROSS_PLATFORM_VIEW_ID_SUFFIX,
                EdtVirtualGroupNode.CROSS_PLATFORM_VIEW_LABEL,
                step5,
                crossPlatformChildren,
                crossPlatformChildType
        );
        this.edtExplorerServices.registerVirtualNode(crossPlatformGroup);
        // children.add(crossPlatformGroup); // hidden from treeview

        // IDS group - hidden from treeview (model data preserved)
        List<Object> idsChildren = new ArrayList<>(step5.getIDS());
        EdtVirtualGroupNode idsGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.IDS_ID_SUFFIX,
                EdtVirtualGroupNode.IDS_LABEL,
                step5,
                idsChildren,
                EdtVirtualGroupNode.ID_MAP_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(idsGroup);
        // children.add(idsGroup); // hidden from treeview

        // DDS Bindings — hidden from treeview; topic name is configured directly on each LogicalComputingPlatformLink
        List<Object> ddsBindingsChildren = new ArrayList<>(step5.getDDSBindings());
        EdtVirtualGroupNode ddsBindingsGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.DDS_BINDINGS_ID_SUFFIX,
                EdtVirtualGroupNode.DDS_BINDINGS_LABEL,
                step5,
                ddsBindingsChildren,
                EdtVirtualGroupNode.DDS_BINDING_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(ddsBindingsGroup);
        // children.add(ddsBindingsGroup); // hidden: topic name managed via platform link property view

        // TCP Bindings group
        List<Object> tcpBindingsChildren = new ArrayList<>(step5.getTCPBindings());
        EdtVirtualGroupNode tcpBindingsGroup = new EdtVirtualGroupNode(
                step5Id + EdtVirtualGroupNode.TCP_BINDINGS_ID_SUFFIX,
                EdtVirtualGroupNode.TCP_BINDINGS_LABEL,
                step5,
                tcpBindingsChildren,
                EdtVirtualGroupNode.TCP_BINDING_CHILD_TYPE
        );
        this.edtExplorerServices.registerVirtualNode(tcpBindingsGroup);
        children.add(tcpBindingsGroup);

        return children;
    }


    /**
     * Get children for ServiceDefinition node - filters out usedLibraries (Library objects)
     * to prevent expansion state collision with the same Library objects under Step0/Types.
     */
    private List<Object> getServiceDefinitionChildren(ServiceDefinition serviceDefinition, List<Object> computedChildren) {
        // Filter out Library objects (usedLibraries) to avoid tree item ID collision
        // with the same Library objects under Step0/Types
        return computedChildren.stream()
                .filter(child -> !(child instanceof Library))
                .collect(java.util.stream.Collectors.toList());
    }
}
