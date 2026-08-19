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

import edtdeployment.*;
import org.eclipse.emf.common.util.EList;
import technology.ecoa.deployment._2.DocumentRoot;
import technology.ecoa.deployment._2.ExecuteOnType;
import technology.ecoa.deployment._2.depFactory;

/**
 * Converts EDT Deployment objects to ECOA Deployment XML format.
 * Based on the original DeploymentExportConverter from edt-tmp.
 */
public class DeploymentExportConverter {

    private static final depFactory DEPFACTORY = depFactory.eINSTANCE;

    private DeploymentExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT Deployment to ECOA Deployment.
     *
     * @param edtDeployment the EDT Deployment to convert
     * @return DocumentRoot containing the Deployment
     */
    public static DocumentRoot recreateDeployment(Deployment edtDeployment) {
        DocumentRoot documentRoot = DEPFACTORY.createDocumentRoot();
        var ecoaDeployment = DEPFACTORY.createDeployment();

        // Set FinalAssembly reference
        if (edtDeployment.getFinalAssembly() != null) {
            ecoaDeployment.setFinalAssembly(edtDeployment.getFinalAssembly().getName());
        }

        // Set LogicalSystem reference
        if (edtDeployment.getLogicalSystem() != null) {
            ecoaDeployment.setLogicalSystem(edtDeployment.getLogicalSystem().getFileNamePrefix());
        }

        // Convert ProtectionDomains
        EList<ProtectionDomain> domains = edtDeployment.getProtectionDomains();
        for (ProtectionDomain domain : domains) {
            ecoaDeployment.getProtectionDomain().add(recreateProtectionDomain(domain));
        }

        // Convert LogPolicies
        EList<LogPolicy> policies = edtDeployment.getLogPolicies();
        for (LogPolicy policy : policies) {
            ecoaDeployment.getLogPolicy().add(recreateLogPolicy(policy));
        }

        // Convert PlatformConfigurations
        EList<PlatformConfiguration> configs = edtDeployment.getPlatformConfigurations();
        for (PlatformConfiguration config : configs) {
            ecoaDeployment.getPlatformConfiguration().add(recreatePlatformConfiguration(config));
        }

        // Convert WireMappings
        EList<WireMapping> mappings = edtDeployment.getWireMappings();
        for (WireMapping mapping : mappings) {
            ecoaDeployment.getWireMapping().add(recreateWireMapping(mapping));
        }

        documentRoot.setDeployment(ecoaDeployment);
        return documentRoot;
    }

    private static technology.ecoa.deployment._2.ProtectionDomain recreateProtectionDomain(ProtectionDomain edtDomain) {
        var domain = DEPFACTORY.createProtectionDomain();

        domain.setName(edtDomain.getName());

        // ExecuteOn
        ExecuteOnType executeOn = DEPFACTORY.createExecuteOnType();
        if (edtDomain.getExecuteOnComputingNode() != null) {
            executeOn.setComputingNode(edtDomain.getExecuteOnComputingNode().getId());
        }
        if (edtDomain.getExecuteOnComputingPlatform() != null) {
            executeOn.setComputingPlatform(edtDomain.getExecuteOnComputingPlatform().getId());
        }
        domain.setExecuteOn(executeOn);

        // DeployedModuleInstances
        EList<DeployedModuleInstance> modules = edtDomain.getDeployedModuleInstances();
        for (DeployedModuleInstance module : modules) {
            domain.getDeployedModuleInstance().add(recreateDeployedModuleInstance(module));
        }

        // DeployedTriggerInstances
        EList<DeployedTriggerInstance> triggers = edtDomain.getDeployedTriggerInstances();
        for (DeployedTriggerInstance trigger : triggers) {
            domain.getDeployedTriggerInstance().add(recreateDeployedTriggerInstance(trigger));
        }

        return domain;
    }

    private static technology.ecoa.deployment._2.DeployedModuleInstanceType recreateDeployedModuleInstance(
            DeployedModuleInstance edtInstance) {
        var instance = DEPFACTORY.createDeployedModuleInstanceType();

        instance.setModulePriority(edtInstance.getModulePriority());
        if (edtInstance.getComponent() != null) {
            instance.setComponentName(edtInstance.getComponent().getName());
        }
        if (edtInstance.getModuleInstance() != null) {
            instance.setModuleInstanceName(edtInstance.getModuleInstance().getName());
        }

        return instance;
    }

