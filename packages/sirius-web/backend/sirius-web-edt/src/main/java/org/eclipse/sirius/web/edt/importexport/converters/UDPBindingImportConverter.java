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

import edtudp.EdtudpFactory;
import edtudp.UDPBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import technology.ecoa.udpbinding._2.PlatformType;
import technology.ecoa.udpbinding._2.udpFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * Imports a {name}.udp-binding.xml file into an edtudp.UDPBinding object.
 */
public final class UDPBindingImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UDPBindingImportConverter.class);

    private UDPBindingImportConverter() { }

    /**
     * Parse a .udp-binding.xml file and return an EDT UDPBinding object.
     * The binding name is derived from the file name (strip .udp-binding.xml suffix).
     */
    public static UDPBinding parse(Path path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            var doc = dbf.newDocumentBuilder().parse(path.toFile());

            UDPBinding binding = EdtudpFactory.eINSTANCE.createUDPBinding();

            // Derive name from file name
            String fileName = path.getFileName().toString();
            String bindingName = fileName.endsWith(".udp-binding.xml")
                    ? fileName.substring(0, fileName.length() - ".udp-binding.xml".length())
                    : fileName;
            binding.setName(bindingName);

            NodeList platformNodes = doc.getElementsByTagNameNS("*", "platform");
            for (int i = 0; i < platformNodes.getLength(); i++) {
                Element el = (Element) platformNodes.item(i);
                PlatformType platform = udpFactory.eINSTANCE.createPlatformType();

                String platformIdStr = el.getAttribute("platformId");
                if (!platformIdStr.isBlank()) {
                    try { platform.setPlatformId(Long.parseLong(platformIdStr)); } catch (NumberFormatException e) { /* skip */ }
                }
                String name = el.getAttribute("name");
                if (!name.isBlank()) platform.setName(name);

                String port = el.getAttribute("receivingPort");
                if (!port.isBlank()) {
                    try { platform.setReceivingPort(new BigInteger(port)); } catch (NumberFormatException e) { /* skip */ }
                }
                String multicast = el.getAttribute("receivingMulticastAddress");
                if (!multicast.isBlank()) platform.setReceivingMulticastAddress(multicast);

                String maxChannels = el.getAttribute("maxChannels");
                if (!maxChannels.isBlank()) {
                    try { platform.setMaxChannels(new BigInteger(maxChannels)); } catch (NumberFormatException e) { /* skip */ }
                }

                binding.getPlatform().add(platform);
            }

            return binding;
        } catch (Exception e) {
            LOGGER.error("Failed to import udp-binding.xml: {}", path, e);
            return null;
        }
    }

} // UDPBindingImportConverter
