/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.importexport.converters;

import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingPlatform;
import edtlogical.LogicalSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Imports the custom Step5 nodes deployment file.
 */
public final class NodesDeploymentImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodesDeploymentImportConverter.class);

    private NodesDeploymentImportConverter() {
        // Utility class
    }

    public static void apply(Path path, LogicalSystem logicalSystem) throws IOException {
        if (logicalSystem == null) {
            return;
        }
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            var document = documentBuilderFactory.newDocumentBuilder().parse(path.toFile());
            NodeList nodeList = document.getElementsByTagName("logicalComputingNode");
            for (int index = 0; index < nodeList.getLength(); index++) {
                Element nodeElement = (Element) nodeList.item(index);
                String nodeId = nodeElement.getAttribute("id");
                String ipAddress = nodeElement.getAttribute("ipAddress");
                if ("main".equals(nodeId)) {
                    LogicalComputingPlatform logicalComputingPlatform = firstPlatform(logicalSystem);
                    if (logicalComputingPlatform != null) {
                        logicalComputingPlatform.setIpAddress(ipAddress);
                    }
                }
                LogicalComputingNode logicalComputingNode = findNode(logicalSystem, nodeId);
                if (logicalComputingNode == null) {
                    if ("main".equals(nodeId)) {
                        continue;
                    }
                    LOGGER.warn("nodes_deployment.xml references unknown logical node '{}'", nodeId);
                    continue;
                }
                logicalComputingNode.setIpAddress(ipAddress);
            }
        } catch (Exception exception) {
            throw new IOException("Failed to import nodes_deployment.xml", exception);
        }
    }

    private static LogicalComputingPlatform firstPlatform(LogicalSystem logicalSystem) {
        if (logicalSystem.getLogicalComputingPlatforms().isEmpty()) {
            return null;
        }
        return logicalSystem.getLogicalComputingPlatforms().get(0);
    }

    private static LogicalComputingNode findNode(LogicalSystem logicalSystem, String nodeId) {
        for (LogicalComputingPlatform platform : logicalSystem.getLogicalComputingPlatforms()) {
            LogicalComputingNode node = platform.findLogicalComputingNodeById(nodeId);
            if (node != null) {
                return node;
            }
        }
        return null;
    }
}
