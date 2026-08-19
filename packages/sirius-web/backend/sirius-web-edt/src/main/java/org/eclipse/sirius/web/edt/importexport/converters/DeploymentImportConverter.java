/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.importexport.converters;

import edtdeployment.EdtdeploymentFactory;
import edtimplementation.ComponentImplementation;
import edtimplementation.Instance;
import edtimplementation.ModuleInstance;
import edtimplementation.Trigger;
import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingPlatform;
import edtlogical.LogicalComputingPlatformLink;
import edtlogical.LogicalSystem;
import edtproject.*;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import technology.ecoa.deployment._2.*;

import java.util.Objects;

/**
 * Convert imported ECOA Deployment objects to EDT objects.
 */
public class DeploymentImportConverter {

    private static final EdtdeploymentFactory EDTDEPLOYMENTFACTORY = EdtdeploymentFactory.eINSTANCE;

    private DeploymentImportConverter() {
        // Utility class
    }

    public static edtdeployment.Deployment createEDTDeployment(technology.ecoa.deployment._2.Deployment ecoaDeployment, String fileName,
                                                               FinalAssembly finalAssembly, LogicalSystem edtLogicalSystem) throws FailedImportException {

        var edtDeployment = EDTDEPLOYMENTFACTORY.createDeployment();
        edtDeployment.setName(EdtProjectImportConverter.getObjectName(fileName, ".deployment.xml"));

        // Set finalAssembly
        String ecoaFinalAssembly = ecoaDeployment.getFinalAssembly();
        if (finalAssembly != null && Objects.equals(finalAssembly.getName(), ecoaFinalAssembly)
                && finalAssembly.getFinalAssembly() != null) {
            edtDeployment.setFinalAssembly(finalAssembly);
        } else if (finalAssembly != null && finalAssembly.getFinalAssembly() == null) {
            throw new FailedImportException("Problem with FinalAssembly, it has no Components");
        } else {
            throw new FailedImportException("The name " + ecoaFinalAssembly + " does not correspond to the name of the FinalAssembly");
        }

        // Set LogicalSystem
        String ecoaLogicalSystem = ecoaDeployment.getLogicalSystem();
        if (edtLogicalSystem != null && Objects.equals(edtLogicalSystem.getFileNamePrefix(), ecoaLogicalSystem)) {
            edtDeployment.setLogicalSystem(edtLogicalSystem);
        } else {
            throw new FailedImportException("The name " + ecoaLogicalSystem + " does not correspond to the name of the LogicalSystem");
        }

        // Convert ProtectionDomain
        for (ProtectionDomain ecoaPD : ecoaDeployment.getProtectionDomain()) {
            edtDeployment.getProtectionDomains().add(createEDTProtectionDomain(finalAssembly, edtLogicalSystem, ecoaPD));
        }

        // Convert LogPolicy
        for (LogPolicy ecoaLP : ecoaDeployment.getLogPolicy()) {
            edtDeployment.getLogPolicies().add(createEDTLogPolicy(finalAssembly, ecoaLP));
        }

        // Convert PlatformConfiguration
        for (PlatformConfiguration ecoaPC : ecoaDeployment.getPlatformConfiguration()) {
            edtDeployment.getPlatformConfigurations().add(createEDTPlatformConfiguration(edtLogicalSystem, ecoaPC));
        }

        // Convert WireMapping
        for (WireMapping ecoaWM : ecoaDeployment.getWireMapping()) {
            edtDeployment.getWireMappings().add(createEDTWireMapping(finalAssembly, ecoaWM, edtLogicalSystem));
        }

        return edtDeployment;
    }

