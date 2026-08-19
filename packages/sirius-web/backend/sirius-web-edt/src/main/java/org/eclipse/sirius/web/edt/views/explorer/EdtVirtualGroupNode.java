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

import org.eclipse.sirius.components.core.api.ChildCreationDescription;

import java.util.List;
import java.util.Objects;

/**
 * Represents a virtual grouping node in the EDT Explorer tree.
 * Virtual nodes are used to organize EMF objects into logical groups
 * without corresponding EMF model elements.
 *
 * @author EDT Team
 */
public record EdtVirtualGroupNode(
        String id,
        String label,
        Object parent,
        List<Object> children,
        String childCreationDescriptionId
) {
    public EdtVirtualGroupNode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(label);
        Objects.requireNonNull(parent);
        Objects.requireNonNull(children);
    }

    /**
     * Convenience constructor without childCreationDescriptionId (defaults to null).
     */
    public EdtVirtualGroupNode(String id, String label, Object parent, List<Object> children) {
        this(id, label, parent, children, null);
    }

    /**
     * Check if this virtual node supports child creation.
     */
    public boolean supportsChildCreation() {
        return this.childCreationDescriptionId != null && !this.childCreationDescriptionId.isBlank();
    }

    /**
     * Get the ChildCreationDescription for this virtual node.
     */
    public ChildCreationDescription getChildCreationDescription() {
        if (!this.supportsChildCreation()) {
            return null;
        }
        return new ChildCreationDescription(this.childCreationDescriptionId, this.childCreationDescriptionId, List.of());
    }

    /**
     * Virtual group for Basic Types under Step0.
     */
    public static final String BASIC_TYPES_ID_SUFFIX = "#basicTypes";
    public static final String BASIC_TYPES_LABEL = "edt.tree.basicTypes";

    /**
     * Virtual group for ECOA Predefined Types under Step0.
     */
    public static final String ECOA_PREDEFINED_TYPES_ID_SUFFIX = "#ecoaPredefinedTypes";
    public static final String ECOA_PREDEFINED_TYPES_LABEL = "edt.tree.ecoaPredefinedTypes";

    /**
     * Virtual group for Record types under "ECOA Predefined Types".
     */
    public static final String RECORD_ID_SUFFIX = "#record";
    public static final String RECORD_LABEL = "edt.tree.record";

    /**
     * Virtual group for Array types under "ECOA Predefined Types".
     */
    public static final String ARRAY_ID_SUFFIX = "#array";
    public static final String ARRAY_LABEL = "edt.tree.array";

    /**
     * Virtual group for Simple types under "ECOA Predefined Types".
     */
    public static final String SIMPLE_ID_SUFFIX = "#simple";
    public static final String SIMPLE_LABEL = "edt.tree.simple";

    /**
     * Virtual group for Enum types under "ECOA Predefined Types".
     */
    public static final String ENUM_ID_SUFFIX = "#enum";
    public static final String ENUM_LABEL = "edt.tree.enum";

    /**
     * Virtual group for Module Types under ComponentImplementation.
     */
    public static final String MODULE_TYPES_ID_SUFFIX = "#moduleTypes";
    public static final String MODULE_TYPES_LABEL = "edt.tree.moduleTypes";

    /**
     * Virtual group for Module Implementations under ComponentImplementation.
     */
    public static final String MODULE_IMPLEMENTATIONS_ID_SUFFIX = "#moduleImplementations";
    public static final String MODULE_IMPLEMENTATIONS_LABEL = "edt.tree.moduleImplementations";

    /**
     * Virtual group for Module Instances under ComponentImplementation.
     */
    public static final String MODULE_INSTANCES_ID_SUFFIX = "#moduleInstances";
    public static final String MODULE_INSTANCES_LABEL = "edt.tree.moduleInstances";

    /**
     * Virtual group for Trigger Instances under ComponentImplementation.
     */
    public static final String TRIGGER_INSTANCES_ID_SUFFIX = "#triggerInstances";
    public static final String TRIGGER_INSTANCES_LABEL = "edt.tree.triggerInstances";

    /**
     * Virtual group for Dynamic Trigger Instances under ComponentImplementation.
     */
    public static final String DYNAMIC_TRIGGER_INSTANCES_ID_SUFFIX = "#dynamicTriggerInstances";
    public static final String DYNAMIC_TRIGGER_INSTANCES_LABEL = "edt.tree.dynamicTriggerInstances";

    /**
     * Virtual group for External Operations under ComponentImplementation.
     */
    public static final String EXTERNAL_OPERATIONS_ID_SUFFIX = "#externalOperations";
    public static final String EXTERNAL_OPERATIONS_LABEL = "edt.tree.externalOperations";

    /**
     * Virtual group for Service QoS under ComponentImplementation.
     */
    public static final String SERVICE_QOS_ID_SUFFIX = "#serviceQos";
    public static final String SERVICE_QOS_LABEL = "edt.tree.serviceQos";

    /**
     * Virtual group for Services under Step1.
     */
    public static final String SERVICES_ID_SUFFIX = "#services";
    public static final String SERVICES_LABEL = "edt.tree.services";

    /**
     * Virtual group for Component Definitions under Step2.
     */
    public static final String COMPONENT_DEFINITIONS_ID_SUFFIX = "#componentDefinitions";
    public static final String COMPONENT_DEFINITIONS_LABEL = "edt.tree.componentDefinitions";

    /**
     * Virtual group for Initial Assembly under Step3.
     */
    public static final String INITIAL_ASSEMBLY_ID_SUFFIX = "#initialAssembly";
    public static final String INITIAL_ASSEMBLY_LABEL = "edt.tree.initialAssembly";

    /**
     * Virtual group for Logical System under Step5.
     */
    public static final String LOGICAL_SYSTEM_ID_SUFFIX = "#logicalSystem";
    public static final String LOGICAL_SYSTEM_LABEL = "edt.tree.logicalSystem";

    /**
     * Virtual group for Deployment under Step5.
     */
    public static final String DEPLOYMENT_ID_SUFFIX = "#deployment";
    public static final String DEPLOYMENT_LABEL = "edt.tree.deployment";

    /**
     * Virtual group for Final Assembly under Step5.
     */
    public static final String FINAL_ASSEMBLY_ID_SUFFIX = "#finalAssembly";
    public static final String FINAL_ASSEMBLY_LABEL = "edt.tree.finalAssembly";

    /**
     * Virtual group for UDP Bindings under Step5.
     */
    public static final String UDP_BINDINGS_ID_SUFFIX = "#udpBindings";
    public static final String UDP_BINDINGS_LABEL = "edt.tree.udpBindings";

    /**
     * Virtual group for TCP Bindings under Step5.
     */
    public static final String TCP_BINDINGS_ID_SUFFIX = "#tcpBindings";
    public static final String TCP_BINDINGS_LABEL = "edt.tree.tcpBindings";

    /**
     * Virtual group for DDS Bindings under Step5.
     */
    public static final String DDS_BINDINGS_ID_SUFFIX = "#ddsBindings";
    public static final String DDS_BINDINGS_LABEL = "edt.tree.ddsBindings";

    /**
     * Virtual group for Cross Platform View under Step5.
     */
    public static final String CROSS_PLATFORM_VIEW_ID_SUFFIX = "#crossPlatformView";
    public static final String CROSS_PLATFORM_VIEW_LABEL = "edt.tree.crossPlatformView";

    /**
     * Virtual group for IDS under Step5.
     */
    public static final String IDS_ID_SUFFIX = "#ids";
    public static final String IDS_LABEL = "edt.tree.ids";

    /**
     * Virtual group for Properties under ModuleType.
     */
    public static final String MODULE_TYPE_PROPERTIES_ID_SUFFIX = "#moduleTypeProperties";
    public static final String MODULE_TYPE_PROPERTIES_LABEL = "edt.tree.moduleTypeProperties";

    /**
     * Virtual group for Operations under ModuleType.
     */
    public static final String MODULE_TYPE_OPERATIONS_ID_SUFFIX = "#moduleTypeOperations";
    public static final String MODULE_TYPE_OPERATIONS_LABEL = "edt.tree.moduleTypeOperations";

    /**
     * Virtual group for Pinfo under ModuleType.
     */
    public static final String MODULE_TYPE_PINFO_ID_SUFFIX = "#moduleTypePinfo";
    public static final String MODULE_TYPE_PINFO_LABEL = "edt.tree.moduleTypePinfo";

    /**
     * Child creation description IDs for virtual folders.
     */
    public static final String RECORD_CHILD_TYPE = "RecordPredefined";
    public static final String ARRAY_CHILD_TYPE = "ArrayPredefined";
    public static final String SIMPLE_CHILD_TYPE = "SimplePredefined";
    public static final String ENUM_CHILD_TYPE = "EnumPredefined";
    public static final String SERVICES_CHILD_TYPE = "ServiceDefinition";
    public static final String COMPONENT_DEFINITIONS_CHILD_TYPE = "ComponentDefinition";
    public static final String MODULE_TYPES_CHILD_TYPE = "ModuleType";
    public static final String MODULE_IMPLEMENTATIONS_CHILD_TYPE = "ModuleImplementation";
    public static final String MODULE_INSTANCES_CHILD_TYPE = "ModuleInstance";
    public static final String TRIGGER_INSTANCES_CHILD_TYPE = "TriggerInstance";
    public static final String DYNAMIC_TRIGGER_INSTANCES_CHILD_TYPE = "DynamicTriggerInstance";
    public static final String SERVICE_QOS_CHILD_TYPE = "ServiceInstanceQos";
    public static final String UDP_BINDING_CHILD_TYPE = "UDPBinding";
    public static final String TCP_BINDING_CHILD_TYPE = "TCPBinding";
    public static final String DDS_BINDING_CHILD_TYPE = "DDSBinding";
    public static final String ID_MAP_CHILD_TYPE = "IDMap";
    public static final String MODULE_TYPE_PROPERTY_CHILD_TYPE = "ModuleTypeProperty";
