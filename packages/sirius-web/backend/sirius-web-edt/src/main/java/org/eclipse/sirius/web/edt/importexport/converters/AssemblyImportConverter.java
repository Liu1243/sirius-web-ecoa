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

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import org.open.oasis.docs.ns.opencsa.sca.sca.Component;
import org.open.oasis.docs.ns.opencsa.sca.sca.ComponentReference;
import org.open.oasis.docs.ns.opencsa.sca.sca.ComponentService;
import org.open.oasis.docs.ns.opencsa.sca.sca.Composite;
import org.open.oasis.docs.ns.opencsa.sca.sca.Property;
import org.open.oasis.docs.ns.opencsa.sca.sca.PropertyValue;
import org.open.oasis.docs.ns.opencsa.sca.sca.Reference;
import org.open.oasis.docs.ns.opencsa.sca.sca.Service;
import org.open.oasis.docs.ns.opencsa.sca.sca.Wire;

import edtimplementation.ComponentImplementation;
import edtproject.ComponentProperty;
import edtproject.CompositeReference;
import edtproject.CompositeService;
import edtproject.EDTProjectFactory;
import edtproject.ServiceLink;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step2;
import edtproject.Step4;
import technology.ecoa.sca.extension.scaExt.ImplementationType;
import technology.ecoa.sca.extension.scaExt.Instance;

/**
 * Convert imported ECOA InitialAssembly objects to EDT objects.
 */
public class AssemblyImportConverter {
    
    private static final EDTProjectFactory EDTFACTORY = EDTProjectFactory.eINSTANCE;

    private AssemblyImportConverter() {
        // Utility class
    }

    /**
     * Create a EDT model Composite from a ECOA model Composite.
     */
    public static edtproject.Composite createEDTComposite(Composite ecoaComposite, Step2 step2, Step0 typeStep,
                                                          Step1 step1, StringBuilder missingElementsToLog) throws FailedImportException {
        
        // Create empty EDT Composite
        edtproject.Composite edtComposite = EDTFACTORY.createComposite();

        edtComposite.setName(ecoaComposite.getName());
        edtComposite.setTargetNamespace(ecoaComposite.getTargetNamespace());

        EList<Property> properties = ecoaComposite.getProperty();
        for (Property property : properties) {
            edtComposite.getProperties()
                    .add(EdtProjectImportConverter.createEDTProperty(property, typeStep, missingElementsToLog));
        }

        EList<Reference> references = ecoaComposite.getReference();
        for (Reference reference : references) {
            edtComposite.getReferences().add(createCompositeReference(step1, reference));
        }

        EList<Service> services = ecoaComposite.getService();
        for (Service service : services) {
            edtComposite.getServices().add(createCompositeService(step1, service));
        }

        // Create EDTComponents
        EList<Component> ecoaComponents = ecoaComposite.getComponent();
        for (Component ecoaComponent : ecoaComponents) {
            edtComposite.getComponents().add(createEDTComponent(step2, ecoaComponent));
        }

        // Convert Wire to links
        EList<Wire> ecoaWires = ecoaComposite.getWire();
        for (Wire ecoaWire : ecoaWires) {
            String source = ecoaWire.getSource();
            String target = ecoaWire.getTarget();
            
            // Note: Assuming findComponentReferenceFromWire and findComponentServiceFromWire exist on edtproject.Composite
            // If they are missing, we'll need to implement manual search logic.
            // For now, assuming they are available as in the original code.
            
            edtproject.ComponentReference ref = edtComposite.findComponentReferenceFromWire(source);
            if (ref == null) {
                // Try finding by parsing source string "componentName/referenceName"
                ref = findRefManually(edtComposite, source);
                if (ref == null)
                    throw new FailedImportException("The ComponentReference " + source + " was not found");
            }
            
            edtproject.ComponentService svc = edtComposite.findComponentServiceFromWire(target);
            if (svc == null) {
                 // Try finding by parsing target string "componentName/serviceName"
                 svc = findSvcManually(edtComposite, target);
                 if (svc == null)
                    throw new FailedImportException("The ComponentService " + target + " was not found");
            }

            ServiceLink serviceLink = EDTFACTORY.createServiceLink();
            serviceLink.setSource(ref);
            serviceLink.setTarget(svc);
            edtComposite.getServiceLinks().add(serviceLink);
        }

        return edtComposite;
    }

    private static edtproject.ComponentReference findRefManually(edtproject.Composite composite, String wireSource) {
        if (wireSource == null || !wireSource.contains("/")) return null;
        String[] parts = wireSource.split("/");
        if (parts.length != 2) return null;
        String compName = parts[0];
        String refName = parts[1];
        
        edtproject.Component comp = composite.findComponentByName(compName);
        if (comp != null) {
            for (edtproject.ComponentReference r : comp.getComponentReferences()) {
                 if (r.getComponentDefinitionReference() != null) {
                      String defName = r.getComponentDefinitionReference().getName();
                      if (defName.equals(refName) || (refName.startsWith("svc_") && defName.equals(refName.substring(4)))) {
                          return r;
                      }
                 }
            }
        }
        return null;
    }

