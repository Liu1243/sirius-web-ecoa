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

import java.math.BigInteger;
import java.util.ArrayList;

import edtimplementation.*;
import edttype.EDTDataType;
import edttype.Library;
import org.eclipse.emf.common.util.EList;
import technology.ecoa.implementation._2.DocumentRoot;
import technology.ecoa.implementation._2.ProgrammingLanguage;
import technology.ecoa.implementation._2.impFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts EDT ComponentImplementation objects to ECOA Implementation XML format.
 * Based on the original ComponentImplementationExportConverter from edt-tmp.
 * Note: This is a simplified version - DataLinks, EventLinks, and RequestLinks 
 * require additional helper converters for complete implementation.
 */
public class ComponentImplementationExportConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentImplementationExportConverter.class);
    private static final impFactory IMPFACTORY = impFactory.eINSTANCE;

    private ComponentImplementationExportConverter() {
        // Utility class
    }

    /**
     * Convert EDT ComponentImplementation to ECOA ComponentImplementation.
     *
     * @param edtComponentImpl the EDT ComponentImplementation to convert
     * @return DocumentRoot containing the ComponentImplementation
     */
    public static DocumentRoot recreateComponentImplementation(ComponentImplementation edtComponentImpl) {
        var ecoaImpl = IMPFACTORY.createComponentImplementation();

        // Set ComponentDefinition reference
        if (edtComponentImpl.getComponentDefinition() != null) {
            ecoaImpl.setComponentDefinition(edtComponentImpl.getComponentDefinition().getName());
        }

        // Set used libraries
        EList<Library> usedLibraries = edtComponentImpl.getUsedLibraries();
        for (Library lib : usedLibraries) {
            ecoaImpl.getUse().add(recreateUseType(lib));
        }

        // Recreate References
        EList<ComponentImplementationReference> refs = edtComponentImpl.getReferences();
        for (ComponentImplementationReference ref : refs) {
            var serviceQos = IMPFACTORY.createServiceQoS();
            if (ref.getComponentDefinitionReference() != null && ref.getNewQos() != null) {
                serviceQos.setName(ref.getComponentDefinitionReference().getName());
                serviceQos.setNewQoS(ref.getNewQos().getName());
            }
            ecoaImpl.getReference().add(serviceQos);
        }

        // Recreate Services
        EList<ComponentImplementationService> services = edtComponentImpl.getServices();
        for (ComponentImplementationService service : services) {
            var serviceQos = IMPFACTORY.createServiceQoS();
            if (service.getComponentDefinitionService() != null && service.getNewQos() != null) {
                serviceQos.setName(service.getComponentDefinitionService().getName());
                serviceQos.setNewQoS(service.getNewQos().getName());
            }
            ecoaImpl.getService().add(serviceQos);
        }

        // Recreate ModuleTypes
        EList<ModuleType> moduleTypes = edtComponentImpl.getModuleTypes();
        for (ModuleType moduleType : moduleTypes) {
            ecoaImpl.getModuleType().add(recreateModuleType(moduleType));
        }

        // Recreate ModuleImplementations
        EList<ModuleImplementation> moduleImpls = edtComponentImpl.getModuleImplementations();
        for (ModuleImplementation moduleImpl : moduleImpls) {
            ecoaImpl.getModuleImplementation().add(recreateModuleImplementation(moduleImpl));
        }

        // Recreate Instances (ModuleInstance, TriggerInstance, DynamicTriggerInstance)
        EList<Instance> instances = edtComponentImpl.getInstances();
        for (Instance instance : instances) {
            if (instance instanceof ModuleInstance mi) {
                ecoaImpl.getModuleInstance().add(recreateModuleInstance(mi));
            } else if (instance instanceof TriggerInstance ti) {
                ecoaImpl.getTriggerInstance().add(recreateTriggerInstance(ti));
            } else if (instance instanceof DynamicTriggerInstance dti) {
                ecoaImpl.getDynamicTriggerInstance().add(recreateDynamicTriggerInstance(dti));
            }
        }

        // Recreate DataLinks, EventLinks, RequestLinks
        ArrayList<DataLink> edtDataLinks = new ArrayList<>();
        ArrayList<EventLink> edtEventLinks = new ArrayList<>();
        ArrayList<RequestLink> edtRequestLinks = new ArrayList<>();
        for (OperationLink link : edtComponentImpl.getOperationLinks()) {
            if (link instanceof DataLink dl) edtDataLinks.add(dl);
            else if (link instanceof EventLink el) edtEventLinks.add(el);
            else if (link instanceof RequestLink rl) edtRequestLinks.add(rl);
        }
        ecoaImpl.getDataLink().addAll(
                ComponentImplementationDataLinkExportConverter.recreateDataLinks(edtComponentImpl, edtDataLinks));
        ecoaImpl.getEventLink().addAll(
                ComponentImplementationEventLinkExportConverter.recreateEventLinks(edtComponentImpl, edtEventLinks));
        ecoaImpl.getRequestLink().addAll(
                ComponentImplementationRequestLinkExportConverter.recreateECOARequestLinks(edtComponentImpl, edtRequestLinks));

        DocumentRoot documentRoot = IMPFACTORY.createDocumentRoot();
        documentRoot.setComponentImplementation(ecoaImpl);
        return documentRoot;
    }

    private static technology.ecoa.implementation._2.UseType recreateUseType(Library library) {
        var useType = IMPFACTORY.createUseType();
        useType.setLibrary(library.getName());
        return useType;
    }

    private static technology.ecoa.implementation._2.ModuleType recreateModuleType(ModuleType edtModuleType) {
        var moduleType = IMPFACTORY.createModuleType();

        moduleType.setName(edtModuleType.getName());

        if (edtModuleType.isSetActivatingFaultNotifs()) {
            moduleType.setActivatingFaultNotifs(edtModuleType.isActivatingFaultNotifs());
        }
        if (edtModuleType.isSetHasUserContext()) {
            moduleType.setHasUserContext(edtModuleType.isHasUserContext());
        }
        if (edtModuleType.isSetHasWarmStartContext()) {
            moduleType.setHasWarmStartContext(edtModuleType.isHasWarmStartContext());
        }
        if (edtModuleType.isSetIsFaultHandler()) {
            moduleType.setIsFaultHandler(edtModuleType.isIsFaultHandler());
        }

        // Recreate PINFOs
        EList<ModuleTypePinfo> pinfos = edtModuleType.getPinfo();
        if (!pinfos.isEmpty()) {
            var pinfoType = IMPFACTORY.createPinfoType1();
            for (ModuleTypePinfo pinfo : pinfos) {
                if (pinfo instanceof PublicPinfo publicPinfo) {
                    var ecoaPinfo = IMPFACTORY.createPublicPinfo();
                    ecoaPinfo.setName(publicPinfo.getName());
                    pinfoType.getPublicPinfo().add(ecoaPinfo);
                } else if (pinfo instanceof PrivatePinfo privatePinfo) {
                    var ecoaPinfo = IMPFACTORY.createPrivatePinfo();
                    ecoaPinfo.setName(privatePinfo.getName());
                    pinfoType.getPrivatePinfo().add(ecoaPinfo);
                }
            }
            moduleType.setPinfo(pinfoType);
        }

        // Recreate Operations
        EList<ModuleOperation> operations = edtModuleType.getOperations();
        var operationsType = IMPFACTORY.createOperationsType();
        for (ModuleOperation op : operations) {
            ComponentImplementationOperationsExportConverter.recreateECOAOperations(operationsType, op);
        }
        moduleType.setOperations(operationsType);

        // Recreate Properties
        EList<ModuleTypeProperty> properties = edtModuleType.getProperties();
        if (!properties.isEmpty()) {
            var propertiesType = IMPFACTORY.createPropertiesType();
            for (ModuleTypeProperty prop : properties) {
                var ecoaProp = IMPFACTORY.createParameter();
                ecoaProp.setName(prop.getName());
                EDTDataType type = prop.getType();
                if (type != null) {
                    ecoaProp.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(type));
                }
                propertiesType.getProperty().add(ecoaProp);
            }
            moduleType.setProperties(propertiesType);
        }

        return moduleType;
    }

    private static technology.ecoa.implementation._2.ModuleImplementation recreateModuleImplementation(
            ModuleImplementation edtModuleImpl) {
        var moduleImpl = IMPFACTORY.createModuleImplementation();

        moduleImpl.setName(edtModuleImpl.getName());
        moduleImpl.setLanguage(edtModuleImpl.getLanguage());
        if (edtModuleImpl.getModuleType() != null) {
            moduleImpl.setModuleType(edtModuleImpl.getModuleType().getName());
        }

        return moduleImpl;
    }

    private static technology.ecoa.implementation._2.ModuleInstance recreateModuleInstance(ModuleInstance edtInstance) {
        var instance = IMPFACTORY.createModuleInstance();

        instance.setName(edtInstance.getName());
        instance.setRelativePriority(edtInstance.getRelativePriority());
        if (edtInstance.getModuleImplementation() != null) {
            instance.setImplementationName(edtInstance.getModuleImplementation().getName());
        }

        // Recreate PropertyValues
        EList<PropertyValue> propertyValues = edtInstance.getPropertyValues();
        if (!propertyValues.isEmpty()) {
            var ecoaPropertyValues = IMPFACTORY.createPropertyValues();
            for (PropertyValue pv : propertyValues) {
                var ecoaPV = IMPFACTORY.createPropertyValue();
                ecoaPV.setName(pv.getName());
                String pvValue = pv.getValue();
                LOGGER.info("[IMPL-EXPORT] ModuleInstance '{}' propertyValue '{}' = '{}'",
                        edtInstance.getName(), pv.getName(), pvValue);
                if (pvValue != null && !pvValue.isBlank()) {
                    ecoaPV.setValue(pvValue);
                } else {
                    LOGGER.warn("[IMPL-EXPORT] ModuleInstance '{}' propertyValue '{}' is null/blank - value will be empty in XML!",
                            edtInstance.getName(), pv.getName());
                }
                ecoaPropertyValues.getPropertyValue().add(ecoaPV);
            }
            instance.setPropertyValues(ecoaPropertyValues);
        }

        // Recreate PinfoValues
        EList<PinfoValue> pinfoValues = edtInstance.getPinfo();
        if (!pinfoValues.isEmpty()) {
            var ecoaPinfoType = IMPFACTORY.createPinfoType();
            for (PinfoValue pv : pinfoValues) {
                var ecoaPinfoValue = IMPFACTORY.createPinfoValue();
                ecoaPinfoValue.setName(pv.getName());
                if (pv.getValue() != null && !pv.getValue().isBlank()) {
                    ecoaPinfoValue.setValue(pv.getValue());
                }
                if (pv instanceof PublicPinfoValue) {
                    ecoaPinfoType.getPublicPinfo().add(ecoaPinfoValue);
                } else if (pv instanceof PrivatePinfoValue) {
                    ecoaPinfoType.getPrivatePinfo().add(ecoaPinfoValue);
                }
            }
            instance.setPinfo(ecoaPinfoType);
        }

        return instance;
    }

    private static technology.ecoa.implementation._2.TriggerInstance recreateTriggerInstance(TriggerInstance edtInstance) {
        var instance = IMPFACTORY.createTriggerInstance();

        instance.setName(edtInstance.getName());
        instance.setRelativePriority(edtInstance.getRelativePriority());

        return instance;
    }

    private static technology.ecoa.implementation._2.DynamicTriggerInstance recreateDynamicTriggerInstance(
            DynamicTriggerInstance edtInstance) {
        var instance = IMPFACTORY.createDynamicTriggerInstance();

        instance.setName(edtInstance.getName());
        instance.setRelativePriority(edtInstance.getRelativePriority());

        // Convert parameters
        EList<Parameter> params = edtInstance.getParameter();
        for (Parameter param : params) {
            var ecoaParam = IMPFACTORY.createParameter();
            ecoaParam.setName(param.getName());
            if (param.getType() != null) {
                ecoaParam.setType(TypesExportConverter.recreateDataTypeNameForNonTypes(param.getType()));
            }
            instance.getParameter().add(ecoaParam);
        }

        return instance;
    }
}
