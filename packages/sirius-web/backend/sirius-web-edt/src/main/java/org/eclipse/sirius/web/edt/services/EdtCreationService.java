/*******************************************************************************
 * Copyright (c) 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import edtdeployment.Deployment;
import edtdeployment.EdtdeploymentFactory;
import edtimplementation.*;
import edtinterface.EDTInterfaceFactory;
import edtinterface.ServiceDefinition;
import edtlogical.EdtlogicalFactory;
import edtlogical.LogicalComputingNode;
import edtlogical.LogicalSystem;
import edtproject.*;
import edtqos.EdtqosFactory;
import edtqos.ServiceInstanceQos;
import edttype.*;
import edtdds.DDSBinding;
import edtdds.EdtddsFactory;
import edttcp.EdttcpFactory;
import edttcp.TCPBinding;
import edtudp.EdtudpFactory;
import edtudp.UDPBinding;
import edtuid.EdtuidFactory;
import edtuid.IDMap;
import org.eclipse.emf.ecore.EObject;
import org.springframework.stereotype.Service;
import temp.CrossPlatformView;
import temp.TempFactory;


/**
 * Service to handle object creation with default attributes (name, type, etc.).
 * Designed to be called from Sirius Web tool definitions.
 *
 * @author EDT Team
 */
@Service
public class EdtCreationService {

    private final EdtNamingService namingService;