// Single-object types (only creatable when folder is empty)
    public static final String INITIAL_ASSEMBLY_CHILD_TYPE = "Composite";
    public static final String LOGICAL_SYSTEM_CHILD_TYPE = "LogicalSystem";
    public static final String DEPLOYMENT_CHILD_TYPE = "Deployment";
    public static final String FINAL_ASSEMBLY_CHILD_TYPE = "FinalAssembly";
    public static final String CROSS_PLATFORM_VIEW_CHILD_TYPE = "CrossPlatformView";

    /**
     * Child creation type IDs for the Operations virtual folder under ModuleType.
     */
    public static final String[] MODULE_OPERATION_CHILD_TYPES = {
        "EventSent", "EventReceived",
        "VersionedDataWritten", "VersionedDataRead",
        "RequestSent", "RequestReceived"
    };

    /**
     * Child creation type IDs for the Pinfo virtual folder under ModuleType.
     */
    public static final String[] MODULE_PINFO_CHILD_TYPES = {
        "PublicPinfo", "PrivatePinfo"
    };

    /**
     * Kind identifier for virtual group nodes.
     */
    public static final String KIND = "siriusWeb://virtualGroup";

    /**
     * Check if this virtual node has children.
     */
    public boolean hasChildren() {
        return !this.children.isEmpty();
    }
}
