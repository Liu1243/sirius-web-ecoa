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
package org.eclipse.sirius.web.edt.importexport.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Helper class for exporting EMF objects to XML bytes.
 */
public class XmlExporterHelper {

    private XmlExporterHelper() {
        // Utility class
    }

    /**
     * Serialize an EMF object to XML bytes.
     *
     * @param object
     *            the EMF object to serialize
     * @param extension
     *            the file extension for the resource (e.g., "xml", "composite")
     * @return the serialized XML as byte array
     * @throws IOException
     *             if serialization fails
     */
    public static byte[] serializeToXml(EObject object, String extension) throws IOException {
        // Register the XMI resource factory
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put(extension, new XMIResourceFactoryImpl());

        // Create a resource set
        ResourceSet resSet = new ResourceSetImpl();

        // Create a resource with a dummy URI
        URI dummyUri = URI.createURI("temp://export." + extension);
        Resource resource = resSet.createResource(dummyUri);

        // Add the object to the resource
        resource.getContents().add(object);

        // Configure save options for proper XML output
        Map<String, Object> saveOptions = new HashMap<>();
        saveOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        saveOptions.put(XMLResource.OPTION_ENCODING, "UTF-8");
        saveOptions.put(XMIResource.OPTION_SUPPRESS_XMI, Boolean.TRUE);
        saveOptions.put(XMLResource.OPTION_SAVE_TYPE_INFORMATION, Boolean.FALSE);

        // Serialize to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.save(outputStream, saveOptions);

        return outputStream.toByteArray();
    }

    /**
     * Create the standard save options for XML export.
     *
     * @return the save options map
     */
    public static Map<String, Object> createSaveOptions() {
        Map<String, Object> saveOptions = new HashMap<>();
        saveOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        saveOptions.put(XMLResource.OPTION_ENCODING, "UTF-8");
        saveOptions.put(XMIResource.OPTION_SUPPRESS_XMI, Boolean.TRUE);
        saveOptions.put(XMLResource.OPTION_SAVE_TYPE_INFORMATION, Boolean.FALSE);
        return saveOptions;
    }
}