    private static edtdeployment.WireMapping createEDTWireMapping(FinalAssembly finalAssembly, WireMapping ecoaWireMapping,
            LogicalSystem edtLogicalSystem) throws FailedImportException {
        var edtWireMapping = EDTDEPLOYMENTFACTORY.createWireMapping();
        
        LogicalComputingPlatformLink edtLogicalPlatformLink = edtLogicalSystem
                .findLogicalComputingPlatformLinkById(ecoaWireMapping.getMappedOnLinkId());
        if (edtLogicalPlatformLink != null) {
            edtWireMapping.setMappedOnLinkId(edtLogicalPlatformLink);
        } else {
            throw new FailedImportException("No LogicalComputingPlatformLink found with name :" + ecoaWireMapping.getMappedOnLinkId());
        }

        ComponentReference componentReferenceFromWire = finalAssembly.getFinalAssembly()
                .findComponentReferenceFromWire(ecoaWireMapping.getSource());
        ComponentService componentServiceFromWire = finalAssembly.getFinalAssembly()
                .findComponentServiceFromWire(ecoaWireMapping.getTarget());

        if (componentReferenceFromWire != null && componentServiceFromWire != null) {
            ServiceLink findServiceLink = finalAssembly.getFinalAssembly().findServiceLink(componentReferenceFromWire,
                    componentServiceFromWire);
            if (findServiceLink == null) {
                throw new FailedImportException("No Wire found between :" + ecoaWireMapping.getSource() + " and " + ecoaWireMapping.getTarget());
            }
            edtWireMapping.setWire(findServiceLink);
        } else {
             throw new FailedImportException("ComponentReference or ComponentService not found for WireMapping: " + ecoaWireMapping.getSource() + " -> " + ecoaWireMapping.getTarget());
        }
        return edtWireMapping;
    }

    private static edtdeployment.PlatformConfiguration createEDTPlatformConfiguration(LogicalSystem edtLogicalSystem,
            PlatformConfiguration ecoaPlatformConfiguration) throws FailedImportException {
        var edtPlatformConfiguration = EDTDEPLOYMENTFACTORY.createPlatformConfiguration();

        if (ecoaPlatformConfiguration.isSetFaultHandlerNotificationMaxNumber()) {
            edtPlatformConfiguration.setFaultHandlerNotificationMaxNumber(
                    ecoaPlatformConfiguration.getFaultHandlerNotificationMaxNumber());
        }
        edtPlatformConfiguration.setEUIDs(ecoaPlatformConfiguration.getEUIDs());

        LogicalComputingPlatform edtLogicalComputingPlatform = edtLogicalSystem
                .findLogicalComputingPlatformById(ecoaPlatformConfiguration.getComputingPlatform());
        if (edtLogicalComputingPlatform != null) {
            edtPlatformConfiguration.setComputingPlatform(edtLogicalComputingPlatform);
            for (ComputingNodeConfiguration ecoaCNC : ecoaPlatformConfiguration.getComputingNodeConfiguration()) {
                edtPlatformConfiguration.getComputingNodeConfigurations().add(createEDTComputingNodeConfiguration(
                        edtLogicalComputingPlatform, ecoaCNC));
            }
        } else {
            throw new FailedImportException("No LogicalComputingPlatform found with name :" + ecoaPlatformConfiguration.getComputingPlatform());
        }

        for (PlatformMessages ecoaPM : ecoaPlatformConfiguration.getPlatformMessages()) {
            edtdeployment.PlatformMessage edtPlatformMessage = EDTDEPLOYMENTFACTORY.createPlatformMessage();
            LogicalComputingPlatformLink edtLogicalPlatformLink = edtLogicalSystem
                    .findLogicalComputingPlatformLinkById(ecoaPM.getMappedOnLinkId());
            if (edtLogicalPlatformLink != null) {
                edtPlatformMessage.setMappedOnLinkId(edtLogicalPlatformLink);
            } else {
                throw new FailedImportException("No LogicalComputingPlatformLink found with name :" + ecoaPM.getMappedOnLinkId());
            }
            edtPlatformConfiguration.getPlatformMessages().add(edtPlatformMessage);
        }
        return edtPlatformConfiguration;
    }

