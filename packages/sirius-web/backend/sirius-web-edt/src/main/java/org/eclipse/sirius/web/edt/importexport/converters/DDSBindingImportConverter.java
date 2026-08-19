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
import edtdds.EdtddsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

/**
 * Imports a {name}.dds-binding.xml file into an edtdds.DDSBinding object.
 */
public final class DDSBindingImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DDSBindingImportConverter.class);

    private DDSBindingImportConverter() { }

    public static DDSBinding parse(Path path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            var doc = dbf.newDocumentBuilder().parse(path.toFile());

            DDSBinding binding = EdtddsFactory.eINSTANCE.createDDSBinding();

            String fileName = path.getFileName().toString();
            binding.setName(fileName.endsWith(".dds-binding.xml")
                    ? fileName.substring(0, fileName.length() - ".dds-binding.xml".length())
                    : fileName);

            // Parse <domain id="N"/>
            var domainNodes = doc.getElementsByTagNameNS("*", "domain");
            if (domainNodes.getLength() == 0) domainNodes = doc.getElementsByTagName("domain");
            if (domainNodes.getLength() > 0) {
                String idStr = ((Element) domainNodes.item(0)).getAttribute("id");
                if (!idStr.isBlank()) {
                    try { binding.setDomainId(Integer.parseInt(idStr)); } catch (NumberFormatException e) { /* keep default */ }
                }
            }

            // Parse optional <topic name="..."/>
            var topicNodes = doc.getElementsByTagNameNS("*", "topic");
            if (topicNodes.getLength() == 0) topicNodes = doc.getElementsByTagName("topic");
            if (topicNodes.getLength() > 0) {
                String name = ((Element) topicNodes.item(0)).getAttribute("name");
                if (!name.isBlank()) binding.setTopicName(name);
            }

            return binding;
        } catch (Exception e) {
            LOGGER.error("Failed to import dds-binding.xml: {}", path, e);
            return null;
        }
    }

} // DDSBindingImportConverter