    private static edtproject.ComponentService findSvcManually(edtproject.Composite composite, String wireTarget) {
        if (wireTarget == null || !wireTarget.contains("/")) return null;
        String[] parts = wireTarget.split("/");
        if (parts.length != 2) return null;
        String compName = parts[0];
        String svcName = parts[1];
        
        edtproject.Component comp = composite.findComponentByName(compName);
        if (comp != null) {
            for (edtproject.ComponentService s : comp.getComponentServices()) {
                 if (s.getComponentDefinitionService() != null) {
                     String defName = s.getComponentDefinitionService().getName();
                     if (defName.equals(svcName) || (svcName.startsWith("svc_") && defName.equals(svcName.substring(4)))) {
                         return s;
                     }
                 }
            }
        }
        return null;
    }

    private static CompositeService createCompositeService(Step1 step1, Service ecoaService) throws FailedImportException {
        CompositeService edtService = EDTFACTORY.createCompositeService();
        if (ecoaService.getInterface() != null) {
            EdtProjectImportConverter.createEDTInterface(edtService, ecoaService.getInterface(), null, step1);
        }
        edtService.setName(ecoaService.getName());
        edtService.setPromote(ecoaService.getPromote());
        return edtService;
    }

    private static CompositeReference createCompositeReference(Step1 step1, Reference ecoaReference) throws FailedImportException {
        CompositeReference edtRef = EDTFACTORY.createCompositeReference();
        if (ecoaReference.getInterface() != null) {
             EdtProjectImportConverter.createEDTInterface(edtRef, ecoaReference.getInterface(), null, step1);
        }
        edtRef.setMultiplicity(ecoaReference.getMultiplicity());
        edtRef.setName(ecoaReference.getName());
        edtRef.setPromote(ecoaReference.getPromote());
        return edtRef;
    }

    private static edtproject.Component createEDTComponent(Step2 step2, Component ecoaComponent) throws FailedImportException {
        var edtComponent = EDTFACTORY.createComponent();
        String componentName = ecoaComponent.getName();
        edtComponent.setName(componentName);

        FeatureMap implementationGroup = ecoaComponent.getImplementationGroup();
        if (implementationGroup != null) {
            for (int i = 0; i < implementationGroup.size(); i++) {
                Object value = implementationGroup.get(i).getValue();
                if (value instanceof Instance instance) {
                    setEDTInstance(step2, edtComponent, instance);
                }
            }
        }

        for (PropertyValue prop : ecoaComponent.getProperty()) {
            edtComponent.getProperties().add(createEDTPropertyValue(edtComponent, prop));
        }

        for (ComponentReference ref : ecoaComponent.getReference()) {
            edtComponent.getComponentReferences().add(createEDTComponentReference(edtComponent, ref));
        }

        for (ComponentService svc : ecoaComponent.getService()) {
            edtComponent.getComponentServices().add(createEDTComponentService(edtComponent, svc));
        }

        return edtComponent;
    }

    private static ComponentProperty createEDTPropertyValue(edtproject.Component edtComponent, PropertyValue ecoaProperty) throws FailedImportException {
        ComponentProperty edtProperty = EDTFACTORY.createComponentProperty();
        String propertyName = ecoaProperty.getName();
        
        var edtPropertyOfCT = edtComponent.getComponentDefinition().findComponentDefinitionPropertyByName(propertyName);
        if (edtPropertyOfCT != null) {
            edtProperty.setComponentDefinitionProperty(edtPropertyOfCT);
        } else {
             throw new FailedImportException("No Property found with name " + propertyName + " in ComponentDefinition " + edtComponent.getComponentDefinition().getName());
        }

        edtProperty.setFile(ecoaProperty.getFile());
        edtProperty.setSource(ecoaProperty.getSource());
        edtProperty.setValue(EdtProjectImportConverter.createPropertyEDTValue(ecoaProperty.getAny()));
        return edtProperty;
    }

    private static edtproject.ComponentReference createEDTComponentReference(edtproject.Component edtComponent, ComponentReference ecoaRef) throws FailedImportException {
        var edtRef = EDTFACTORY.createComponentReference();
        var edtCTRef = edtComponent.getComponentDefinition().findComponentDefinitionReferenceByName(ecoaRef.getName());
        
        if (edtCTRef == null && ecoaRef.getName() != null && ecoaRef.getName().startsWith("svc_")) {
            edtCTRef = edtComponent.getComponentDefinition().findComponentDefinitionReferenceByName(ecoaRef.getName().substring(4));
        }

        if (edtCTRef != null) {
            edtRef.setComponentDefinitionReference(edtCTRef);
        } else {
            throw new FailedImportException("No Reference found with name " + ecoaRef.getName() + " in ComponentDefinition " + edtComponent.getComponentDefinition().getName());
        }

        if (ecoaRef.isSetMultiplicity()) {
            edtRef.setMultiplicity(ecoaRef.getMultiplicity());
        }
        return edtRef;
    }