    private static edtdeployment.ComputingNodeConfiguration createEDTComputingNodeConfiguration(
            LogicalComputingPlatform edtLogicalComputingPlatform,
            ComputingNodeConfiguration ecoaComputingNodeConfiguration) throws FailedImportException {
        var edtComputingNodeConfiguration = EDTDEPLOYMENTFACTORY.createComputingNodeConfiguration();

        edtComputingNodeConfiguration.setSchedulingInformation(ecoaComputingNodeConfiguration.getSchedulingInformation());

        LogicalComputingNode edtLogicalComputingNode = edtLogicalComputingPlatform
                .findLogicalComputingNodeById(ecoaComputingNodeConfiguration.getComputingNode());
        if (edtLogicalComputingNode != null) {
            edtComputingNodeConfiguration.setComputingNode(edtLogicalComputingNode);
        } else {
            throw new FailedImportException("No LogicalComputingNode found with name :" + ecoaComputingNodeConfiguration.getComputingNode());
        }
        return edtComputingNodeConfiguration;
    }

    private static edtdeployment.LogPolicy createEDTLogPolicy(FinalAssembly finalAssembly, LogPolicy ecoaLogPolicy)
            throws FailedImportException {
        var edtLogPolicy = EDTDEPLOYMENTFACTORY.createLogPolicy();

        for (ComponentLog ecoaComponentLog : ecoaLogPolicy.getComponentLog()) {
            edtLogPolicy.getComponentLogs().add(createEDTComponentLog(finalAssembly, ecoaComponentLog));
        }
        return edtLogPolicy;
    }

    private static edtdeployment.ComponentLog createEDTComponentLog(FinalAssembly finalAssembly, ComponentLog ecoaComponentLog)
            throws FailedImportException {
        var edtComponentLog = EDTDEPLOYMENTFACTORY.createComponentLog();
        edtComponentLog.setEnabledLevels(ecoaComponentLog.getEnabledLevels());

        Component edtComponent = finalAssembly.getFinalAssembly().findComponentByName(ecoaComponentLog.getInstanceName());
        if (edtComponent != null) {
            ComponentImplementation edtComponentImplementation = edtComponent.getComponentImplementation();
            if (edtComponentImplementation != null) {
                edtComponentLog.setComponentInstance(edtComponent);
                edtComponentLog.setComponentImplementation(edtComponentImplementation);

                for (ModuleLog ecoaModuleLog : ecoaComponentLog.getModuleLog()) {
                    edtComponentLog.getModuleLogs().add(createEDTModuleLog(edtComponentImplementation, ecoaModuleLog));
                }
            } else {
                throw new FailedImportException("No ComponentImplementation associated to the Component " + ecoaComponentLog.getInstanceName());
            }
        } else {
            throw new FailedImportException("Component not found: " + ecoaComponentLog.getInstanceName());
        }
        return edtComponentLog;
    }

    private static edtdeployment.ModuleLog createEDTModuleLog(ComponentImplementation edtComponentImplementation,
            ModuleLog ecoaModuleLog) throws FailedImportException {
        var edtModuleLog = EDTDEPLOYMENTFACTORY.createModuleLog();
        edtModuleLog.setEnabledLevels(ecoaModuleLog.getEnabledLevels());

        Instance instance = edtComponentImplementation.findInstanceByName(ecoaModuleLog.getInstanceName());
        if (instance instanceof ModuleInstance edtModuleInstance) {
            edtModuleLog.setModuleInstance(edtModuleInstance);
        } else {
            throw new FailedImportException("ModuleInstance not found: " + ecoaModuleLog.getInstanceName());
        }
        return edtModuleLog;
    }

