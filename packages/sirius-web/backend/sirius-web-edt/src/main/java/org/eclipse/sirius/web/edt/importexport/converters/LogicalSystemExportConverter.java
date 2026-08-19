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

import org.eclipse.emf.common.util.EList;

import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingNodeLink;
import edtlogical.LogicalComputingPlatformLink;
import edtlogical.LogicalProcessor;
import technology.ecoa.logicalsystem._2.AvailableMemoryType;
import technology.ecoa.logicalsystem._2.DocumentRoot;
import technology.ecoa.logicalsystem._2.EndianessType;
import technology.ecoa.logicalsystem._2.LatencyType;
import technology.ecoa.logicalsystem._2.LatencyType1;
import technology.ecoa.logicalsystem._2.LinkType;
import technology.ecoa.logicalsystem._2.LinkType1;
import technology.ecoa.logicalsystem._2.LogicalComputingNodeLinks;
import technology.ecoa.logicalsystem._2.LogicalComputingNodeType;
import technology.ecoa.logicalsystem._2.LogicalComputingPlatformLinks;
import technology.ecoa.logicalsystem._2.LogicalProcessorsType;
import technology.ecoa.logicalsystem._2.ModuleSwitchTimeType;
import technology.ecoa.logicalsystem._2.OsType;
import technology.ecoa.logicalsystem._2.StepDurationType;
import technology.ecoa.logicalsystem._2.ThroughputType;
import technology.ecoa.logicalsystem._2.ThroughputType1;
import technology.ecoa.logicalsystem._2.TransportBindingType;
import technology.ecoa.logicalsystem._2.logFactory;

/**
 * Converts EDT LogicalSystem objects to ECOA LogicalSystem XML format.
 * Based on the original LogicalSystemExportConverter from edt-tmp.
 */
public class LogicalSystemExportConverter {

    private static final logFactory LOGFACTORY = logFactory.eINSTANCE;

    private LogicalSystemExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT LogicalSystem to ECOA LogicalSystem.
     *
     * @param edtLogicalSystem the EDT LogicalSystem to convert
     * @return DocumentRoot containing the LogicalSystem
     */
    public static DocumentRoot recreateLogicalSystem(edtlogical.LogicalSystem edtLogicalSystem) {
        DocumentRoot documentRoot = LOGFACTORY.createDocumentRoot();
        var ecoaLogicalSystem = LOGFACTORY.createLogicalSystem();

        ecoaLogicalSystem.setId(edtLogicalSystem.getId());

        // Convert LogicalComputingPlatforms
        EList<edtlogical.LogicalComputingPlatform> platforms = edtLogicalSystem.getLogicalComputingPlatforms();
        for (edtlogical.LogicalComputingPlatform platform : platforms) {
            ecoaLogicalSystem.getLogicalComputingPlatform().add(recreateLogicalComputingPlatform(platform));
        }

        // Convert LogicalComputingPlatformLinks
        EList<LogicalComputingPlatformLink> platformLinks = edtLogicalSystem.getLogicalComputingPlatformLinks();
        if (!platformLinks.isEmpty()) {
            LogicalComputingPlatformLinks links = LOGFACTORY.createLogicalComputingPlatformLinks();
            for (LogicalComputingPlatformLink link : platformLinks) {
                links.getLink().add(recreatePlatformLink(link));
            }
            ecoaLogicalSystem.getLogicalComputingPlatformLinks().add(links);
        }

        documentRoot.setLogicalSystem(ecoaLogicalSystem);
        return documentRoot;
    }

    private static technology.ecoa.logicalsystem._2.LogicalComputingPlatform recreateLogicalComputingPlatform(
            edtlogical.LogicalComputingPlatform edtPlatform) {
        var ecoaPlatform = LOGFACTORY.createLogicalComputingPlatform();

        ecoaPlatform.setId(edtPlatform.getId());
        if (edtPlatform.isSetELIPlatformId()) {
            ecoaPlatform.setELIPlatformId(edtPlatform.getELIPlatformId());
        }

        // Convert LogicalComputingNodes
        EList<LogicalComputingNode> nodes = edtPlatform.getLogicalComputingNodes();
        for (LogicalComputingNode node : nodes) {
            ecoaPlatform.getLogicalComputingNode().add(recreateLogicalComputingNode(node));
        }

        // Convert LogicalComputingNodeLinks
        EList<LogicalComputingNodeLink> nodeLinks = edtPlatform.getLogicalComputingNodeLinks();
        if (!nodeLinks.isEmpty()) {
            LogicalComputingNodeLinks links = LOGFACTORY.createLogicalComputingNodeLinks();
            for (LogicalComputingNodeLink link : nodeLinks) {
                links.getLink().add(recreateNodeLink(link));
            }
            ecoaPlatform.getLogicalComputingNodeLinks().add(links);
        }

        return ecoaPlatform;
    }

