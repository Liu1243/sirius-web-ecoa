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

import org.eclipse.sirius.web.edt.importexport.FailedImportException;

import edtlogical.EdtlogicalFactory;
import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingNodeLink;
import edtlogical.LogicalComputingPlatform;
import edtlogical.LogicalComputingPlatformLink;
import edtlogical.LogicalProcessor;
import edtlogical.LogicalSystem;
import technology.ecoa.logicalsystem._2.LinkType;
import technology.ecoa.logicalsystem._2.LinkType1;
import technology.ecoa.logicalsystem._2.LogicalComputingNodeLinks;
import technology.ecoa.logicalsystem._2.LogicalComputingNodeType;
import technology.ecoa.logicalsystem._2.LogicalComputingPlatformLinks;
import technology.ecoa.logicalsystem._2.LogicalProcessorsType;
import technology.ecoa.logicalsystem._2.OsType;

/**
 * Convert imported ECOA LogicalSystem objects to EDT objects.
 */
public class LogicalSystemImportConverter {

    private static final EdtlogicalFactory EDTLOGICALFACTORY = EdtlogicalFactory.eINSTANCE;

    private LogicalSystemImportConverter() {
        // Utility class
    }

    public static LogicalSystem createEDTLogicalSystem(technology.ecoa.logicalsystem._2.LogicalSystem ecoaLogicalSystem, String fileName)
            throws FailedImportException {
        
        var edtLogicalSystem = EDTLOGICALFACTORY.createLogicalSystem();
        edtLogicalSystem.setId(ecoaLogicalSystem.getId());
        edtLogicalSystem.setFileNamePrefix(EdtProjectImportConverter.getObjectName(fileName, ".logical-system.xml"));

        for (technology.ecoa.logicalsystem._2.LogicalComputingPlatform ecoaPlatform : ecoaLogicalSystem.getLogicalComputingPlatform()) {
            edtLogicalSystem.getLogicalComputingPlatforms().add(createEDTLogicalComputingPlatform(ecoaPlatform));
        }

        for (LogicalComputingPlatformLinks ecoaLinks : ecoaLogicalSystem.getLogicalComputingPlatformLinks()) {
            // Note: In EDT LogicalSystem has a list of LogicalComputingPlatformLink objects directly, 
            // but ECOA XML structure groups them in LogicalComputingPlatformLinks container.
            // We need to flatten or handle as per EDT model. 
            // The original code does: edtLogicalSystem.getLogicalComputingPlatformLinks().add(edtLogicalComputingPlatformLink)
            // But iterate over links inside the container.
            
            // Wait, createEDTLogicalComputingPlatformLinks returns ONE link? 
            // Original code loop: for (LogicalComputingPlatformLinks ecoa... : ...) { ... createEDT...(..., ecoa...) }
            // And inside createEDT... it iterates ecoaPlatformLink.getLink() and seems to overwrite properties?
            // Actually original code createEDTLogicalComputingPlatformLinks returns a Link, but inside it loops!
            // This looks like a bug in original code or I misread it. 
            // "edtLogicalComputingPlatformLink.setId(ecoaPlatformLink.getId());" inside loop means it overwrites.
            // But it returns the object after the loop.
            // I should verify this.
            
            // Correct approach: Each LinkType in LinkType container should produce one LogicalComputingPlatformLink in EDT.
            
             for (LinkType ecoaLink : ecoaLinks.getLink()) {
                 edtLogicalSystem.getLogicalComputingPlatformLinks().add(createEDTLogicalComputingPlatformLink(edtLogicalSystem, ecoaLink));
             }
        }
        
        return edtLogicalSystem;
    }

    private static LogicalComputingPlatform createEDTLogicalComputingPlatform(
            technology.ecoa.logicalsystem._2.LogicalComputingPlatform ecoaLogicalComputingPlatform) throws FailedImportException {
        
        LogicalComputingPlatform edtLogicalComputingPlatform = EDTLOGICALFACTORY.createLogicalComputingPlatform();
        if (ecoaLogicalComputingPlatform.isSetELIPlatformId()) {
            edtLogicalComputingPlatform.setELIPlatformId(ecoaLogicalComputingPlatform.getELIPlatformId());
        }
        edtLogicalComputingPlatform.setId(ecoaLogicalComputingPlatform.getId());
        
        for (LogicalComputingNodeType ecoaNode : ecoaLogicalComputingPlatform.getLogicalComputingNode()) {
            edtLogicalComputingPlatform.getLogicalComputingNodes().add(createEDTLogicalComputingNode(ecoaNode));
        }

        for (LogicalComputingNodeLinks ecoaNodeLinks : ecoaLogicalComputingPlatform.getLogicalComputingNodeLinks()) {
             for (LinkType1 ecoaLink : ecoaNodeLinks.getLink()) {
                 edtLogicalComputingPlatform.getLogicalComputingNodeLinks().add(createEDTLogicalComputingNodeLink(edtLogicalComputingPlatform, ecoaLink));
             }
        }
        return edtLogicalComputingPlatform;
    }

