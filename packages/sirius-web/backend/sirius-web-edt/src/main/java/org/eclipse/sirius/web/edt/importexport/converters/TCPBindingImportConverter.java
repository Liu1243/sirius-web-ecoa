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

import edttcp.EdttcpFactory;
import edttcp.TCPBinding;
import edttcp.TCPPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

/**
 * Imports a {name}.tcp-params.xml file into an edttcp.TCPBinding object.
 */
public final class TCPBindingImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TCPBindingImportConverter.class);

    private TCPBindingImportConverter() { }

    /**
     * Parse a .tcp-params.xml file and return an EDT TCPBinding object.
     */
    public static TCPBinding parse(Path path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            var doc = dbf.newDocumentBuilder().parse(path.toFile());

            TCPBinding binding = EdttcpFactory.eINSTANCE.createTCPBinding();

            String fileName = path.getFileName().toString();
            String bindingName = fileName.endsWith(".tcp-params.xml")
                    ? fileName.substring(0, fileName.length() - ".tcp-params.xml".length())
                    : fileName;
            binding.setName(bindingName);

            NodeList platformNodes = doc.getElementsByTagNameNS("*", "platform");
            for (int i = 0; i < platformNodes.getLength(); i++) {
                Element el = (Element) platformNodes.item(i);
                TCPPlatform platform = EdttcpFactory.eINSTANCE.createTCPPlatform();

                String name = el.getAttribute("name");
                if (!name.isBlank()) platform.setName(name);

                String address = el.getAttribute("address");
                if (!address.isBlank()) platform.setAddress(address);

                String portStr = el.getAttribute("port");
                if (!portStr.isBlank()) {
                    try { platform.setPort(Integer.parseInt(portStr)); } catch (NumberFormatException e) { /* skip */ }
                }

                binding.getPlatform().add(platform);
            }

            return binding;
        } catch (Exception e) {
            LOGGER.error("Failed to import tcp-params.xml: {}", path, e);
            return null;
        }
    }

} // TCPBindingImportConverter