    private static LinkType recreatePlatformLink(LogicalComputingPlatformLink edtLink) {
        LinkType link = LOGFACTORY.createLinkType();

        link.setId(edtLink.getId());
        if (edtLink.getFrom() != null) {
            link.setFrom(edtLink.getFrom().getId());
        }
        if (edtLink.getTo() != null) {
            link.setTo(edtLink.getTo().getId());
        }

        if (edtLink.getLatencyMicroSeconds() != null) {
            LatencyType latency = LOGFACTORY.createLatencyType();
            latency.setMicroSeconds(edtLink.getLatencyMicroSeconds());
            link.setLatency(latency);
        }

        if (edtLink.getThroughputMegaBytesPerSecond() != null) {
            ThroughputType throughput = LOGFACTORY.createThroughputType();
            throughput.setMegaBytesPerSecond(edtLink.getThroughputMegaBytesPerSecond());
            link.setThroughput(throughput);
        }

        if (edtLink.getTransportBindingProtocol() != null || edtLink.getTransportBindingParameters() != null) {
            TransportBindingType transport = LOGFACTORY.createTransportBindingType();
            transport.setParameters(edtLink.getTransportBindingParameters());
            transport.setProtocol(edtLink.getTransportBindingProtocol());
            link.setTransportBinding(transport);
        }

        return link;
    }

    private static LinkType1 recreateNodeLink(LogicalComputingNodeLink edtLink) {
        LinkType1 link = LOGFACTORY.createLinkType1();

        if (edtLink.getId() != null) {
            link.setId(edtLink.getId());
        }
        if (edtLink.getFrom() != null) {
            link.setFrom(edtLink.getFrom().getId());
        }
        if (edtLink.getTo() != null) {
            link.setTo(edtLink.getTo().getId());
        }

        if (edtLink.getLatencyMicroSeconds() != null) {
            LatencyType1 latency = LOGFACTORY.createLatencyType1();
            latency.setMicroSeconds(edtLink.getLatencyMicroSeconds());
            link.setLatency(latency);
        }

        if (edtLink.getThroughputMegaBytesPerSecond() != null) {
            ThroughputType1 throughput = LOGFACTORY.createThroughputType1();
            throughput.setMegaBytesPerSecond(edtLink.getThroughputMegaBytesPerSecond());
            link.setThroughput(throughput);
        }

        return link;
    }

    private static LogicalComputingNodeType recreateLogicalComputingNode(LogicalComputingNode edtNode) {
        LogicalComputingNodeType node = LOGFACTORY.createLogicalComputingNodeType();

        node.setId(edtNode.getId());

        if (edtNode.getAvailableMemoryGigaBytes() != null) {
            AvailableMemoryType memory = LOGFACTORY.createAvailableMemoryType();
            memory.setGigaBytes(edtNode.getAvailableMemoryGigaBytes());
            node.setAvailableMemory(memory);
        }

        if (edtNode.getEndianessType() != null) {
            EndianessType endianess = LOGFACTORY.createEndianessType();
            endianess.setType(edtNode.getEndianessType());
            node.setEndianess(endianess);
        }

        if (edtNode.getModuleSwitchTimeMicroSeconds() != null) {
            ModuleSwitchTimeType switchTime = LOGFACTORY.createModuleSwitchTimeType();
            switchTime.setMicroSeconds(edtNode.getModuleSwitchTimeMicroSeconds());
            node.setModuleSwitchTime(switchTime);
        }

        if (edtNode.isSetOsName()) {
            OsType os = LOGFACTORY.createOsType();
            os.setName(edtNode.getOsName());
            os.setVersion(edtNode.getOsVersion());
            node.setOs(os);
        }

        // Convert LogicalProcessors
        EList<LogicalProcessor> processors = edtNode.getLogicalProcessors();
        for (LogicalProcessor processor : processors) {
            node.getLogicalProcessors().add(recreateLogicalProcessor(processor));
        }

        return node;
    }

    private static LogicalProcessorsType recreateLogicalProcessor(LogicalProcessor edtProcessor) {
        LogicalProcessorsType processor = LOGFACTORY.createLogicalProcessorsType();

        processor.setNumber(edtProcessor.getNumber());
        processor.setType(edtProcessor.getType());

        if (edtProcessor.getStepDurationNanoSeconds() != null) {
            StepDurationType duration = LOGFACTORY.createStepDurationType();
            duration.setNanoSeconds(edtProcessor.getStepDurationNanoSeconds());
            processor.setStepDuration(duration);
        }

        return processor;
    }
}