    private static edtproject.ComponentService createEDTComponentService(edtproject.Component edtComponent, ComponentService ecoaSvc) throws FailedImportException {
        var edtSvc = EDTFACTORY.createComponentService();
        var edtCTSvc = edtComponent.getComponentDefinition().findComponentDefinitionServiceByName(ecoaSvc.getName());
        
        if (edtCTSvc == null && ecoaSvc.getName() != null && ecoaSvc.getName().startsWith("svc_")) {
            edtCTSvc = edtComponent.getComponentDefinition().findComponentDefinitionServiceByName(ecoaSvc.getName().substring(4));
        }

        if (edtCTSvc != null) {
            edtSvc.setComponentDefinitionService(edtCTSvc);
        } else {
             throw new FailedImportException("No Service found with name " + ecoaSvc.getName() + " in ComponentDefinition " + edtComponent.getComponentDefinition().getName());
        }
        return edtSvc;
    }

    private static void setEDTInstance(Step2 step2, edtproject.Component edtComponent, Instance ecoaInstance) throws FailedImportException {
        String componentTypeName = ecoaInstance.getComponentType();
        var edtComponentType = step2.findComponentDefinitionByName(componentTypeName);
        
        if (edtComponentType != null) {
            edtComponent.setComponentDefinition(edtComponentType);
        } else {
            throw new FailedImportException("No ComponentDefinition found with name: " + componentTypeName);
        }
        edtComponent.setComponentDefinitionVersion(ecoaInstance.getVersion());
    }

    public static void addImplementationToInitialAssembly(Composite finalAssembly, edtproject.Composite edtInitialAssembly,
            Step4 step4, Step0 typeStep, StringBuilder missingElementsToLog) throws FailedImportException {
        
        for (Property property : finalAssembly.getProperty()) {
            if (edtInitialAssembly.findPropertyByName(property.getName()) == null) {
                edtInitialAssembly.getProperties().add(EdtProjectImportConverter.createEDTProperty(property, typeStep, missingElementsToLog));
            }
        }

        for (Component ecoaComponent : finalAssembly.getComponent()) {
            edtproject.Component initialAssemblyComponent = edtInitialAssembly.findComponentByName(ecoaComponent.getName());
            if (initialAssemblyComponent == null) {
                 throw new FailedImportException("Component " + ecoaComponent.getName() + " not found in InitialAssembly");
            }

            FeatureMap implementationGroup = ecoaComponent.getImplementationGroup();
            if (implementationGroup != null) {
                for (int i = 0; i < implementationGroup.size(); i++) {
                    Object value = implementationGroup.get(i).getValue();
                    if (value instanceof Instance instance && instance.getImplementation() != null) {
                        ImplementationType impl = instance.getImplementation();
                        ComponentImplementation edtImpl = step4.findComponentImplementationByName(impl.getName());
                        if (edtImpl != null) {
                            initialAssemblyComponent.setComponentImplementation(edtImpl);
                        } else {
                            throw new FailedImportException("ComponentImplementation " + impl.getName() + " not found");
                        }
                    }
                }
            }
            
            for (PropertyValue prop : ecoaComponent.getProperty()) {
                initialAssemblyComponent.getProperties().add(createEDTPropertyValue(initialAssemblyComponent, prop));
            }
        }

        // Services Links (Wires)
         EList<Wire> ecoaWires = finalAssembly.getWire();
        for (Wire ecoaWire : ecoaWires) {
            String source = ecoaWire.getSource();
            String target = ecoaWire.getTarget();

            edtproject.ComponentReference ref = edtInitialAssembly.findComponentReferenceFromWire(source);
             if (ref == null) ref = findRefManually(edtInitialAssembly, source);
            if (ref == null) throw new FailedImportException("ComponentReference " + source + " not found");

            edtproject.ComponentService svc = edtInitialAssembly.findComponentServiceFromWire(target);
            if (svc == null) svc = findSvcManually(edtInitialAssembly, target);
            if (svc == null) throw new FailedImportException("ComponentService " + target + " not found");

            if (edtInitialAssembly.findServiceLink(ref, svc) == null) {
                ServiceLink link = EDTFACTORY.createServiceLink();
                link.setSource(ref);
                link.setTarget(svc);
                edtInitialAssembly.getServiceLinks().add(link);
            }
        }
    }
}