    private static technology.ecoa.deployment._2.DeployedTriggerInstanceType recreateDeployedTriggerInstance(
            DeployedTriggerInstance edtInstance) {
        var instance = DEPFACTORY.createDeployedTriggerInstanceType();

        instance.setTriggerPriority(edtInstance.getTriggerPriority());
        if (edtInstance.getComponent() != null) {
            instance.setComponentName(edtInstance.getComponent().getName());
        }
        if (edtInstance.getTriggerInstance() != null) {
            instance.setTriggerInstanceName(edtInstance.getTriggerInstance().getName());
        }

        return instance;
    }

    private static technology.ecoa.deployment._2.LogPolicy recreateLogPolicy(LogPolicy edtPolicy) {
        var policy = DEPFACTORY.createLogPolicy();

        EList<ComponentLog> logs = edtPolicy.getComponentLogs();
        for (ComponentLog log : logs) {
            policy.getComponentLog().add(recreateComponentLog(log));
        }

        return policy;
    }

    private static technology.ecoa.deployment._2.ComponentLog recreateComponentLog(ComponentLog edtLog) {
        var log = DEPFACTORY.createComponentLog();

        if (edtLog.getEnabledLevels() != null && !edtLog.getEnabledLevels().isBlank()) {
            log.setEnabledLevels(edtLog.getEnabledLevels());
        }
        if (edtLog.getComponentInstance() != null) {
            log.setInstanceName(edtLog.getComponentInstance().getName());
        }

        EList<ModuleLog> moduleLogs = edtLog.getModuleLogs();
        for (ModuleLog moduleLog : moduleLogs) {
            log.getModuleLog().add(recreateModuleLog(moduleLog));
        }

        return log;
    }

    private static technology.ecoa.deployment._2.ModuleLog recreateModuleLog(ModuleLog edtLog) {
        var log = DEPFACTORY.createModuleLog();

        if (edtLog.getEnabledLevels() != null && !edtLog.getEnabledLevels().isBlank()) {
            log.setEnabledLevels(edtLog.getEnabledLevels());
        }
        if (edtLog.getModuleInstance() != null) {
            log.setInstanceName(edtLog.getModuleInstance().getName());
        }

        return log;
    }

    private static technology.ecoa.deployment._2.PlatformConfiguration recreatePlatformConfiguration(
            PlatformConfiguration edtConfig) {
        var config = DEPFACTORY.createPlatformConfiguration();

        if (edtConfig.isSetFaultHandlerNotificationMaxNumber()) {
            config.setFaultHandlerNotificationMaxNumber(edtConfig.getFaultHandlerNotificationMaxNumber());
        }

        config.setEUIDs(edtConfig.getEUIDs());

        if (edtConfig.getComputingPlatform() != null) {
            config.setComputingPlatform(edtConfig.getComputingPlatform().getId());
        }

        // ComputingNodeConfigurations
        EList<ComputingNodeConfiguration> nodeConfigs = edtConfig.getComputingNodeConfigurations();
        for (ComputingNodeConfiguration nodeConfig : nodeConfigs) {
            config.getComputingNodeConfiguration().add(recreateComputingNodeConfiguration(nodeConfig));
        }

        // PlatformMessages
        EList<PlatformMessage> messages = edtConfig.getPlatformMessages();
        for (PlatformMessage message : messages) {
            var ecoaMessage = DEPFACTORY.createPlatformMessages();
            if (message.getMappedOnLinkId() != null) {
                ecoaMessage.setMappedOnLinkId(message.getMappedOnLinkId().getId());
            }
            config.getPlatformMessages().add(ecoaMessage);
        }

        return config;
    }

    private static technology.ecoa.deployment._2.ComputingNodeConfiguration recreateComputingNodeConfiguration(
            ComputingNodeConfiguration edtConfig) {
        var config = DEPFACTORY.createComputingNodeConfiguration();

        if (edtConfig.getSchedulingInformation() != null && !edtConfig.getSchedulingInformation().isBlank()) {
            config.setSchedulingInformation(edtConfig.getSchedulingInformation());
        }
        if (edtConfig.getComputingNode() != null) {
            config.setComputingNode(edtConfig.getComputingNode().getId());
        }

        return config;
    }

    private static technology.ecoa.deployment._2.WireMapping recreateWireMapping(WireMapping edtMapping) {
        var mapping = DEPFACTORY.createWireMapping();

        if (edtMapping.getMappedOnLinkId() != null) {
            mapping.setMappedOnLinkId(edtMapping.getMappedOnLinkId().getId());
        }
        if (edtMapping.getWire() != null && edtMapping.getWire().getSource() != null) {
            mapping.setSource(edtMapping.getWire().getSource().getWireString());
        }
        if (edtMapping.getWire() != null && edtMapping.getWire().getTarget() != null) {
            mapping.setTarget(edtMapping.getWire().getTarget().getWireString());
        }

        return mapping;
    }
}
