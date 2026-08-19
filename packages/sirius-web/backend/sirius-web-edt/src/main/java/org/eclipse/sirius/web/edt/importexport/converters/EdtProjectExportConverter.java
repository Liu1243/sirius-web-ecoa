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

import edttype.EDTDataType;
import edttype.Library;
import org.eclipse.emf.ecore.xml.type.XMLTypeFactory;
import org.open.oasis.docs.ns.opencsa.sca.sca.Property;
import org.open.oasis.docs.ns.opencsa.sca.sca.ValueType;
import org.open.oasis.docs.ns.opencsa.sca.sca.scaFactory;
import org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage;
import technology.ecoa.sca.extension.scaExt.scaExtPackage;

/**
 * Converts EDT project objects to ECOA project.xml format.
 * Based on the original EDTProjectExportConverter from edt-tmp.
 */
public class EdtProjectExportConverter {

    private EdtProjectExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT Property to ECOA SCA Property.
     */
    public static Property recreateProperty(edtproject.Property edtProperty) {
        scaFactory factory = scaFactory.eINSTANCE;
        Property property = factory.createProperty();

        property.setName(edtProperty.getName());

        if (edtProperty.isSetMustSupply()) {
            property.setMustSupply(edtProperty.isMustSupply());
        }

        // Set type to xsd:string
        var scaType = XMLTypeFactory.eINSTANCE.createQName("http://www.w3.org/2001/XMLSchema", "string");
        property.setType(scaType);

        // Set value
        if (edtProperty.getValue() != null && !edtProperty.getValue().isBlank()) {
            property.getAny().add(scaPackage.Literals.DOCUMENT_ROOT__VALUE, recreateValueType(edtProperty.getValue()));
        }

        // Set ECOA-SCA type if present
        EDTDataType ecoaScaType = edtProperty.getECOASCAType();
        if (ecoaScaType != null) {
            String ecoaScaTypeValue;
            if (EDTDataType.isBasicOrPredefined(ecoaScaType)) {
                ecoaScaTypeValue = "ECOA:" + ecoaScaType.getName();
            } else {
                String namespace = ((Library) ecoaScaType.eContainer()).getName();
                ecoaScaTypeValue = namespace + ":" + ecoaScaType.getName();
            }
            property.getAnyAttribute().add(scaExtPackage.Literals.DOCUMENT_ROOT__TYPE, ecoaScaTypeValue);
        }

        // Set ECOA-SCA library if present
        if (edtProperty.getECOASCALibrary() != null) {
            property.getAnyAttribute().add(
                    org.eclipse.emf.ecore.util.BasicExtendedMetaData.INSTANCE.demandFeature(
                            "http://www.ecoa.technology/sca-extension-2.0", "library", false),
                    edtProperty.getECOASCALibrary().getName());
        }

        return property;
    }

    /**
     * Create ValueType from string value.
     */
    public static ValueType recreateValueType(String value) {
        var valueType = scaFactory.eINSTANCE.createValueType();
        valueType.getMixed().add(org.eclipse.emf.ecore.xml.type.XMLTypePackage.Literals.XML_TYPE_DOCUMENT_ROOT__TEXT, value);
        return valueType;
    }
}
