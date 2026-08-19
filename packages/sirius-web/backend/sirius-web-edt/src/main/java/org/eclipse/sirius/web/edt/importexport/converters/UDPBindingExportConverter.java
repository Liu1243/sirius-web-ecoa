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

import edtudp.UDPBinding;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import technology.ecoa.udpbinding._2.PlatformType;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Exports edtudp.UDPBinding to the ECOA standard udp-binding.xml format.
 * Schema: http://www.ecoa.technology/udpbinding-2.0 (ecoa-udpbinding-2.0.xsd)
 */
public final class UDPBindingExportConverter {

    private UDPBindingExportConverter() { }

    public static byte[] toXmlBytes(UDPBinding udpBinding) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS("http://www.ecoa.technology/udpbinding-2.0", "UDPBinding");
            root.setAttribute("xmlns", "http://www.ecoa.technology/udpbinding-2.0");
            doc.appendChild(root);

            for (PlatformType platform : udpBinding.getPlatform()) {
                Element platformEl = doc.createElement("platform");
                platformEl.setAttribute("platformId", String.valueOf(platform.getPlatformId()));
                platformEl.setAttribute("name", safe(platform.getName()));
                platformEl.setAttribute("receivingPort", safe(platform.getReceivingPort()));
                platformEl.setAttribute("receivingMulticastAddress", safe(platform.getReceivingMulticastAddress()));
                BigInteger maxChannels = platform.getMaxChannels();
                if (maxChannels != null) {
                    platformEl.setAttribute("maxChannels", maxChannels.toString());
                }
                root.appendChild(platformEl);
            }

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export udp-binding.xml", e);
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

} // UDPBindingExportConverter
