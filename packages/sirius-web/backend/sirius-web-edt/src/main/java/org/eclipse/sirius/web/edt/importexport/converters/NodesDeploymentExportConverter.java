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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exports the custom Step5 nodes deployment file.
 */
public final class NodesDeploymentExportConverter {

    private NodesDeploymentExportConverter() {
        // Utility class
    }

    /**
     * Returns the XML bytes for nodes_deployment.xml, or {@code Optional.empty()} when
     * no platform or node has a non-blank IP address (i.e. the file would be meaningless
     * and would fail XSD validation since ipAddress is a required IP-format attribute).
     */
    public static Optional<byte[]> toXmlBytes(LogicalSystem logicalSystem) {
        Optional<String> mainIpAddress = firstMainIpAddress(logicalSystem);
        List<LogicalComputingNode> nodesWithIp = flattenNodesWithIp(logicalSystem);

        if (mainIpAddress.isEmpty() && nodesWithIp.isEmpty()) {
            return Optional.empty();
        }

        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);

            Document document = documentBuilderFactory.newDocumentBuilder().newDocument();
            Element root = document.createElement("nodesDeployment");
            document.appendChild(root);

            if (mainIpAddress.isPresent()) {
                Element mainElement = document.createElement("logicalComputingNode");
                mainElement.setAttribute("id", "main");
                mainElement.setAttribute("ipAddress", mainIpAddress.get());
                root.appendChild(mainElement);
            }

            for (LogicalComputingNode node : nodesWithIp) {
                if (mainIpAddress.isPresent() && "main".equals(node.getId())) {
                    continue;
                }
                Element nodeElement = document.createElement("logicalComputingNode");
                nodeElement.setAttribute("id", safe(node.getId()));
                nodeElement.setAttribute("ipAddress", node.getIpAddress());
                root.appendChild(nodeElement);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes(StandardCharsets.UTF_8));
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            return Optional.of(outputStream.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to export nodes_deployment.xml", exception);
        }
    }

    private static Optional<String> firstMainIpAddress(LogicalSystem logicalSystem) {
        if (logicalSystem == null) {
            return Optional.empty();
        }
        return logicalSystem.getLogicalComputingPlatforms().stream()
                .map(LogicalComputingPlatform::getIpAddress)
                .filter(ipAddress -> ipAddress != null && !ipAddress.isBlank())
                .findFirst();
    }

    private static List<LogicalComputingNode> flattenNodesWithIp(LogicalSystem logicalSystem) {
        List<LogicalComputingNode> nodes = new ArrayList<>();
        if (logicalSystem == null) {
            return nodes;
        }
        for (LogicalComputingPlatform platform : logicalSystem.getLogicalComputingPlatforms()) {
            for (LogicalComputingNode node : platform.getLogicalComputingNodes()) {
                if (node.getIpAddress() != null && !node.getIpAddress().isBlank()) {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
