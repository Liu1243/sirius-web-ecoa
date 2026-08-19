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

import edtproject.ComponentProperty;
import edtproject.CompositeReference;
import edtproject.CompositeService;
import edtproject.ServiceLink;
import org.eclipse.emf.common.util.EList;
import org.open.oasis.docs.ns.opencsa.sca.sca.*;
import technology.ecoa.sca.extension.scaExt.ImplementationType;
import technology.ecoa.sca.extension.scaExt.Interface;
import technology.ecoa.sca.extension.scaExt.scaExtFactory;
import technology.ecoa.sca.extension.scaExt.scaExtPackage;

/**
 * Converts EDT Composite objects to ECOA SCA Composite format.
 * Based on the original AssemblyExportConverter from edt-tmp.
 */
public class AssemblyExportConverter {

    private static final scaFactory SCAFACTORY = scaFactory.eINSTANCE;

    private AssemblyExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT Composite to ECOA Composite.
     *
     * @param edtComposite    the EDT Composite to convert
     * @param isFinalAssembly true if final assembly (step 5), false for initial assembly (step 3)
     * @param name            the composite name
     * @return DocumentRoot containing the Composite
     */
    public static DocumentRoot recreateComposite(edtproject.Composite edtComposite, boolean isFinalAssembly, String name) {
        Composite ecoaComposite = SCAFACTORY.createComposite();

        ecoaComposite.setName(name);
        ecoaComposite.setTargetNamespace(edtComposite.getTargetNamespace());

        // Recreate References
        EList<CompositeReference> edtReferences = edtComposite.getReferences();
        for (CompositeReference edtRef : edtReferences) {
            ecoaComposite.getReference().add(recreateCompositeReference(edtRef));
        }

        // Recreate Services
        EList<CompositeService> edtServices = edtComposite.getServices();
        for (CompositeService edtService : edtServices) {
            ecoaComposite.getService().add(recreateCompositeService(edtService));
        }

        // Recreate Properties
        EList<edtproject.Property> edtProperties = edtComposite.getProperties();
        for (edtproject.Property edtProperty : edtProperties) {
            ecoaComposite.getProperty().add(EdtProjectExportConverter.recreateProperty(edtProperty));
        }

        // Recreate Components
        EList<edtproject.Component> edtComponents = edtComposite.getComponents();
        for (edtproject.Component edtComponent : edtComponents) {
            ecoaComposite.getComponent().add(recreateComponent(edtComponent, isFinalAssembly));
        }

        // Recreate Wires (ServiceLinks)
        EList<ServiceLink> edtServiceLinks = edtComposite.getServiceLinks();
        for (ServiceLink edtLink : edtServiceLinks) {
            ecoaComposite.getWire().add(recreateWire(edtLink));
        }

        DocumentRoot documentRoot = SCAFACTORY.createDocumentRoot();
        documentRoot.setComposite(ecoaComposite);
        return documentRoot;
    }

    private static Wire recreateWire(ServiceLink edtServiceLink) {
        Wire wire = SCAFACTORY.createWire();

        if (edtServiceLink.getSource() != null) {
            wire.setSource(edtServiceLink.getSource().getWireString());
        }
        if (edtServiceLink.getTarget() != null) {
            wire.setTarget(edtServiceLink.getTarget().getWireString());
        }

        return wire;
    }

    private static Service recreateCompositeService(CompositeService edtService) {
        Service service = SCAFACTORY.createService();

        Interface ecoaInterface = ComponentDefinitionExportConverter.recreateInterface(edtService);
        service.setInterface(ecoaInterface);
        service.setName(edtService.getName());
        service.setPromote(edtService.getPromote());

        return service;
    }

    private static Reference recreateCompositeReference(CompositeReference edtReference) {
        Reference reference = SCAFACTORY.createReference();

        Interface ecoaInterface = ComponentDefinitionExportConverter.recreateInterface(edtReference);
        reference.setInterface(ecoaInterface);
        reference.setName(edtReference.getName());
        reference.setMultiplicity(edtReference.getMultiplicity());
        reference.setPromote(edtReference.getPromote());

        return reference;
    }

    private static Component recreateComponent(edtproject.Component edtComponent, boolean isFinalAssembly) {
        Component ecoaComponent = SCAFACTORY.createComponent();

        ecoaComponent.setName(edtComponent.getName());

        // Recreate Properties
        EList<ComponentProperty> edtProperties = edtComponent.getProperties();
        for (ComponentProperty edtProperty : edtProperties) {
            ecoaComponent.getProperty().add(recreatePropertyValue(edtProperty));
        }

        // Recreate References
        EList<edtproject.ComponentReference> edtRefs = edtComponent.getComponentReferences();
        for (edtproject.ComponentReference edtRef : edtRefs) {
            ecoaComponent.getReference().add(recreateComponentReference(edtRef));
        }

        // Recreate Services
        EList<edtproject.ComponentService> edtServices = edtComponent.getComponentServices();
        for (edtproject.ComponentService edtService : edtServices) {
            ecoaComponent.getService().add(recreateComponentService(edtService));
        }

        // Create ECOA instance with ComponentDefinition link
        var ecoaInstance = scaExtFactory.eINSTANCE.createInstance();
        if (edtComponent.getComponentDefinition() != null) {
            ecoaInstance.setComponentType(edtComponent.getComponentDefinition().getName());
        }

        if (isFinalAssembly && edtComponent.getComponentImplementation() != null) {
            ImplementationType implementationType = scaExtFactory.eINSTANCE.createImplementationType();
            implementationType.setName(edtComponent.getComponentImplementation().getName());
            ecoaInstance.setImplementation(implementationType);
        }

        ecoaInstance.setVersion(edtComponent.getComponentDefinitionVersion());
        ecoaComponent.getImplementationGroup().add(scaExtPackage.Literals.DOCUMENT_ROOT__INSTANCE, ecoaInstance);

        return ecoaComponent;
    }

    private static ComponentService recreateComponentService(edtproject.ComponentService edtService) {
        ComponentService service = SCAFACTORY.createComponentService();
        if (edtService.getComponentDefinitionService() != null) {
            service.setName(edtService.getComponentDefinitionService().getName());
        }
        return service;
    }

    private static ComponentReference recreateComponentReference(edtproject.ComponentReference edtReference) {
        ComponentReference reference = SCAFACTORY.createComponentReference();

        if (edtReference.isSetMultiplicity()) {
            reference.setMultiplicity(edtReference.getMultiplicity());
        }
        if (edtReference.getComponentDefinitionReference() != null) {
            reference.setName(edtReference.getComponentDefinitionReference().getName());
        }

        return reference;
    }

    private static PropertyValue recreatePropertyValue(ComponentProperty edtProperty) {
        PropertyValue property = SCAFACTORY.createPropertyValue();

        if (edtProperty.getFile() != null && !edtProperty.getFile().isBlank()) {
            property.setFile(edtProperty.getFile());
        }
        if (edtProperty.getSource() != null && !edtProperty.getSource().isBlank()) {
            property.setSource(edtProperty.getSource());
        }
        if (edtProperty.getComponentDefinitionProperty() != null) {
            property.setName(edtProperty.getComponentDefinitionProperty().getName());
        }

        if (edtProperty.getValue() != null && !edtProperty.getValue().isBlank()) {
            property.getAny().add(scaPackage.Literals.DOCUMENT_ROOT__VALUE,
                    EdtProjectExportConverter.recreateValueType(edtProperty.getValue()));
        }

        return property;
    }
}
