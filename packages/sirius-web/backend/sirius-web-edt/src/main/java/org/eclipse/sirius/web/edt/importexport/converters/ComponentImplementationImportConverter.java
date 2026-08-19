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

import java.util.List;
import java.util.Objects;

import org.eclipse.emf.common.util.EList;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edtimplementation.EdtimplementationFactory;
import edtimplementation.TriggerSender;
import temp.TempFactory;
import edtproject.ComponentDefinition;
import edtproject.ComponentDefinitionReference;
import edtproject.ComponentDefinitionService;
import edtproject.Step0;
import edtqos.ServiceInstanceQos;
import edttype.EDTDataType;
import edttype.Library;
import technology.ecoa.implementation._2.ComponentImplementation;
import technology.ecoa.implementation._2.DataLink;
import technology.ecoa.implementation._2.DynamicTriggerInstance;
import technology.ecoa.implementation._2.EventLink;
import technology.ecoa.implementation._2.ModuleImplementation;
import technology.ecoa.implementation._2.ModuleInstance;
import technology.ecoa.implementation._2.ModuleType;
import technology.ecoa.implementation._2.OperationsType;
import technology.ecoa.implementation._2.Parameter;
import technology.ecoa.implementation._2.PinfoType;
import technology.ecoa.implementation._2.PinfoType1;
import technology.ecoa.implementation._2.PinfoValue;
import technology.ecoa.implementation._2.PrivatePinfo;
import technology.ecoa.implementation._2.PropertiesType;
import technology.ecoa.implementation._2.PropertyValue;
import technology.ecoa.implementation._2.PropertyValues;
import technology.ecoa.implementation._2.PublicPinfo;
import technology.ecoa.implementation._2.RequestLink;
import technology.ecoa.implementation._2.ServiceQoS;
import technology.ecoa.implementation._2.TriggerInstance;
import technology.ecoa.implementation._2.UseType;

/**
 * Helper class to convert ECOA Model Objects to EDT.
 */
