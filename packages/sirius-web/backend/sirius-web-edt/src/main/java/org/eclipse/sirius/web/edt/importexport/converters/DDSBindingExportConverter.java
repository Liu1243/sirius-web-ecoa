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

import edtdds.DDSBinding;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

/**
 * Exports edtdds.DDSBinding to the {name}.dds-binding.xml format.
 * Schema: http://www.ecoa.technology/ddsbinding (schemas/ecoa-ddsbinding.xsd)
 */
public final class DDSBindingExportConverter {

    static final String DDS_NS = "http://www.ecoa.technology/ddsbinding";
    static final String DEFAULT_TOPIC = "LdpLocalPeerData";

    private DDSBindingExportConverter() { }

    public static byte[] toXmlBytes(DDSBinding binding) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(DDS_NS, "DDSBinding");
            root.setAttribute("xmlns", DDS_NS);
            doc.appendChild(root);

            Element domainEl = doc.createElement("domain");
            domainEl.setAttribute("id", String.valueOf(binding.getDomainId()));
            root.appendChild(domainEl);

            String topic = binding.getTopicName();
            if (topic != null && !topic.isBlank() && !DEFAULT_TOPIC.equals(topic)) {
                Element topicEl = doc.createElement("topic");
                topicEl.setAttribute("name", topic);
                root.appendChild(topicEl);
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
            throw new IllegalStateException("Failed to export dds-binding.xml", e);
        }
    }

} // DDSBindingExportConverter
