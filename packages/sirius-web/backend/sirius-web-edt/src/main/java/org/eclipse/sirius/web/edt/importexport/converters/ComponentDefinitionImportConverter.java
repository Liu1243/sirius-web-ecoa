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

import edtproject.*;
import edtqos.ServiceInstanceQos;
import org.eclipse.emf.common.util.EList;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import org.open.oasis.docs.ns.opencsa.sca.sca.ComponentType;
import org.open.oasis.docs.ns.opencsa.sca.sca.ComponentTypeReference;
import org.open.oasis.docs.ns.opencsa.sca.sca.Property;
import org.open.oasis.docs.ns.opencsa.sca.sca.ServiceType;

import java.util.List;

/**
 * Convert imported ECOA ComponentDefinition objects to EDT objects.
 * Based on the original ComponentDefinitionImportConverter from edt-tmp.
 */
public class ComponentDefinitionImportConverter {

    private static final EDTProjectFactory EDTFACTORY = EDTProjectFactory.eINSTANCE;

    private ComponentDefinitionImportConverter() {
        // Utility class
    }

    /**
     * Create EDT ComponentDefinition from Ecoa ComponentDefinition.
     */
    public static ComponentDefinition createEDTComponentDefinition(ComponentType ecoaComponentDefinition,
                                                                   String fileName, Step0 typeStep, List<ServiceInstanceQos> edtServiceQosList, Step1 step1,
                                                                   StringBuilder missingElementsToLog) throws FailedImportException {

        // Create empty EDTComponentDefinition
        ComponentDefinition edtComponentDefinition = EDTFACTORY.createComponentDefinition();

        // Add QoS
        if (edtServiceQosList != null) {
            edtComponentDefinition.getAssociatedServiceQos().addAll(edtServiceQosList);
        }

        // Fill EDTComponentDefinition
        String name = EdtProjectImportConverter.getObjectName(fileName, ".componentType");
        edtComponentDefinition.setName(name);

        EList<Property> ecoaProperties = ecoaComponentDefinition.getProperty();
        for (Property ecoaProperty : ecoaProperties) {
            edtComponentDefinition.getProperties()
                    .add(EdtProjectImportConverter.createEDTProperty(ecoaProperty, typeStep, missingElementsToLog));
        }

        EList<ComponentTypeReference> ecoaComponentTypeReferences = ecoaComponentDefinition.getReference();
        for (ComponentTypeReference ecoaRef : ecoaComponentTypeReferences) {
            edtComponentDefinition.getReferences()
                    .add(createEDTComponentDefinitionReference(step1, ecoaRef, edtServiceQosList));
        }

        EList<ServiceType> ecoaComponentTypeServices = ecoaComponentDefinition.getService();
        for (ServiceType ecoaService : ecoaComponentTypeServices) {
            edtComponentDefinition.getServices()
                    .add(createEDTComponentDefinitionService(step1, ecoaService, edtServiceQosList));
        }

        return edtComponentDefinition;
    }

    private static ComponentDefinitionService createEDTComponentDefinitionService(Step1 step1,
                                                                                  ServiceType componentTypeService, List<ServiceInstanceQos> edtServiceQosList) throws FailedImportException {
        
        ComponentDefinitionService edtService = EDTFACTORY.createComponentDefinitionService();

        EdtProjectImportConverter.createEDTInterface(edtService, componentTypeService.getInterface(),
                edtServiceQosList, step1);

        edtService.setName(componentTypeService.getName());

        return edtService;
    }

    private static ComponentDefinitionReference createEDTComponentDefinitionReference(Step1 step1,
                                                                                      ComponentTypeReference componentTypeReference, List<ServiceInstanceQos> edtServiceQosList)
            throws FailedImportException {

        ComponentDefinitionReference edtReference = EDTFACTORY.createComponentDefinitionReference();

        EdtProjectImportConverter.createEDTInterface(edtReference,
                componentTypeReference.getInterface(), edtServiceQosList, step1);

        edtReference.setMultiplicity(componentTypeReference.getMultiplicity());
        edtReference.setName(componentTypeReference.getName());

        return edtReference;
    }
}
