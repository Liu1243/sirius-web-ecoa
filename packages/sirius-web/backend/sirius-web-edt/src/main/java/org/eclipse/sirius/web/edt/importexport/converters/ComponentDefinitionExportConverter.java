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

import edtproject.ComponentDefinition;
import edtproject.ComponentDefinitionReference;
import edtproject.ComponentDefinitionService;
import org.eclipse.emf.common.util.EList;
import org.open.oasis.docs.ns.opencsa.sca.sca.*;
import technology.ecoa.sca.extension.scaExt.Interface;
import technology.ecoa.sca.extension.scaExt.scaExtFactory;

/**
 * Converts EDT ComponentDefinition objects to ECOA SCA ComponentType format.
 * Based on the original ComponentDefinitionExportConverter from edt-tmp.
 */
public class ComponentDefinitionExportConverter {

    private static final scaFactory SCAFACTORY = scaFactory.eINSTANCE;

    private ComponentDefinitionExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT ComponentDefinition to ECOA ComponentType.
     *
     * @param edtComponentDefinition the EDT ComponentDefinition to convert
     * @return DocumentRoot containing the ComponentType
     */
    public static DocumentRoot recreateComponentType(ComponentDefinition edtComponentDefinition) {
        ComponentType ecoaComponentType = SCAFACTORY.createComponentType();

        // Recreate Properties
        EList<edtproject.Property> edtProperties = edtComponentDefinition.getProperties();
        for (edtproject.Property edtProperty : edtProperties) {
            ecoaComponentType.getProperty().add(EdtProjectExportConverter.recreateProperty(edtProperty));
        }

        // Recreate References
        EList<ComponentDefinitionReference> edtReferences = edtComponentDefinition.getReferences();
        for (ComponentDefinitionReference edtRef : edtReferences) {
            ecoaComponentType.getReference().add(recreateComponentTypeReference(edtRef));
        }

        // Recreate Services
        EList<ComponentDefinitionService> edtServices = edtComponentDefinition.getServices();
        for (ComponentDefinitionService edtService : edtServices) {
            ecoaComponentType.getService().add(recreateServiceType(edtService));
        }

        DocumentRoot documentRoot = SCAFACTORY.createDocumentRoot();
        documentRoot.setComponentType(ecoaComponentType);
        return documentRoot;
    }

    private static ServiceType recreateServiceType(ComponentDefinitionService edtService) {
        ServiceType serviceType = SCAFACTORY.createServiceType();
        serviceType.setName(edtService.getName());
        serviceType.setInterface(recreateInterface(edtService));
        return serviceType;
    }

    private static ComponentTypeReference recreateComponentTypeReference(ComponentDefinitionReference edtRef) {
        ComponentTypeReference reference = SCAFACTORY.createComponentTypeReference();
        reference.setName(edtRef.getName());
        reference.setInterface(recreateInterface(edtRef));
        return reference;
    }

    /**
     * Recreate ECOA SCA Extension Interface from EDT Contract.
     */
    public static Interface recreateInterface(edtproject.Contract edtContract) {
        Interface ecoaInterface = scaExtFactory.eINSTANCE.createInterface();

        if (edtContract.getQos() != null) {
            ecoaInterface.setQos(edtContract.getQos().getName());
        }
        if (edtContract.getSyntax() != null) {
            ecoaInterface.setSyntax(edtContract.getSyntax().getName());
        }

        return ecoaInterface;
    }
}
