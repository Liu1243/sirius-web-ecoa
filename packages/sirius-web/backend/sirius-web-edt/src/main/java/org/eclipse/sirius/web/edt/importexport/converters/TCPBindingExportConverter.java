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

import edttcp.TCPBinding;
import edttcp.TCPPlatform;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

/**
 * Exports edttcp.TCPBinding to the vendor-defined tcp-params.xml format.
 * Format: <TCPBinding xmlns="http://www.ecoa.technology/tcpbinding">
 *           <platform name="..." address="..." port="..."/>
 *         </TCPBinding>
 */
public final class TCPBindingExportConverter {

    public static final String TCP_NS = "http://www.ecoa.technology/tcpbinding";

    private TCPBindingExportConverter() { }

    public static byte[] toXmlBytes(TCPBinding tcpBinding) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(TCP_NS, "TCPBinding");
            root.setAttribute("xmlns", TCP_NS);
            doc.appendChild(root);

            for (TCPPlatform platform : tcpBinding.getPlatform()) {
                Element platformEl = doc.createElement("platform");
                platformEl.setAttribute("name",    safe(platform.getName()));
                platformEl.setAttribute("address", safe(platform.getAddress()));
                platformEl.setAttribute("port",    String.valueOf(platform.getPort()));
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
            throw new IllegalStateException("Failed to export tcp-params.xml", e);
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

} // TCPBindingExportConverter