    private static LogicalComputingPlatformLink createEDTLogicalComputingPlatformLink(
            LogicalSystem edtLogicalSystem, LinkType ecoaPlatformLink) throws FailedImportException {
        
        LogicalComputingPlatformLink edtLink = EDTLOGICALFACTORY.createLogicalComputingPlatformLink();
        edtLink.setId(ecoaPlatformLink.getId());
        
        if (ecoaPlatformLink.getLatency() != null) {
            edtLink.setLatencyMicroSeconds(ecoaPlatformLink.getLatency().getMicroSeconds());
        }
        if (ecoaPlatformLink.getThroughput() != null) {
            edtLink.setThroughputMegaBytesPerSecond(ecoaPlatformLink.getThroughput().getMegaBytesPerSecond());
        }
        if (ecoaPlatformLink.getTransportBinding() != null) {
            edtLink.setTransportBindingParameters(ecoaPlatformLink.getTransportBinding().getParameters());
            edtLink.setTransportBindingProtocol(ecoaPlatformLink.getTransportBinding().getProtocol());
        }

        LogicalComputingPlatform fromPlatform = edtLogicalSystem.findLogicalComputingPlatformById(ecoaPlatformLink.getFrom());
        LogicalComputingPlatform toPlatform = edtLogicalSystem.findLogicalComputingPlatformById(ecoaPlatformLink.getTo());
        
        if (fromPlatform != null) edtLink.setFrom(fromPlatform);
        else throw new FailedImportException("No LogicalComputingPlatform found with name :" + ecoaPlatformLink.getFrom());
        
        if (toPlatform != null) edtLink.setTo(toPlatform);
        else throw new FailedImportException("No LogicalComputingPlatform found with name :" + ecoaPlatformLink.getTo());

        return edtLink;
    }

    private static LogicalComputingNodeLink createEDTLogicalComputingNodeLink(
            LogicalComputingPlatform edtLogicalPlatform, LinkType1 ecoaNodeLink) throws FailedImportException {
        
        LogicalComputingNodeLink edtLink = EDTLOGICALFACTORY.createLogicalComputingNodeLink();
        edtLink.setId(ecoaNodeLink.getId());
        
        if (ecoaNodeLink.getLatency() != null) {
            edtLink.setLatencyMicroSeconds(ecoaNodeLink.getLatency().getMicroSeconds());
        }
        if (ecoaNodeLink.getThroughput() != null) {
            edtLink.setThroughputMegaBytesPerSecond(ecoaNodeLink.getThroughput().getMegaBytesPerSecond());
        }

        LogicalComputingNode fromNode = edtLogicalPlatform.findLogicalComputingNodeById(ecoaNodeLink.getFrom());
        LogicalComputingNode toNode = edtLogicalPlatform.findLogicalComputingNodeById(ecoaNodeLink.getTo());
        
        if (fromNode != null) edtLink.setFrom(fromNode);
        else throw new FailedImportException("No LogicalComputingNode found with name :" + ecoaNodeLink.getFrom());
        
        if (toNode != null) edtLink.setTo(toNode);
        else throw new FailedImportException("No LogicalComputingNode found with name :" + ecoaNodeLink.getTo());
        
        return edtLink;
    }

    private static LogicalComputingNode createEDTLogicalComputingNode(LogicalComputingNodeType ecoaLogicalComputingNode) {
        LogicalComputingNode edtNode = EDTLOGICALFACTORY.createLogicalComputingNode();

        edtNode.setId(ecoaLogicalComputingNode.getId());
        if (ecoaLogicalComputingNode.getAvailableMemory() != null) {
            edtNode.setAvailableMemoryGigaBytes(ecoaLogicalComputingNode.getAvailableMemory().getGigaBytes());
        }
        if (ecoaLogicalComputingNode.getEndianess() != null) {
            edtNode.setEndianessType(ecoaLogicalComputingNode.getEndianess().getType());
        }
        if (ecoaLogicalComputingNode.getModuleSwitchTime() != null) {
            edtNode.setModuleSwitchTimeMicroSeconds(ecoaLogicalComputingNode.getModuleSwitchTime().getMicroSeconds());
        }

        OsType os = ecoaLogicalComputingNode.getOs();
        if (os != null) {
            if (os.isSetName()) edtNode.setOsName(os.getName());
            edtNode.setOsVersion(os.getVersion());
        }

        for (LogicalProcessorsType ecoaProcessor : ecoaLogicalComputingNode.getLogicalProcessors()) {
            edtNode.getLogicalProcessors().add(createEDTLogicalProcessor(ecoaProcessor));
        }
        return edtNode;
    }

    private static LogicalProcessor createEDTLogicalProcessor(LogicalProcessorsType ecoaLogicalProcessor) {
        LogicalProcessor edtProc = EDTLOGICALFACTORY.createLogicalProcessor();
        edtProc.setNumber(ecoaLogicalProcessor.getNumber());
        if (ecoaLogicalProcessor.getStepDuration() != null) {
            edtProc.setStepDurationNanoSeconds(ecoaLogicalProcessor.getStepDuration().getNanoSeconds());
        }
        edtProc.setType(ecoaLogicalProcessor.getType());
        return edtProc;
    }
}