    private static edtdeployment.ProtectionDomain createEDTProtectionDomain(FinalAssembly finalAssembly,
            LogicalSystem logicalSystem, ProtectionDomain ecoaProtectionDomain) throws FailedImportException {
        var edtProtectionDomain = EDTDEPLOYMENTFACTORY.createProtectionDomain();
        edtProtectionDomain.setName(ecoaProtectionDomain.getName());

        ExecuteOnType ecoaExecuteOn = ecoaProtectionDomain.getExecuteOn();
        String computingPlatform = ecoaExecuteOn.getComputingPlatform();
        LogicalComputingPlatform logicalComputingPlatformById = logicalSystem.findLogicalComputingPlatformById(computingPlatform);
        if (logicalComputingPlatformById != null) {
            edtProtectionDomain.setExecuteOnComputingPlatform(logicalComputingPlatformById);
        } else {
            throw new FailedImportException("No LogicalComputingPlatform found with name :" + computingPlatform);
        }

        String computingNode = ecoaExecuteOn.getComputingNode();
        LogicalComputingNode logicalComputingNodeById = logicalComputingPlatformById.findLogicalComputingNodeById(computingNode);
        if (logicalComputingNodeById != null) {
            edtProtectionDomain.setExecuteOnComputingNode(logicalComputingNodeById);
        } else {
            throw new FailedImportException("No LogicalComputingNode found with name :" + computingNode);
        }

        for (DeployedModuleInstanceType ecoaDMI : ecoaProtectionDomain.getDeployedModuleInstance()) {
            edtProtectionDomain.getDeployedModuleInstances().add(createEDTDeployedModuleInstance(finalAssembly, ecoaDMI));
        }

        for (DeployedTriggerInstanceType ecoaDTI : ecoaProtectionDomain.getDeployedTriggerInstance()) {
            edtProtectionDomain.getDeployedTriggerInstances().add(createEDTDeployedTriggerInstance(finalAssembly, ecoaDTI));
        }
        return edtProtectionDomain;
    }

    private static edtdeployment.DeployedModuleInstance createEDTDeployedModuleInstance(FinalAssembly finalAssembly,
            DeployedModuleInstanceType ecoaDMI) throws FailedImportException {
        var edtDMI = EDTDEPLOYMENTFACTORY.createDeployedModuleInstance();
        edtDMI.setModulePriority(ecoaDMI.getModulePriority());

        Component edtComponent = finalAssembly.getFinalAssembly().findComponentByName(ecoaDMI.getComponentName());
        if (edtComponent != null) {
            if (edtComponent.getComponentImplementation() != null) {
                edtDMI.setComponent(edtComponent);
                edtDMI.setComponentImplementation(edtComponent.getComponentImplementation());

                Instance edtInstance = edtComponent.getComponentImplementation().findInstanceByName(ecoaDMI.getModuleInstanceName());
                if (edtInstance instanceof ModuleInstance edtModuleInstance) {
                    edtDMI.setModuleInstance(edtModuleInstance);
                } else {
                    throw new FailedImportException("ModuleInstance not found: " + ecoaDMI.getModuleInstanceName());
                }
            } else {
                throw new FailedImportException("No ComponentImplementation for component: " + ecoaDMI.getComponentName());
            }
        } else {
            throw new FailedImportException("Component not found: " + ecoaDMI.getComponentName());
        }
        return edtDMI;
    }

    private static edtdeployment.DeployedTriggerInstance createEDTDeployedTriggerInstance(FinalAssembly finalAssembly,
            DeployedTriggerInstanceType ecoaDTI) throws FailedImportException {
        var edtDTI = EDTDEPLOYMENTFACTORY.createDeployedTriggerInstance();
        edtDTI.setTriggerPriority(ecoaDTI.getTriggerPriority());

        Component edtComponent = finalAssembly.getFinalAssembly().findComponentByName(ecoaDTI.getComponentName());
        if (edtComponent != null) {
            if (edtComponent.getComponentImplementation() != null) {
                edtDTI.setComponent(edtComponent);
                edtDTI.setComponentImplementation(edtComponent.getComponentImplementation());

                Instance edtInstance = edtComponent.getComponentImplementation().findInstanceByName(ecoaDTI.getTriggerInstanceName());
                if (edtInstance instanceof Trigger trigger) {
                    edtDTI.setTriggerInstance(trigger);
                } else {
                    throw new FailedImportException("TriggerInstance not found: " + ecoaDTI.getTriggerInstanceName());
                }
            } else {
                throw new FailedImportException("No ComponentImplementation for component: " + ecoaDTI.getComponentName());
            }
        } else {
             throw new FailedImportException("Component not found: " + ecoaDTI.getComponentName());
        }
        return edtDTI;
    }
}