public class ComponentImplementationImportConverter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentImplementationImportConverter.class);
    private static final EdtimplementationFactory EDTIMPFACTORY = EdtimplementationFactory.eINSTANCE;

    private ComponentImplementationImportConverter() {
        // Utility class
    }

    public static edtimplementation.ComponentImplementation createEDTComponentImplementation(
            ComponentImplementation ecoaImp, String fileName, ComponentDefinition edtComponentDefinition,
            List<ServiceInstanceQos> edtServiceQosList, Step0 typeStep) throws FailedImportException {
        
        edtimplementation.ComponentImplementation edtComponentImplementation = EDTIMPFACTORY.createComponentImplementation();

        // insertionPolicyList has lowerBound=1 in the EDT ecore; always initialise it
        // so the model satisfies the constraint even when importing ECOA XML that
        // manages insertion policies in separate .pol.xml files.
        edtComponentImplementation.setInsertionPolicyList(TempFactory.eINSTANCE.createInsertionPolicies());

        // Set name
        edtComponentImplementation.setName(EdtProjectImportConverter.getObjectName(fileName, ".impl.xml"));

        // Set Associated ServiceQoS
        if (edtServiceQosList != null) {
            edtComponentImplementation.getAssociatedServiceQos().addAll(edtServiceQosList);
        }

        // Get Libraries used
        EList<UseType> usedLibraries = ecoaImp.getUse();
        for (UseType useType : usedLibraries) {
            if ("ECOA".equals(useType.getLibrary())) {
                continue;
            }
            Library libraryToBeAssociated = typeStep.findLibrary(useType.getLibrary());
            if (libraryToBeAssociated != null) {
                edtComponentImplementation.getUsedLibraries().add(libraryToBeAssociated);
            } else {
                throw new FailedImportException("No Library was found with the name " + useType.getLibrary());
            }
        }

        // Set References
        for (ServiceQoS ecoaServiceQoS : ecoaImp.getReference()) {
            edtComponentImplementation.getReferences().add(createEDTComponentImplementationReference(edtServiceQosList,
                    ecoaServiceQoS, edtComponentDefinition.getReferences()));
        }

        // Set Services
        for (ServiceQoS ecoaServiceQoS : ecoaImp.getService()) {
            edtComponentImplementation.getServices().add(createEDTComponentImplementationService(edtServiceQosList,
                    ecoaServiceQoS, edtComponentDefinition.getServices()));
        }

        // Set ComponentDefinition
        edtComponentImplementation.setComponentDefinition(edtComponentDefinition);

        // Set ModuleTypes
        for (ModuleType moduleType : ecoaImp.getModuleType()) {
            edtComponentImplementation.getModuleTypes().add(createEDTModuleType(moduleType, typeStep));
        }

        // Set ModuleImplementation
        for (ModuleImplementation moduleImplementation : ecoaImp.getModuleImplementation()) {
            edtComponentImplementation.getModuleImplementations()
                    .add(createEDTModuleImplementation(moduleImplementation, edtComponentImplementation));
        }

        // Set Instances
        for (ModuleInstance moduleInstance : ecoaImp.getModuleInstance()) {
            edtComponentImplementation.getInstances().add(createEDTModuleInstance(moduleInstance, edtComponentImplementation));
        }

        for (TriggerInstance triggerInstance : ecoaImp.getTriggerInstance()) {
            edtComponentImplementation.getInstances().add(createEDTTriggerInstance(triggerInstance));
        }

        for (DynamicTriggerInstance dynamicTriggerInstance : ecoaImp.getDynamicTriggerInstance()) {
            edtComponentImplementation.getInstances().add(createEDTDynamicTriggerInstance(dynamicTriggerInstance, typeStep));
        }

        // Set links
        for (DataLink dataLink : ecoaImp.getDataLink()) {
            edtComponentImplementation.getOperationLinks().addAll(ComponentImplementationDataLinkImportConverter
                    .createEDTDataLink(dataLink, edtComponentImplementation));
        }
        for (EventLink eventLink : ecoaImp.getEventLink()) {
            edtComponentImplementation.getOperationLinks().addAll(ComponentImplementationEventLinkImportConverter
                    .createEDTEventLink(eventLink, edtComponentImplementation));
        }
        for (RequestLink requestLink : ecoaImp.getRequestLink()) {
            edtComponentImplementation.getOperationLinks().addAll(ComponentImplementationRequestLinkImportConverter
                    .createEDTRequestLink(requestLink, edtComponentImplementation));
        }

        return edtComponentImplementation;
    }

    private static edtimplementation.ComponentImplementationReference createEDTComponentImplementationReference(
            List<ServiceInstanceQos> edtServiceQosList, ServiceQoS ecoaServiceQoS,
            EList<ComponentDefinitionReference> componentDefinitionReferences) {
        
        var edtReference = EDTIMPFACTORY.createComponentImplementationReference();
        for (ComponentDefinitionReference componentDefinitionReference : componentDefinitionReferences) {
            String defName = componentDefinitionReference.getName();
            String ecoaName = ecoaServiceQoS.getName();
            if (Objects.equals(defName, ecoaName) || (ecoaName != null && ecoaName.startsWith("svc_") && Objects.equals(defName, ecoaName.substring(4)))) {
                edtReference.setComponentDefinitionReference(componentDefinitionReference);
                break;
            }
        }
        
        ServiceInstanceQos edtQoS = findQoS(edtServiceQosList, ecoaServiceQoS.getNewQoS());
        if (edtQoS != null) {
            edtReference.setNewQos(edtQoS);
        } else if (ecoaServiceQoS.getNewQoS() != null) {
            LOGGER.warn("No ServiceInstanceQos was found with the name: {}, skipping QoS assignment",
                    ecoaServiceQoS.getNewQoS());
        }
        return edtReference;
    }

    private static edtimplementation.ComponentImplementationService createEDTComponentImplementationService(
            List<ServiceInstanceQos> edtServiceQosList, ServiceQoS ecoaServiceQoS,
            EList<ComponentDefinitionService> componentDefinitionServices) {
        
        var edtService = EDTIMPFACTORY.createComponentImplementationService();
        for (ComponentDefinitionService componentDefinitionService : componentDefinitionServices) {
            String defName = componentDefinitionService.getName();
            String ecoaName = ecoaServiceQoS.getName();
            if (Objects.equals(defName, ecoaName) || (ecoaName != null && ecoaName.startsWith("svc_") && Objects.equals(defName, ecoaName.substring(4)))) {
                edtService.setComponentDefinitionService(componentDefinitionService);
                break;
            }
        }
        
        ServiceInstanceQos edtQoS = findQoS(edtServiceQosList, ecoaServiceQoS.getNewQoS());
        if (edtQoS != null) {
            edtService.setNewQos(edtQoS);
        } else if (ecoaServiceQoS.getNewQoS() != null) {
            LOGGER.warn("No ServiceInstanceQos was found with the name: {}, skipping QoS assignment",
                    ecoaServiceQoS.getNewQoS());
        }
        return edtService;
    }
    
    private static ServiceInstanceQos findQoS(List<ServiceInstanceQos> list, String name) {
        if (list == null || name == null) return null;
        for (ServiceInstanceQos q : list) {
            if (Objects.equals(q.getName(), name)) return q;
        }
        return null;
    }

    private static edtimplementation.ModuleType createEDTModuleType(ModuleType ecoaModuleType, Step0 typeStep)
            throws FailedImportException {
        edtimplementation.ModuleType edtModuleType = EDTIMPFACTORY.createModuleType();
        
        if (ecoaModuleType.isSetActivatingFaultNotifs()) edtModuleType.setActivatingFaultNotifs(ecoaModuleType.isActivatingFaultNotifs());
        if (ecoaModuleType.isSetHasUserContext()) edtModuleType.setHasUserContext(ecoaModuleType.isHasUserContext());
        if (ecoaModuleType.isSetHasWarmStartContext()) edtModuleType.setHasWarmStartContext(ecoaModuleType.isHasWarmStartContext());
        if (ecoaModuleType.isSetIsFaultHandler()) edtModuleType.setIsFaultHandler(ecoaModuleType.isIsFaultHandler());

        edtModuleType.setName(ecoaModuleType.getName());

        PinfoType1 pinfoType = ecoaModuleType.getPinfo();
        if (pinfoType != null) {
            for (PrivatePinfo p : pinfoType.getPrivatePinfo()) {
                edtimplementation.PrivatePinfo edtP = EDTIMPFACTORY.createPrivatePinfo();
                edtP.setName(p.getName());
                edtModuleType.getPinfo().add(edtP);
            }
            for (PublicPinfo p : pinfoType.getPublicPinfo()) {
                edtimplementation.PublicPinfo edtP = EDTIMPFACTORY.createPublicPinfo();
                edtP.setName(p.getName());
                edtModuleType.getPinfo().add(edtP);
            }
        }

        OperationsType operations = ecoaModuleType.getOperations();
        ComponentImplementationOperationsImportConverter.createEDTOperations(typeStep, edtModuleType, operations);

        PropertiesType ecoaPropertiesType = ecoaModuleType.getProperties();
        if (ecoaPropertiesType != null) {
            for (Parameter ecoaProperty : ecoaPropertiesType.getProperty()) {
                edtimplementation.ModuleTypeProperty edtProperty = EDTIMPFACTORY.createModuleTypeProperty();
                edtProperty.setName(ecoaProperty.getName());
                EDTDataType edtDataType = TypesImportConverter.findEDTDataTypeForNonTypes(typeStep, ecoaProperty.getType());
                if (edtDataType != null) {
                    edtProperty.setType(edtDataType);
                } else {
                    throw new FailedImportException("No Type was found with the name :" + ecoaProperty.getType());
                }
                edtModuleType.getProperties().add(edtProperty);
            }
        }

        return edtModuleType;
    }

    private static edtimplementation.ModuleImplementation createEDTModuleImplementation(
            ModuleImplementation ecoaModuleImplementation,
            edtimplementation.ComponentImplementation edtComponentImplementation) throws FailedImportException {
        edtimplementation.ModuleImplementation edtModuleImplementation = EDTIMPFACTORY.createModuleImplementation();

        edtModuleImplementation.setName(ecoaModuleImplementation.getName());
        edtModuleImplementation.setLanguage(ecoaModuleImplementation.getLanguage());
        String moduleTypeName = ecoaModuleImplementation.getModuleType();
        edtimplementation.ModuleType moduleType = edtComponentImplementation.findModuleTypeByName(moduleTypeName);
        if (moduleType != null) {
            edtModuleImplementation.setModuleType(moduleType);
        } else {
            throw new FailedImportException("No ModuleType was found with the name :" + moduleTypeName);
        }
        return edtModuleImplementation;
    }

    private static edtimplementation.ModuleInstance createEDTModuleInstance(ModuleInstance ecoaModuleInstance,
            edtimplementation.ComponentImplementation edtComponentImplementation) throws FailedImportException {
        edtimplementation.ModuleInstance edtModuleInstance = EDTIMPFACTORY.createModuleInstance();
        edtModuleInstance.setName(ecoaModuleInstance.getName());
        
        if (ecoaModuleInstance.getRelativePriority() != null) {
            edtModuleInstance.setRelativePriority(ecoaModuleInstance.getRelativePriority());
        }
        
        String implName = ecoaModuleInstance.getImplementationName();
        edtimplementation.ModuleImplementation impl = edtComponentImplementation.findModuleImplementationByName(implName);
        if (impl != null) {
            edtModuleInstance.setModuleImplementation(impl);
            edtModuleInstance.setModuleType(impl.getModuleType());
        } else {
             throw new FailedImportException("No ModuleImplementation found with name: " + implName);
        }

        // Handle pinfo values
        if (ecoaModuleInstance.getPinfo() != null && edtModuleInstance.getModuleType() != null
                && edtModuleInstance.getModuleType().getPinfo() != null) {
            edtimplementation.ModuleType edtModuleType = edtModuleInstance.getModuleType();
            PinfoType ecoaPinfoValueInstance = ecoaModuleInstance.getPinfo();
            createEDTPinfoValue(edtModuleInstance, edtModuleType, ecoaPinfoValueInstance);
        }

        // Handle property values
        if (ecoaModuleInstance.getPropertyValues() != null && edtModuleInstance.getModuleType() != null
                && edtModuleInstance.getModuleType().getProperties() != null) {
            edtimplementation.ModuleType edtModuleType = edtModuleInstance.getModuleType();
            PropertyValues ecoaPropertyValueInstance = ecoaModuleInstance.getPropertyValues();
            createEDTPropertyValue(edtModuleInstance, edtModuleType, ecoaPropertyValueInstance);
        }

        return edtModuleInstance;
    }

    private static void createEDTPinfoValue(edtimplementation.ModuleInstance edtModuleInstance,
            edtimplementation.ModuleType edtModuleType, PinfoType ecoaPinfoValueInstance) throws FailedImportException {
        for (PinfoValue pinfoValue : ecoaPinfoValueInstance.getPrivatePinfo()) {
            String name = pinfoValue.getName();
            edtimplementation.PrivatePinfo privatePinfoType = edtModuleType.findPrivatePinfoByName(name);
            if (privatePinfoType == null) {
                throw new FailedImportException("No PrivatePinfo was found in "
                        + edtModuleInstance.getModuleType().getName() + " with the name: " + name);
            }
            edtimplementation.PrivatePinfoValue privatePinfoValue = EDTIMPFACTORY.createPrivatePinfoValue();
            privatePinfoValue.setPrivatePinfoFromModuleType(privatePinfoType);
            privatePinfoValue.setName(name);
            privatePinfoValue.setValue(pinfoValue.getValue());
            edtModuleInstance.getPinfo().add(privatePinfoValue);
        }
        for (PinfoValue pinfoValue : ecoaPinfoValueInstance.getPublicPinfo()) {
            String name = pinfoValue.getName();
            edtimplementation.PublicPinfo publicPinfoType = edtModuleType.findPublicPinfoByName(name);
            if (publicPinfoType == null) {
                throw new FailedImportException("No PublicPinfo was found in "
                        + edtModuleInstance.getModuleType().getName() + " with the name: " + name);
            }
            edtimplementation.PublicPinfoValue publicPinfoValue = EDTIMPFACTORY.createPublicPinfoValue();
            publicPinfoValue.setPublicPinfoFromModuleType(publicPinfoType);
            publicPinfoValue.setName(name);
            publicPinfoValue.setValue(pinfoValue.getValue());
            edtModuleInstance.getPinfo().add(publicPinfoValue);
        }
    }

    private static void createEDTPropertyValue(edtimplementation.ModuleInstance edtModuleInstance,
            edtimplementation.ModuleType edtModuleType, PropertyValues ecoaPropertyValuesInstance)
            throws FailedImportException {
        for (PropertyValue ecoaPropertyValue : ecoaPropertyValuesInstance.getPropertyValue()) {
            String name = ecoaPropertyValue.getName();
            edtimplementation.ModuleTypeProperty propertyType = edtModuleType.findPropertyTypeByName(name);
            if (propertyType == null) {
                throw new FailedImportException("No Property was found in "
                        + edtModuleInstance.getModuleType().getName() + " with the name: " + name);
            }
            edtimplementation.PropertyValue edtPropertyValue = EDTIMPFACTORY.createPropertyValue();
            edtPropertyValue.setPropertyType(propertyType);
            edtPropertyValue.setName(name);
            edtPropertyValue.setValue(ecoaPropertyValue.getValue());
            edtModuleInstance.getPropertyValues().add(edtPropertyValue);
        }
    }

    private static edtimplementation.TriggerInstance createEDTTriggerInstance(TriggerInstance ecoaTriggerInstance) {
        edtimplementation.TriggerInstance edtTriggerInstance = EDTIMPFACTORY.createTriggerInstance();
        edtTriggerInstance.setName(ecoaTriggerInstance.getName());
        if (ecoaTriggerInstance.getRelativePriority() != null) {
            edtTriggerInstance.setRelativePriority(ecoaTriggerInstance.getRelativePriority());
        }
        
        TriggerSender edtTriggerSender = EDTIMPFACTORY.createTriggerSender();
        edtTriggerSender.setName("TRIGGER");
        edtTriggerInstance.setOperations(edtTriggerSender);
        
        return edtTriggerInstance;
    }

    private static edtimplementation.DynamicTriggerInstance createEDTDynamicTriggerInstance(
            DynamicTriggerInstance ecoaDynamicTriggerInstance, Step0 typeStep) throws FailedImportException {
        var edtDynamicTriggerInstance = EDTIMPFACTORY.createDynamicTriggerInstance();
        edtDynamicTriggerInstance.setName(ecoaDynamicTriggerInstance.getName());
        if (ecoaDynamicTriggerInstance.getRelativePriority() != null) {
            edtDynamicTriggerInstance.setRelativePriority(ecoaDynamicTriggerInstance.getRelativePriority());
        }
        if (ecoaDynamicTriggerInstance.isSetSize()) {
             edtDynamicTriggerInstance.setSize(ecoaDynamicTriggerInstance.getSize());
        }

        for (Parameter param : ecoaDynamicTriggerInstance.getParameter()) {
             edtimplementation.Parameter edtParam = EDTIMPFACTORY.createParameter();
             edtParam.setName(param.getName());
             EDTDataType type = TypesImportConverter.findEDTDataTypeForNonTypes(typeStep, param.getType());
             if (type != null) {
                 edtParam.setType(type);
             } else {
                 throw new FailedImportException("Type " + param.getType() + " not found for parameter " + param.getName());
             }
             edtDynamicTriggerInstance.getParameter().add(edtParam);
        }

        edtimplementation.DynamicTriggerEventReceiverInstance edtEventReceiver = EDTIMPFACTORY.createDynamicTriggerEventReceiverInstance();
        edtEventReceiver.setName("in");
        edtDynamicTriggerInstance.getOperations().add(edtEventReceiver);

        edtimplementation.DynamicTriggerEventSenderInstance edtEventSender = EDTIMPFACTORY.createDynamicTriggerEventSenderInstance();
        edtEventSender.setName("out");
        edtDynamicTriggerInstance.getOperations().add(edtEventSender);
        
        edtimplementation.DynamicTriggerEventReceiverInstance edtEventReset = EDTIMPFACTORY.createDynamicTriggerEventReceiverInstance();
        edtEventReset.setName("reset");
        edtDynamicTriggerInstance.getOperations().add(edtEventReset);

        return edtDynamicTriggerInstance;
    }
}
