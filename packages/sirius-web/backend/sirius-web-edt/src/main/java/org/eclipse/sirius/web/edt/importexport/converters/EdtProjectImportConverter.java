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

import edtinterface.ServiceDefinition;
import edtproject.Contract;
import edtproject.EDTProjectFactory;
import edtproject.Step0;
import edtproject.Step1;
import edtqos.ServiceInstanceQos;
import edttype.EDTDataType;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import org.open.oasis.docs.ns.opencsa.sca.sca.Property;
import org.open.oasis.docs.ns.opencsa.sca.sca.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import technology.ecoa.sca.extension.scaExt.Interface;

import java.util.List;
import java.util.Objects;

/**
 * Utility class for converting common ECOA objects to EDT objects during import.
 * Based on the original EDTProjectImportConverter from edt-tmp.
 */
public class EdtProjectImportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtProjectImportConverter.class);
    private static final EDTProjectFactory EDTFACTORY = EDTProjectFactory.eINSTANCE;

    private EdtProjectImportConverter() {
        // Utility class
    }

    /**
     * Get name of file without extension.
     */
    public static String getObjectName(String fileName, String extension) {
        if (fileName.contains(extension)) {
            return fileName.substring(0, fileName.lastIndexOf(extension));
        } else if (fileName.contains(".")) {
            String substring = fileName.substring(0, fileName.indexOf("."));
            LOGGER.warn("The filename {} does not contain {}. The name will be {}", fileName, extension, substring);
            return substring;
        } else {
            LOGGER.error("The filename {} does not contain {}", fileName, extension);
            return fileName;
        }
    }

    /**
     * Create EDT property from Ecoa property.
     */
    public static edtproject.Property createEDTProperty(Property ecoaProperty, Step0 typeStep,
            StringBuilder missingElementsToLog) throws FailedImportException {
        
        edtproject.Property edtProperty = EDTFACTORY.createProperty();
        edtProperty.setName(ecoaProperty.getName());
        
        if (ecoaProperty.isSetMustSupply()) {
            edtProperty.setMustSupply(ecoaProperty.isMustSupply());
        }

        // Verify and set type (must be xsd:string)
        if (!(ecoaProperty.getType() != null
                && Objects.equals(ecoaProperty.getType().getNamespaceURI(), "http://www.w3.org/2001/XMLSchema")
                && Objects.equals(ecoaProperty.getType().getLocalPart(), "string"))) {
            LOGGER.warn("The type of a property should have been xsd:string");
        }
        edtProperty.setType("xsd:string");

        // Get property value
        FeatureMap values = ecoaProperty.getAny();
        edtProperty.setValue(createPropertyEDTValue(values));

        // Get property any attribute: type and library
        FeatureMap propertyAnyAttribute = ecoaProperty.getAnyAttribute();
        for (int i = 0; i < propertyAnyAttribute.size(); i++) {
            Object featureName = propertyAnyAttribute.get(i).getEStructuralFeature().getName();
            String value = (String) propertyAnyAttribute.get(i).getValue();

            if (Objects.equals(featureName, "type")) {
                EDTDataType edtType = TypesImportConverter.findEDTDataTypeForNonTypes(typeStep, value);
                if (edtType != null) {
                    edtProperty.setECOASCAType(edtType);
                } else {
                    throw new FailedImportException("The type " + value + " was not found");
                }
            } else if (Objects.equals(featureName, "library")) {
                edttype.Library libraryToBeAssociated = typeStep.findLibrary(value);
                if (libraryToBeAssociated != null) {
                    edtProperty.setECOASCALibrary(libraryToBeAssociated);
                } else {
                    throw new FailedImportException("No Library was found with the name " + value);
                }
            } else {
                missingElementsToLog.append("WARNING : The attribute ").append(featureName)
                        .append(" is not managed in EDT (only type and library are managed), it will not be reexported\n");
            }
        }
        return edtProperty;
    }

    /**
     * Convert ValueType(s) to a String.
     */
    public static String createPropertyEDTValue(FeatureMap values) {
        StringBuilder valueToPut = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (values.getValue(i) instanceof ValueType value) {
                FeatureMap mixed = value.getMixed();
                for (int j = 0; j < mixed.size(); j++) {
                    if (!Objects.equals(mixed.getEStructuralFeature(j).getName(), "comment")) {
                        valueToPut.append(mixed.getValue(j));
                    }
                }
            }
        }
        return valueToPut.toString();
    }

    /**
     * Configure EDT Interface (Contract) from ECOA Interface.
     * When qos is specified, first try to find a matching ServiceInstanceQos.
     * If not found (e.g., empty QoS list), fallback to using the syntax attribute
     * to find a ServiceDefinition directly.
     */
    public static void createEDTInterface(Contract contract, Interface ecoaInterface,
                                          List<ServiceInstanceQos> edtServiceQosList, Step1 step1) throws FailedImportException {
        
        String qos = ecoaInterface.getQos();
        if (qos != null && !qos.isBlank()) {
            // Try to find ServiceInstanceQos by name
            edtqos.ServiceInstanceQos edtQoS = null;
            if (edtServiceQosList != null) {
                for (ServiceInstanceQos q : edtServiceQosList) {
                    if (Objects.equals(q.getName(), qos)) {
                        edtQoS = q;
                        break;
                    }
                }
            }

            if (edtQoS != null) {
                contract.setSyntax(edtQoS.getServiceDefinition());
                contract.setQos(edtQoS);
            } else {
                // QoS not found: fallback to syntax attribute to find ServiceDefinition
                String serviceDefinitionName = ecoaInterface.getSyntax();
                if (serviceDefinitionName != null && !serviceDefinitionName.isBlank()) {
                    ServiceDefinition serviceDefinition = step1.findServiceDefinitionByName(serviceDefinitionName);
                    if (serviceDefinition != null) {
                        LOGGER.warn("QoS '{}' not found, falling back to ServiceDefinition '{}' via syntax attribute", qos, serviceDefinitionName);
                        contract.setSyntax(serviceDefinition);
                    } else {
                        throw new FailedImportException("No ServiceInstanceQos was found with the name:" + qos
                                + ", and no ServiceDefinition was found with the name:" + serviceDefinitionName);
                    }
                } else {
                    throw new FailedImportException("No ServiceInstanceQos was found with the name:" + qos);
                }
            }
        } else {
            String serviceDefinitionName = ecoaInterface.getSyntax();
            ServiceDefinition serviceDefinition = step1.findServiceDefinitionByName(serviceDefinitionName);
            if (serviceDefinition != null) {
                contract.setSyntax(serviceDefinition);
            } else {
                throw new FailedImportException("No ServiceDefinition was found with the name:" + serviceDefinitionName);
            }
        }
    }
}