    public EdtCreationService(EdtNamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * Creates a new Library in Step0 with a default name.
     *
     * @param step0 the container Step0
     * @return the created Library
     */
    public Library createLibrary(Step0 step0) {
        Library library = EDTTypeFactory.eINSTANCE.createLibrary();
        String name = this.namingService.checkNameUnique("Library", 
            step0.getTypes().toArray(new EObject[0]), step0.getTypes().size());
        library.setName(name);
        step0.getTypes().add(library);
        return library;
    }

    /**
     * Creates a new ServiceDefinition in Step1 with a default name.
     *
     * @param step1 the container Step1
     * @return the created ServiceDefinition
     */
    public ServiceDefinition createServiceDefinition(Step1 step1) {
        ServiceDefinition serviceDefinition = EDTInterfaceFactory.eINSTANCE.createServiceDefinition();
        String name = this.namingService.checkNameUnique("ServiceDefinition", 
            step1.getServices().toArray(new EObject[0]), step1.getServices().size());
        serviceDefinition.setName(name);
        step1.getServices().add(serviceDefinition);
        return serviceDefinition;
    }

    /**
     * Creates a new ComponentDefinition in Step2 with a default name.
     *
     * @param step2 the container Step2
     * @return the created ComponentDefinition
     */
    public ComponentDefinition createComponentDefinition(Step2 step2) {
        ComponentDefinition componentDefinition = EDTProjectFactory.eINSTANCE.createComponentDefinition();
        String name = this.namingService.checkNameUnique("ComponentDefinition", 
            step2.getComponentDefinitions().toArray(new EObject[0]), step2.getComponentDefinitions().size());
        componentDefinition.setName(name);
        step2.getComponentDefinitions().add(componentDefinition);
        return componentDefinition;
    }

    /**
     * Creates a new Property ("Data") in a ComponentDefinition with a default name.
     *
     * @param componentDefinition the container ComponentDefinition
     * @return the created Property
     */
    public Property createProperty(ComponentDefinition componentDefinition) {
        // Use EDTProjectFactory to create Property
        Property property = EDTProjectFactory.eINSTANCE.createProperty();
        
        componentDefinition.getProperties().add(property);
        
        // Use generic checkNameUnique to avoid type mismatch with sca.Property
        int size = componentDefinition.getProperties().size();
        String name = this.namingService.checkNameUnique("Property" + size,
                componentDefinition.getProperties().toArray(new EObject[0]), size);
                
        property.setName(name);

        return property;
    }
    
     /**
     * Creates a new Property ("Data") in a Composite with a default name.
     *
     * @param composite the container Composite
     * @return the created Property
     */
    public Property createProperty(Composite composite) {
        Property property = EDTProjectFactory.eINSTANCE.createProperty();
        composite.getProperties().add(property);
        
        int size = composite.getProperties().size();
        String name = this.namingService.checkNameUnique("Property" + size,
                composite.getProperties().toArray(new EObject[0]), size);
                
        property.setName(name);
        return property;
    }

    /**
     * Generic create method that can be called with a type name.
     * Matches "Data" to Property creation.
     */
    public EObject createChild(Object container, String typeId) {
        // Unwrap virtual nodes
        EObject realContainer = null;
        if (container instanceof EObject eObject) {
            realContainer = eObject;
        } else if (container instanceof org.eclipse.sirius.web.edt.views.explorer.EdtVirtualGroupNode virtualNode) {
            if (virtualNode.parent() instanceof EObject parentEObject) {
                realContainer = parentEObject;
                // We could use virtualNode.label() or id() to determine subtype if needed
            }
        }

        if (realContainer == null) {
            return null;
        }

        if ("Data".equals(typeId) || "Property".equals(typeId)) {
            if (realContainer instanceof ComponentDefinition cd) {
                return createProperty(cd);
            } else if (realContainer instanceof Composite c) {
                return createProperty(c);
            }
        } else if ("Library".equals(typeId) && realContainer instanceof Step0 s0) {
            return createLibrary(s0);
        } else if ("ServiceDefinition".equals(typeId) && realContainer instanceof Step1 s1) {
            return createServiceDefinition(s1);

        } else if ("ComponentDefinition".equals(typeId) && realContainer instanceof Step2 s2) {
            return createComponentDefinition(s2);
        } else if ("ServiceInstanceQos".equals(typeId)) {
            if (realContainer instanceof ComponentDefinition cd) {
                return createServiceInstanceQos(cd);
            } else if (realContainer instanceof ComponentImplementation ci) {
                return createServiceInstanceQos(ci);
            }
        } else if ("ModuleType".equals(typeId) && realContainer instanceof ComponentImplementation ci) {
            return createModuleType(ci);
        } else if ("ModuleTypeProperty".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createModuleTypeProperty(mt);
        // ModuleOperation subtypes — created under ModuleType.Operations virtual folder
        } else if ("EventSent".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createEventSent(mt);
        } else if ("EventReceived".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createEventReceived(mt);
        } else if ("VersionedDataWritten".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createVersionedDataWritten(mt);
        } else if ("VersionedDataRead".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createVersionedDataRead(mt);
        } else if ("RequestSent".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createRequestSent(mt);
        } else if ("RequestReceived".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createRequestReceived(mt);
        // Pinfo subtypes — created under ModuleType.Pinfo virtual folder
        } else if ("PublicPinfo".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createPublicPinfo(mt);
        } else if ("PrivatePinfo".equals(typeId) && realContainer instanceof ModuleType mt) {
            return createPrivatePinfo(mt);
        } else if ("ComponentImplementation".equals(typeId) && realContainer instanceof Step4 s4) {
            return createComponentImplementation(s4);
        } else if ("RecordPredefined".equals(typeId) && realContainer instanceof Step0 s0) {
            return createRecordPredefined(s0);
        } else if ("ArrayPredefined".equals(typeId) && realContainer instanceof Step0 s0) {
            return createArrayPredefined(s0);
        } else if ("SimplePredefined".equals(typeId) && realContainer instanceof Step0 s0) {
            return createSimplePredefined(s0);
        } else if ("EnumPredefined".equals(typeId) && realContainer instanceof Step0 s0) {
            return createEnumPredefined(s0);
        } else if ("ModuleImplementation".equals(typeId) && realContainer instanceof ComponentImplementation ci) {
            return createModuleImplementation(ci);
        } else if ("ModuleInstance".equals(typeId) && realContainer instanceof ComponentImplementation ci) {
            return createModuleInstance(ci);
        } else if ("TriggerInstance".equals(typeId) && realContainer instanceof ComponentImplementation ci) {
            return createTriggerInstance(ci);
        } else if ("DynamicTriggerInstance".equals(typeId) && realContainer instanceof ComponentImplementation ci) {
            return createDynamicTriggerInstance(ci);
        } else if ("UDPBinding".equals(typeId) && realContainer instanceof Step5 s5) {
            return createUDPBinding(s5);
        } else if ("TCPBinding".equals(typeId) && realContainer instanceof Step5 s5) {
            return createTCPBinding(s5);
        } else if ("DDSBinding".equals(typeId) && realContainer instanceof Step5 s5) {
            return createDDSBinding(s5);
        } else if ("IDMap".equals(typeId) && realContainer instanceof Step5 s5) {
            return createIDMap(s5);
        } else if ("LogicalComputingNode".equals(typeId) && realContainer instanceof Step5 s5) {
            return createLogicalComputingNodeInStep5(s5);
        } else if ("Composite".equals(typeId) && realContainer instanceof Step3 s3) {
            return createInitialAssembly(s3);
        } else if ("LogicalSystem".equals(typeId) && realContainer instanceof Step5 s5) {
            return createLogicalSystem(s5);
        } else if ("Deployment".equals(typeId) && realContainer instanceof Step5 s5) {
            return createDeployment(s5);
        } else if ("FinalAssembly".equals(typeId) && realContainer instanceof Step5 s5) {
            return createFinalAssembly(s5);
        } else if ("CrossPlatformView".equals(typeId) && realContainer instanceof Step5 s5) {
            return createCrossPlatformView(s5);
        }
        
        return null;
    }

    /**
     * Creates a new ServiceInstanceQos in a ComponentDefinition with a default name.
     *
     * @param componentDefinition the container ComponentDefinition
     * @return the created ServiceInstanceQos
     */
    public ServiceInstanceQos createServiceInstanceQos(ComponentDefinition componentDefinition) {
        ServiceInstanceQos serviceInstanceQos = EdtqosFactory.eINSTANCE.createServiceInstanceQos();
        componentDefinition.getAssociatedServiceQos().add(serviceInstanceQos);
        String name = this.namingService.generateServiceQosName(serviceInstanceQos);
        serviceInstanceQos.setName(name);
        return serviceInstanceQos;
    }

    /**
     * Creates a new ServiceInstanceQos in a ComponentImplementation with a default name.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created ServiceInstanceQos
     */
    public ServiceInstanceQos createServiceInstanceQos(ComponentImplementation componentImplementation) {
        ServiceInstanceQos serviceInstanceQos = EdtqosFactory.eINSTANCE.createServiceInstanceQos();
        componentImplementation.getAssociatedServiceQos().add(serviceInstanceQos);
        String name = this.namingService.generateServiceQosName(serviceInstanceQos);
        serviceInstanceQos.setName(name);
        return serviceInstanceQos;
    }

    /**
     * Creates a new ModuleType in a ComponentImplementation with a default name.
     * Name follows the convention "Mt_N" where N is the count.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created ModuleType
     */
    public ModuleType createModuleType(ComponentImplementation componentImplementation) {
        ModuleType moduleType = EdtimplementationFactory.eINSTANCE.createModuleType();
        String name = this.namingService.generateModuleTypeName(componentImplementation);
        moduleType.setName(name);
        
        // Add default Property to satisfy lowerBound=1
        ModuleTypeProperty property = EdtimplementationFactory.eINSTANCE.createModuleTypeProperty();
        property.setName("property");
        moduleType.getProperties().add(property);
        
        // Add default Operation to satisfy lowerBound=1
        EventSent eventSent = EdtimplementationFactory.eINSTANCE.createEventSent();
        eventSent.setName("eventSent");
        moduleType.getOperations().add(eventSent);
        
        componentImplementation.getModuleTypes().add(moduleType);
        return moduleType;
    }

    /**
     * Creates a new ModuleTypeProperty in a ModuleType with a default name.
     *
     * @param moduleType the container ModuleType
     * @return the created ModuleTypeProperty
     */
    public ModuleTypeProperty createModuleTypeProperty(ModuleType moduleType) {
        ModuleTypeProperty property = EdtimplementationFactory.eINSTANCE.createModuleTypeProperty();
        int size = moduleType.getProperties().size();
        String name = this.namingService.checkNameUnique("property" + size,
            moduleType.getProperties().toArray(new EObject[0]), size);
        property.setName(name);
        moduleType.getProperties().add(property);
        return property;
    }

    // ─── ModuleOperation subtypes (for ModuleType.Operations virtual folder) ───

    public ModuleOperation createEventSent(ModuleType moduleType) {
        EventSent op = EdtimplementationFactory.eINSTANCE.createEventSent();
        String name = this.namingService.checkNameUnique("EventSent",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    public ModuleOperation createEventReceived(ModuleType moduleType) {
        EventReceived op = EdtimplementationFactory.eINSTANCE.createEventReceived();
        String name = this.namingService.checkNameUnique("EventReceived",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    public ModuleOperation createVersionedDataWritten(ModuleType moduleType) {
        VersionedDataWritten op = EdtimplementationFactory.eINSTANCE.createVersionedDataWritten();
        String name = this.namingService.checkNameUnique("VersionedDataWritten",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    public ModuleOperation createVersionedDataRead(ModuleType moduleType) {
        VersionedDataRead op = EdtimplementationFactory.eINSTANCE.createVersionedDataRead();
        String name = this.namingService.checkNameUnique("VersionedDataRead",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    public ModuleOperation createRequestSent(ModuleType moduleType) {
        RequestSent op = EdtimplementationFactory.eINSTANCE.createRequestSent();
        String name = this.namingService.checkNameUnique("RequestSent",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    public ModuleOperation createRequestReceived(ModuleType moduleType) {
        RequestReceived op = EdtimplementationFactory.eINSTANCE.createRequestReceived();
        String name = this.namingService.checkNameUnique("RequestReceived",
            moduleType.getOperations().toArray(new EObject[0]), moduleType.getOperations().size());
        op.setName(name);
        moduleType.getOperations().add(op);
        return op;
    }

    // ─── Pinfo subtypes (for ModuleType.Pinfo virtual folder) ───

    public PublicPinfo createPublicPinfo(ModuleType moduleType) {
        PublicPinfo pinfo = EdtimplementationFactory.eINSTANCE.createPublicPinfo();
        String name = this.namingService.checkNameUnique("PublicPinfo",
            moduleType.getPinfo().toArray(new EObject[0]), moduleType.getPinfo().size());
        pinfo.setName(name);
        moduleType.getPinfo().add(pinfo);
        return pinfo;
    }

    public PrivatePinfo createPrivatePinfo(ModuleType moduleType) {
        PrivatePinfo pinfo = EdtimplementationFactory.eINSTANCE.createPrivatePinfo();
        String name = this.namingService.checkNameUnique("PrivatePinfo",
            moduleType.getPinfo().toArray(new EObject[0]), moduleType.getPinfo().size());
        pinfo.setName(name);
        moduleType.getPinfo().add(pinfo);
        return pinfo;
    }

    /**
     * Creates a new ComponentImplementation in Step4 with a default name.
     *
     * @param step4 the container Step4
     * @return the created ComponentImplementation
     */
    public ComponentImplementation createComponentImplementation(Step4 step4) {
        ComponentImplementation componentImplementation = EdtimplementationFactory.eINSTANCE.createComponentImplementation();
        String name = this.namingService.checkNameUnique("ComponentImplementation", 
            step4.getComponentImplementations().toArray(new EObject[0]), step4.getComponentImplementations().size());
        componentImplementation.setName(name);
        
        // Add default insertionPolicyList to satisfy lowerBound=1
        componentImplementation.setInsertionPolicyList(TempFactory.eINSTANCE.createInsertionPolicies());
        
        step4.getComponentImplementations().add(componentImplementation);
        return componentImplementation;
    }

    /**
     * Creates a new RecordPredefined in Step0 with a default name.
     *
     * @param step0 the container Step0
     * @return the created RecordPredefined
     */
    public RecordPredefined createRecordPredefined(Step0 step0) {
        RecordPredefined recordPredefined = EDTTypeFactory.eINSTANCE.createRecordPredefined();
        String name = this.namingService.checkNameUnique("RecordPredefined",
            step0.getEcoaPredefinedTypes().toArray(new EObject[0]), step0.getEcoaPredefinedTypes().size());
        recordPredefined.setName(name);
        step0.getEcoaPredefinedTypes().add(recordPredefined);
        return recordPredefined;
    }

    /**
     * Creates a new ArrayPredefined in Step0 with a default name.
     *
     * @param step0 the container Step0
     * @return the created ArrayPredefined
     */
    public ArrayPredefined createArrayPredefined(Step0 step0) {
        ArrayPredefined arrayPredefined = EDTTypeFactory.eINSTANCE.createArrayPredefined();
        String name = this.namingService.checkNameUnique("ArrayPredefined",
            step0.getEcoaPredefinedTypes().toArray(new EObject[0]), step0.getEcoaPredefinedTypes().size());
        arrayPredefined.setName(name);
        step0.getEcoaPredefinedTypes().add(arrayPredefined);
        return arrayPredefined;
    }

    /**
     * Creates a new SimplePredefined in Step0 with a default name.
     *
     * @param step0 the container Step0
     * @return the created SimplePredefined
     */
    public SimplePredefined createSimplePredefined(Step0 step0) {
        SimplePredefined simplePredefined = EDTTypeFactory.eINSTANCE.createSimplePredefined();
        String name = this.namingService.checkNameUnique("SimplePredefined",
            step0.getEcoaPredefinedTypes().toArray(new EObject[0]), step0.getEcoaPredefinedTypes().size());
        simplePredefined.setName(name);
        step0.getEcoaPredefinedTypes().add(simplePredefined);
        return simplePredefined;
    }

    /**
     * Creates a new EnumPredefined in Step0 with a default name.
     *
     * @param step0 the container Step0
     * @return the created EnumPredefined
     */
    public EnumPredefined createEnumPredefined(Step0 step0) {
        EnumPredefined enumPredefined = EDTTypeFactory.eINSTANCE.createEnumPredefined();
        String name = this.namingService.checkNameUnique("EnumPredefined",
            step0.getEcoaPredefinedTypes().toArray(new EObject[0]), step0.getEcoaPredefinedTypes().size());
        enumPredefined.setName(name);
        step0.getEcoaPredefinedTypes().add(enumPredefined);
        return enumPredefined;
    }

    /**
     * Creates a new ModuleImplementation in a ComponentImplementation with a default name.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created ModuleImplementation
     */
    public ModuleImplementation createModuleImplementation(ComponentImplementation componentImplementation) {
        ModuleImplementation moduleImplementation = EdtimplementationFactory.eINSTANCE.createModuleImplementation();
        String name = this.namingService.checkNameUnique("ModuleImplementation",
            componentImplementation.getModuleImplementations().toArray(new EObject[0]),
            componentImplementation.getModuleImplementations().size());
        moduleImplementation.setName(name);
        componentImplementation.getModuleImplementations().add(moduleImplementation);
        return moduleImplementation;
    }

    /**
     * Creates a new ModuleInstance in a ComponentImplementation with a default name.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created ModuleInstance
     */
    public ModuleInstance createModuleInstance(ComponentImplementation componentImplementation) {
        ModuleInstance moduleInstance = EdtimplementationFactory.eINSTANCE.createModuleInstance();
        String name = this.namingService.checkNameUnique("ModuleInstance",
            componentImplementation.getInstances().toArray(new EObject[0]),
            componentImplementation.getInstances().size());
        moduleInstance.setName(name);
        componentImplementation.getInstances().add(moduleInstance);
        return moduleInstance;
    }

    /**
     * Creates a new TriggerInstance in a ComponentImplementation with a default name.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created TriggerInstance
     */
    public TriggerInstance createTriggerInstance(ComponentImplementation componentImplementation) {
        TriggerInstance triggerInstance = EdtimplementationFactory.eINSTANCE.createTriggerInstance();
        String name = this.namingService.checkNameUnique("TriggerInstance",
            componentImplementation.getInstances().toArray(new EObject[0]),
            componentImplementation.getInstances().size());
        triggerInstance.setName(name);
        componentImplementation.getInstances().add(triggerInstance);
        return triggerInstance;
    }

    /**
     * Creates a new DynamicTriggerInstance in a ComponentImplementation with a default name.
     *
     * @param componentImplementation the container ComponentImplementation
     * @return the created DynamicTriggerInstance
     */
    public DynamicTriggerInstance createDynamicTriggerInstance(ComponentImplementation componentImplementation) {
        DynamicTriggerInstance dynamicTriggerInstance = EdtimplementationFactory.eINSTANCE.createDynamicTriggerInstance();
        String name = this.namingService.checkNameUnique("DynamicTriggerInstance",
            componentImplementation.getInstances().toArray(new EObject[0]),
            componentImplementation.getInstances().size());
        dynamicTriggerInstance.setName(name);
        componentImplementation.getInstances().add(dynamicTriggerInstance);
        return dynamicTriggerInstance;
    }

    /**
     * Creates a new UDPBinding in Step5 with a default name.
     *
     * @param step5 the container Step5
     * @return the created UDPBinding
     */
    public UDPBinding createUDPBinding(Step5 step5) {
        UDPBinding udpBinding = EdtudpFactory.eINSTANCE.createUDPBinding();
        step5.getUDPBindings().add(udpBinding);
        return udpBinding;
    }

    /**
     * Creates a new TCPBinding in Step5 with a default name.
     *
     * @param step5 the container Step5
     * @return the created TCPBinding
     */
    public TCPBinding createTCPBinding(Step5 step5) {
        TCPBinding tcpBinding = EdttcpFactory.eINSTANCE.createTCPBinding();
        step5.getTCPBindings().add(tcpBinding);
        return tcpBinding;
    }

    /**
     * Creates a new DDSBinding in Step5 with default domain 0.
     */
    public DDSBinding createDDSBinding(Step5 step5) {
        DDSBinding ddsBinding = EdtddsFactory.eINSTANCE.createDDSBinding();
        step5.getDDSBindings().add(ddsBinding);
        return ddsBinding;
    }

    /**
     * Creates a new IDMap in Step5 with a default name.
     *
     * @param step5 the container Step5
     * @return the created IDMap
     */
    public IDMap createIDMap(Step5 step5) {
        IDMap idMap = EdtuidFactory.eINSTANCE.createIDMap();
        step5.getIDS().add(idMap);
        return idMap;
    }

    /**
     * Creates a new LogicalComputingNode in Step5's LogicalSystem.
     * If no LogicalSystem exists, one is created first.
     *
     * @param step5 the container Step5
     * @return the created LogicalComputingNode
     */
    public LogicalComputingNode createLogicalComputingNodeInStep5(Step5 step5) {
        LogicalSystem logicalSystem = step5.getLogicalSystem();
        if (logicalSystem == null) {
            logicalSystem = EdtlogicalFactory.eINSTANCE.createLogicalSystem();
            step5.setLogicalSystem(logicalSystem);
        }
        if (logicalSystem.getLogicalComputingPlatforms().isEmpty()) {
            var platform = EdtlogicalFactory.eINSTANCE.createLogicalComputingPlatform();
            logicalSystem.getLogicalComputingPlatforms().add(platform);
        }
        var platform = logicalSystem.getLogicalComputingPlatforms().get(0);
        LogicalComputingNode node = EdtlogicalFactory.eINSTANCE.createLogicalComputingNode();
        platform.getLogicalComputingNodes().add(node);
        return node;
    }

    /**
     * Creates a new Composite (InitialAssembly) in Step3.
     *
     * @param step3 the container Step3
     * @return the created Composite
     */
    public Composite createInitialAssembly(Step3 step3) {
        Composite composite = EDTProjectFactory.eINSTANCE.createComposite();
        composite.setName("InitialAssembly");
        step3.setInitialAssembly(composite);
        return composite;
    }

    /**
     * Creates a new LogicalSystem in Step5.
     *
     * @param step5 the container Step5
     * @return the created LogicalSystem
     */
    public LogicalSystem createLogicalSystem(Step5 step5) {
        LogicalSystem logicalSystem = EdtlogicalFactory.eINSTANCE.createLogicalSystem();
        step5.setLogicalSystem(logicalSystem);
        return logicalSystem;
    }

    /**
     * Creates a new Deployment in Step5.
     *
     * @param step5 the container Step5
     * @return the created Deployment
     */
    public Deployment createDeployment(Step5 step5) {
        Deployment deployment = EdtdeploymentFactory.eINSTANCE.createDeployment();
        step5.setDeployment(deployment);
        return deployment;
    }

    /**
     * Creates a new FinalAssembly in Step5.
     *
     * @param step5 the container Step5
     * @return the created FinalAssembly
     */
    public FinalAssembly createFinalAssembly(Step5 step5) {
        FinalAssembly finalAssembly = EDTProjectFactory.eINSTANCE.createFinalAssembly();
        step5.setFinalAssembly(finalAssembly);
        return finalAssembly;
    }

    /**
     * Creates a new CrossPlatformView in Step5.
     *
     * @param step5 the container Step5
     * @return the created CrossPlatformView
     */
    public CrossPlatformView createCrossPlatformView(Step5 step5) {
        CrossPlatformView crossPlatformView = TempFactory.eINSTANCE.createCrossPlatformView();
        step5.setCrossPlatformView(crossPlatformView);
        return crossPlatformView;
    }
}
