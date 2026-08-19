package org.eclipse.sirius.web.edt.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.sirius.web.edt.importexport.EcoaInterfaceXmlCache;
import org.eclipse.sirius.web.edt.importexport.FailedImportException;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentImplementationDataLinkImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentImplementationEventLinkImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentImplementationRequestLinkImportConverter;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.edt.importexport.converters.ServiceDefinitionImportConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edtinterface.ServiceDefinition;
import edtimplementation.ComponentImplementation;
import edtimplementation.DataLink;
import edtimplementation.DataLinkActivatableFifo;
import edtimplementation.DataLinkToServiceOperation;
import edtimplementation.DataReaderInstance;
import edtimplementation.DataWriterInstance;
import edtimplementation.EventDefinitionInstance;
import edtimplementation.EventLink;
import edtimplementation.EventLinkActivatableFifo;
import edtimplementation.EventLinkActivatableFifoFromTrigger;
import edtimplementation.EventLinkActivatingFifo;
import edtimplementation.EventLinkActivatingFifoFromTrigger;
import edtimplementation.EventLinkReceiver;
import edtimplementation.EventLinkSender;
import edtimplementation.EventLinkToDefinitionOperation;
import edtimplementation.EventLinkToDefinitionOperationFromTrigger;
import edtimplementation.EventReceiverInstance;
import edtimplementation.OperationInheritingFromSD;
import edtimplementation.OperationInstance;
import edtimplementation.OperationLink;
import edtimplementation.ModuleInstance;
import edtimplementation.ReferenceOfLinkedComponentDefinition;
import edtimplementation.RequestClientInstance;
import edtimplementation.RequestLink;
import edtimplementation.RequestLinkActivatableFifo;
import edtimplementation.RequestLinkActivatingActivatableFifo;
import edtimplementation.RequestLinkActivatingToReferenceOperation;
import edtimplementation.RequestReferenceInstance;
import edtimplementation.RequestServerInstance;
import edtimplementation.RequestServiceInstance;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtimplementation.ServRefOfLinkedComponentDefinition;
import edtimplementation.VersionedDataReferenceInstance;
import edtimplementation.VersionedDataServiceInstance;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step4;
import edtproject.Steps;
import technology.ecoa.implementation._2.impPackage;
import technology.ecoa.implementation._2.util.impResourceFactoryImpl;
import technology.ecoa.interface_._2.interPackage;
import technology.ecoa.interface_._2.util.interResourceFactoryImpl;

/**
 * Repairs imported service definitions whose operations were lost after JSON persistence.
 */
@Service
public class EdtServiceDefinitionRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtServiceDefinitionRepairService.class);
    private static final String SERVICES_DIR = "1-Services";

    private final IProjectEditingContextService projectEditingContextService;
    private final EcoaInterfaceXmlCache interfaceXmlCache;
    private final String workspaceDir;

    public EdtServiceDefinitionRepairService(IProjectEditingContextService projectEditingContextService, EcoaInterfaceXmlCache interfaceXmlCache,
            @Value("${ecoa.workspace.dir}") String workspaceDir) {
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
        this.interfaceXmlCache = Objects.requireNonNull(interfaceXmlCache);
        this.workspaceDir = Objects.requireNonNull(workspaceDir);
    }

    public boolean repairIfNeeded(String editingContextId, ResourceSet resourceSet) {
        LOGGER.debug("[SVC-REPAIR] Checking whether repair is needed for editing context {}", editingContextId);
        Optional<Steps> optionalSteps = resourceSet.getResources().stream()
                .filter(resource -> !resource.getContents().isEmpty() && resource.getContents().get(0) instanceof Steps)
                .map(resource -> (Steps) resource.getContents().get(0))
                .findFirst();

        if (optionalSteps.isEmpty()) {
            LOGGER.debug("[SVC-REPAIR] No Steps root found in resource set for editing context {}", editingContextId);
            return false;
        }

        Steps steps = optionalSteps.get();
        Step0 step0 = steps.getStep0();
        Step1 step1 = steps.getStep1();
        Step4 step4 = steps.getStep4();
        if (step0 == null || step1 == null || step1.getServices().isEmpty()) {
            LOGGER.debug("[SVC-REPAIR] Skipping repair for editing context {} because step0={}, step1={}, serviceCount={}",
                    editingContextId, step0 != null, step1 != null, step1 != null ? step1.getServices().size() : -1);
            return false;
        }

        boolean missingServiceDefinitionOperations = step1.getServices().stream().anyMatch(service -> service.getOperations().isEmpty());
        boolean brokenOperationGraph = this.hasBrokenOperationGraph(step4);
        if (!missingServiceDefinitionOperations && !brokenOperationGraph) {
            LOGGER.debug("[SVC-REPAIR] Skipping repair for editing context {} because service definitions and operation links look consistent",
                    editingContextId);
            return false;
        }

        Optional<Path> optionalServicesDir = this.resolveLatestServicesDir(editingContextId);
        if (optionalServicesDir.isEmpty() && missingServiceDefinitionOperations) {
            LOGGER.warn("[SVC-REPAIR] Could not locate exported service XML directory for editing context {}", editingContextId);
            return false;
        }

        Path servicesDir = optionalServicesDir.orElse(null);
        int repairedCount = 0;

        if (servicesDir != null) {
            for (ServiceDefinition serviceDefinition : step1.getServices()) {
                if (!serviceDefinition.getOperations().isEmpty()) {
                    continue;
                }

                String interfaceFileName = serviceDefinition.getName() + ".interface.xml";
                Path interfaceXml = servicesDir.resolve(interfaceFileName);
                if (!Files.isRegularFile(interfaceXml) && this.interfaceXmlCache.get(editingContextId, interfaceFileName) == null) {
                    LOGGER.debug("[SVC-REPAIR] No interface XML found for '{}' at {}", serviceDefinition.getName(), interfaceXml);
                    continue;
                }

                try {
                    technology.ecoa.interface_._2.ServiceDefinition ecoaServiceDefinition = this.loadInterfaceDefinition(editingContextId, interfaceFileName, interfaceXml);
                    ServiceDefinition repaired = ServiceDefinitionImportConverter.createEDTServiceDefinition(
                            ecoaServiceDefinition, interfaceFileName, step0);

                    if (!repaired.getOperations().isEmpty()) {
                        serviceDefinition.getOperations().clear();
                        serviceDefinition.getOperations().addAll(repaired.getOperations());
                        serviceDefinition.getUsedLibraries().clear();
                        serviceDefinition.getUsedLibraries().addAll(repaired.getUsedLibraries());
                        repairedCount++;
                        LOGGER.info("[SVC-REPAIR] Restored {} operation(s) for '{}'",
                                serviceDefinition.getOperations().size(), serviceDefinition.getName());
                    }
                } catch (IOException exception) {
                    LOGGER.warn("[SVC-REPAIR] Failed to restore service definition '{}' from {}",
                            serviceDefinition.getName(), interfaceXml, exception);
                }
            }
        }

        if (repairedCount > 0 || brokenOperationGraph) {
            this.rebuildModuleInstanceOperations(step4);
            this.rebuildLinkedComponentDefinitionOperations(step4);
            if (servicesDir != null) {
                this.reloadBrokenOperationLinks(editingContextId, step0, step4, servicesDir);
            }
            this.rebindOperationLinks(step4);
            LOGGER.info("[SVC-REPAIR] Repaired {} service definition(s), brokenOperationGraph={} from {}",
                    repairedCount, brokenOperationGraph, servicesDir);
            return true;
        }
        return false;
    }

    public void prepareForPersistence(String editingContextId, ResourceSet resourceSet) {
        Optional<Steps> optionalSteps = this.findSteps(resourceSet);
        if (optionalSteps.isEmpty()) {
            LOGGER.debug("[SVC-PERSIST] Skipping persist preparation for editing context {} because no Steps root was found",
                    editingContextId);
            return;
        }

        Step4 step4 = optionalSteps.get().getStep4();
        if (step4 == null) {
            LOGGER.debug("[SVC-PERSIST] Skipping persist preparation for editing context {} because step4 is missing",
                    editingContextId);
            return;
        }

        this.logOperationLinkSnapshot(editingContextId, step4, "beforePersist");
        int reboundLinkCount = this.rebindOperationLinks(step4, "[SVC-PERSIST]");
        LOGGER.info("[SVC-PERSIST] Prepared editing context {} for persistence, reboundEndpointCount={}",
                editingContextId, reboundLinkCount);
        this.logOperationLinkSnapshot(editingContextId, step4, "afterPersistPreparation");
    }

    private void rebuildLinkedComponentDefinitionOperations(Step4 step4) {
        if (step4 == null) {
            return;
        }

        int rebuiltServiceCount = 0;
        int rebuiltReferenceCount = 0;

        for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
            for (ServiceOfLinkedComponentDefinition service : componentImplementation.getComponentDefinitionServices()) {
                if (this.shouldRebuild(service)) {
                    service.setServiceDefinitionLink(service.getServiceDefinitionLink());
                    rebuiltServiceCount++;
                }
            }
            for (ReferenceOfLinkedComponentDefinition reference : componentImplementation.getComponentDefinitionReferences()) {
                if (this.shouldRebuild(reference)) {
                    reference.setServiceDefinitionLink(reference.getServiceDefinitionLink());
                    rebuiltReferenceCount++;
                }
            }
        }

        if (rebuiltServiceCount > 0 || rebuiltReferenceCount > 0) {
            LOGGER.info("[SVC-REPAIR] Rebuilt linked operations for {} service(s) and {} reference(s)",
                    rebuiltServiceCount, rebuiltReferenceCount);
        }
    }

    private void rebuildModuleInstanceOperations(Step4 step4) {
        if (step4 == null) {
            return;
        }

        int rebuiltModuleInstanceCount = 0;
        for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
            for (var instance : componentImplementation.getInstances()) {
                if (instance instanceof ModuleInstance moduleInstance
                        && moduleInstance.getOperations().isEmpty()
                        && moduleInstance.getModuleType() != null
                        && !moduleInstance.getModuleType().getOperations().isEmpty()) {
                    moduleInstance.setModuleType(moduleInstance.getModuleType());
                    rebuiltModuleInstanceCount++;
                    LOGGER.info("[SVC-REPAIR] Rebuilt {} module operation(s) for module instance '{}'",
                            moduleInstance.getOperations().size(), moduleInstance.getName());
                }
            }
        }

        if (rebuiltModuleInstanceCount > 0) {
            LOGGER.info("[SVC-REPAIR] Rebuilt module-instance operations for {} module instance(s)", rebuiltModuleInstanceCount);
        }
    }

    private boolean shouldRebuild(edtimplementation.ServRefOfLinkedComponentDefinition servRef) {
        return servRef.getOperations().isEmpty()
                && servRef.getServiceDefinitionLink() != null
                && !servRef.getServiceDefinitionLink().getOperations().isEmpty();
    }

    private boolean hasBrokenOperationGraph(Step4 step4) {
        if (step4 == null) {
            return false;
        }
        return step4.getComponentImplementations().stream().anyMatch(componentImplementation ->
                componentImplementation.getInstances().stream()
                        .filter(ModuleInstance.class::isInstance)
                        .map(ModuleInstance.class::cast)
                        .anyMatch(moduleInstance -> moduleInstance.getModuleType() != null && moduleInstance.getOperations().isEmpty())
                || componentImplementation.getComponentDefinitionServices().stream().anyMatch(this::shouldRebuild)
                || componentImplementation.getComponentDefinitionReferences().stream().anyMatch(this::shouldRebuild)
                || this.hasBrokenOperationLink(componentImplementation));
    }

    private void rebindOperationLinks(Step4 step4) {
        this.rebindOperationLinks(step4, "[SVC-REPAIR]");
    }

    private int rebindOperationLinks(Step4 step4, String logPrefix) {
        if (step4 == null) {
            return 0;
        }

        int reboundLinkCount = 0;
        for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
            LOGGER.debug("{} Inspecting {} operation link(s) for component implementation '{}'",
                    logPrefix,
                    componentImplementation.getOperationLinks().size(), componentImplementation.getName());
            for (OperationLink operationLink : componentImplementation.getOperationLinks()) {
                LOGGER.debug("{} OperationLink class={} id={}",
                        logPrefix,
                        operationLink.eClass().getName(), operationLink.getId());
                if (this.rebindOperationLink(componentImplementation, operationLink)) {
                    reboundLinkCount++;
                }
            }
        }

        if (reboundLinkCount > 0) {
            LOGGER.info("{} Rebound {} operation link endpoint(s)", logPrefix, reboundLinkCount);
        }
        return reboundLinkCount;
    }

    private void reloadBrokenOperationLinks(String editingContextId, Step0 step0, Step4 step4, Path servicesDir) {
        if (step0 == null || step4 == null || servicesDir == null || servicesDir.getParent() == null || servicesDir.getParent().getParent() == null) {
            return;
        }

        Path runRoot = servicesDir.getParent().getParent();
        Path componentImplementationsDir = runRoot.resolve("Steps").resolve("4-ComponentImplementations");
        if (!Files.isDirectory(componentImplementationsDir)) {
            LOGGER.warn("[SVC-REPAIR] Component implementation directory not found at {}", componentImplementationsDir);
            return;
        }

        int reloadedCount = 0;
        for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
            if (!this.hasBrokenOperationLink(componentImplementation)) {
                continue;
            }

            Optional<Path> optionalImplXml = this.resolveImplementationXml(componentImplementationsDir, componentImplementation.getName());
            String implFileName = componentImplementation.getName() + ".impl.xml";
            if (optionalImplXml.isEmpty() && this.interfaceXmlCache.get(editingContextId, implFileName) == null) {
                LOGGER.warn("[SVC-REPAIR] Could not find implementation XML for '{}'", componentImplementation.getName());
                continue;
            }

            try {
                technology.ecoa.implementation._2.ComponentImplementation ecoaImplementation =
                        this.loadImplementationDefinition(editingContextId, implFileName, optionalImplXml.orElse(null));
                componentImplementation.getOperationLinks().clear();
                ecoaImplementation.getDataLink().forEach(dataLink -> {
                    try {
                        componentImplementation.getOperationLinks().addAll(
                                ComponentImplementationDataLinkImportConverter.createEDTDataLink(dataLink, componentImplementation));
                    } catch (FailedImportException exception) {
                        throw new RuntimeException(exception);
                    }
                });
                ecoaImplementation.getEventLink().forEach(eventLink -> {
                    try {
                        componentImplementation.getOperationLinks().addAll(
                                ComponentImplementationEventLinkImportConverter.createEDTEventLink(eventLink, componentImplementation));
                    } catch (FailedImportException exception) {
                        throw new RuntimeException(exception);
                    }
                });
                ecoaImplementation.getRequestLink().forEach(requestLink -> {
                    try {
                        componentImplementation.getOperationLinks().addAll(
                                ComponentImplementationRequestLinkImportConverter.createEDTRequestLink(requestLink, componentImplementation));
                    } catch (FailedImportException exception) {
                        throw new RuntimeException(exception);
                    }
                });
                reloadedCount++;
                LOGGER.info("[SVC-REPAIR] Reloaded {} operation link(s) for '{}' from {}",
                        componentImplementation.getOperationLinks().size(), componentImplementation.getName(), optionalImplXml.orElse(null));
            } catch (IOException exception) {
                LOGGER.warn("[SVC-REPAIR] Failed to parse implementation XML for '{}'",
                        componentImplementation.getName(), exception);
            } catch (RuntimeException exception) {
                LOGGER.warn("[SVC-REPAIR] Failed to rebuild operation links for '{}'",
                        componentImplementation.getName(), exception.getCause() != null ? exception.getCause() : exception);
            }
        }

        if (reloadedCount > 0) {
            LOGGER.info("[SVC-REPAIR] Reloaded broken operation links for {} component implementation(s)", reloadedCount);
        }
    }

    private boolean hasBrokenOperationLink(ComponentImplementation componentImplementation) {
        return componentImplementation.getOperationLinks().stream().anyMatch(this::hasBrokenEndpoint);
    }

    private boolean hasBrokenEndpoint(OperationLink operationLink) {
        if (operationLink instanceof DataLinkToServiceOperation dataLink) {
            return this.isBrokenEndpoint(dataLink.getWriter()) || this.isBrokenEndpoint(dataLink.getReader());
        }
        if (operationLink instanceof DataLinkActivatableFifo dataLink) {
            return this.isBrokenEndpoint(dataLink.getWriter()) || this.isBrokenEndpoint(dataLink.getReader());
        }
        if (operationLink instanceof EventLinkToDefinitionOperation eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof EventLinkToDefinitionOperationFromTrigger eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof EventLinkActivatableFifo eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof EventLinkActivatableFifoFromTrigger eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof EventLinkActivatingFifo eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof EventLinkActivatingFifoFromTrigger eventLink) {
            return this.isBrokenEndpoint(eventLink.getSender()) || this.isBrokenEndpoint(eventLink.getReceiver());
        }
        if (operationLink instanceof RequestLinkActivatableFifo requestLink) {
            return this.isBrokenEndpoint(requestLink.getClient()) || this.isBrokenEndpoint(requestLink.getServer());
        }
        if (operationLink instanceof RequestLinkActivatingActivatableFifo requestLink) {
            return this.isBrokenEndpoint(requestLink.getClient()) || this.isBrokenEndpoint(requestLink.getServer());
        }
        if (operationLink instanceof RequestLinkActivatingToReferenceOperation requestLink) {
            return this.isBrokenEndpoint(requestLink.getClient()) || this.isBrokenEndpoint(requestLink.getServer());
        }
        return false;
    }

    private boolean isBrokenEndpoint(OperationInstance operationInstance) {
        return operationInstance == null || operationInstance.eContainer() == null;
    }

    private Optional<Path> resolveImplementationXml(Path componentImplementationsDir, String componentImplementationName) {
        try (Stream<Path> paths = Files.find(componentImplementationsDir, 3,
                (path, attributes) -> attributes.isRegularFile()
                        && Objects.equals(path.getFileName().toString(), componentImplementationName + ".impl.xml"))) {
            return paths.findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private boolean rebindOperationLink(ComponentImplementation componentImplementation, OperationLink operationLink) {
        boolean rebound = false;

        if (operationLink instanceof DataLinkToServiceOperation dataLink) {
            LOGGER.debug("[SVC-REPAIR] DataLinkToServiceOperation writerClass={} readerClass={}",
                    this.className(dataLink.getWriter()), this.className(dataLink.getReader()));
            OperationInstance writer = this.findReplacementModuleOperation(componentImplementation, dataLink.getWriter());
            if (writer instanceof DataWriterInstance replacement && replacement != dataLink.getWriter()) {
                dataLink.setWriter(replacement);
                rebound = true;
            }
            OperationInstance reader = this.findReplacementSdOperation(componentImplementation, dataLink.getReader());
            if (reader instanceof VersionedDataServiceInstance replacement && replacement != dataLink.getReader()) {
                dataLink.setReader(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof EventLinkToDefinitionOperation eventLink) {
            LOGGER.debug("[SVC-REPAIR] EventLinkToDefinitionOperation senderClass={} receiverClass={}",
                    this.className(eventLink.getSender()), this.className(eventLink.getReceiver()));
            OperationInstance sender = this.findReplacementModuleOperation(componentImplementation, eventLink.getSender());
            if (sender instanceof EventLinkSender replacement && replacement != eventLink.getSender()) {
                eventLink.setSender(replacement);
                rebound = true;
            }
            OperationInstance receiver = this.findReplacementSdOperation(componentImplementation, eventLink.getReceiver());
            if (receiver instanceof EventDefinitionInstance replacement && replacement != eventLink.getReceiver()) {
                eventLink.setReceiver(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof EventLinkToDefinitionOperationFromTrigger eventLink) {
            LOGGER.debug("[SVC-REPAIR] EventLinkToDefinitionOperationFromTrigger senderClass={} receiverClass={}",
                    this.className(eventLink.getSender()), this.className(eventLink.getReceiver()));
            OperationInstance receiver = this.findReplacementSdOperation(componentImplementation, eventLink.getReceiver());
            if (receiver instanceof EventDefinitionInstance replacement && replacement != eventLink.getReceiver()) {
                eventLink.setReceiver(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof EventLinkActivatableFifo eventLink) {
            LOGGER.debug("[SVC-REPAIR] EventLinkActivatableFifo senderClass={} receiverClass={}",
                    this.className(eventLink.getSender()), this.className(eventLink.getReceiver()));
            OperationInstance sender = this.findReplacementModuleOperation(componentImplementation, eventLink.getSender());
            if (sender instanceof EventLinkSender replacement && replacement != eventLink.getSender()) {
                eventLink.setSender(replacement);
                rebound = true;
            }
            OperationInstance receiver = this.findReplacementModuleOperation(componentImplementation, eventLink.getReceiver());
            if (receiver instanceof EventReceiverInstance replacement && replacement != eventLink.getReceiver()) {
                eventLink.setReceiver(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof EventLinkActivatingFifo eventLink) {
            LOGGER.debug("[SVC-REPAIR] EventLinkActivatingFifo senderClass={} receiverClass={}",
                    this.className(eventLink.getSender()), this.className(eventLink.getReceiver()));
            OperationInstance sender = this.findReplacementModuleOperation(componentImplementation, eventLink.getSender());
            if (sender instanceof EventLinkSender replacement && replacement != eventLink.getSender()) {
                eventLink.setSender(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof EventLinkActivatableFifoFromTrigger) {
            // Trigger sender is not rebuilt from service definitions.
        } else if (operationLink instanceof EventLinkActivatingFifoFromTrigger) {
            // Trigger sender is not rebuilt from service definitions.
        } else if (operationLink instanceof RequestLinkActivatingToReferenceOperation requestLink) {
            LOGGER.debug("[SVC-REPAIR] RequestLinkActivatingToReferenceOperation clientClass={} serverClass={}",
                    this.className(requestLink.getClient()), this.className(requestLink.getServer()));
            OperationInstance server = this.findReplacementSdOperation(componentImplementation, requestLink.getServer());
            if (server instanceof RequestReferenceInstance replacement && replacement != requestLink.getServer()) {
                requestLink.setServer(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof RequestLinkActivatableFifo requestLink) {
            LOGGER.debug("[SVC-REPAIR] RequestLinkActivatableFifo clientClass={} serverClass={}",
                    this.className(requestLink.getClient()), this.className(requestLink.getServer()));
            OperationInstance client = this.findReplacementSdOperation(componentImplementation, requestLink.getClient());
            if (client instanceof RequestServiceInstance replacement && replacement != requestLink.getClient()) {
                requestLink.setClient(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof RequestLinkActivatingActivatableFifo requestLink) {
            LOGGER.debug("[SVC-REPAIR] RequestLinkActivatingActivatableFifo clientClass={} serverClass={}",
                    this.className(requestLink.getClient()), this.className(requestLink.getServer()));
            OperationInstance client = this.findReplacementModuleOperation(componentImplementation, requestLink.getClient());
            if (client instanceof RequestClientInstance replacement && replacement != requestLink.getClient()) {
                requestLink.setClient(replacement);
                rebound = true;
            }
            OperationInstance server = this.findReplacementModuleOperation(componentImplementation, requestLink.getServer());
            if (server instanceof RequestServerInstance replacement && replacement != requestLink.getServer()) {
                requestLink.setServer(replacement);
                rebound = true;
            }
        } else if (operationLink instanceof DataLinkActivatableFifo dataLink) {
            LOGGER.debug("[SVC-REPAIR] DataLinkActivatableFifo writerClass={} readerClass={}",
                    this.className(dataLink.getWriter()), this.className(dataLink.getReader()));
            OperationInstance writer = this.findReplacementModuleOperation(componentImplementation, dataLink.getWriter());
            if (writer instanceof DataWriterInstance replacement && replacement != dataLink.getWriter()) {
                dataLink.setWriter(replacement);
                rebound = true;
            }
            OperationInstance reader = this.findReplacementModuleOperation(componentImplementation, dataLink.getReader());
            if (reader instanceof DataReaderInstance replacement && replacement != dataLink.getReader()) {
                dataLink.setReader(replacement);
                rebound = true;
            }
        }

        return rebound;
    }

    private OperationInstance findReplacementSdOperation(ComponentImplementation componentImplementation, OperationInstance existingOperation) {
        if (!(existingOperation instanceof OperationInheritingFromSD sdOperation)) {
            return null;
        }

        var sdOperationRef = sdOperation.getSDOperationRef();
        var operationName = existingOperation.getName();
        if (sdOperationRef == null || operationName == null) {
            return null;
        }

        for (ServiceOfLinkedComponentDefinition service : componentImplementation.getComponentDefinitionServices()) {
            OperationInstance replacement = this.findMatchingOperation(service, existingOperation, sdOperationRef, operationName);
            if (replacement != null) {
                return replacement;
            }
        }
        for (ReferenceOfLinkedComponentDefinition reference : componentImplementation.getComponentDefinitionReferences()) {
            OperationInstance replacement = this.findMatchingOperation(reference, existingOperation, sdOperationRef, operationName);
            if (replacement != null) {
                return replacement;
            }
        }
        return null;
    }

    private OperationInstance findReplacementModuleOperation(ComponentImplementation componentImplementation, OperationInstance existingOperation) {
        if (existingOperation == null) {
            return null;
        }

        EObject container = existingOperation.eContainer();
        if (container instanceof ModuleInstance moduleInstance) {
            OperationInstance replacement = this.findMatchingOperation(moduleInstance, existingOperation);
            if (replacement != null) {
                return replacement;
            }
        }

        OperationInstance uniqueMatch = null;
        for (EObject instance : componentImplementation.getInstances()) {
            if (instance instanceof ModuleInstance moduleInstance) {
                OperationInstance candidate = this.findMatchingOperation(moduleInstance, existingOperation);
                if (candidate == null) {
                    continue;
                }
                if (uniqueMatch != null && uniqueMatch != candidate) {
                    return uniqueMatch;
                }
                uniqueMatch = candidate;
            }
        }
        return uniqueMatch;
    }

    private String className(Object object) {
        return object != null ? object.getClass().getName() : "null";
    }

    private OperationInstance findMatchingOperation(ModuleInstance owner, OperationInstance expectedOperation) {
        for (OperationInstance candidate : owner.getOperations()) {
            if (this.isSameOperation(candidate, expectedOperation)) {
                return candidate;
            }
        }
        return null;
    }

    private OperationInstance findMatchingOperation(ServRefOfLinkedComponentDefinition owner, OperationInstance expectedType,
            edtinterface.OperationType sdOperationRef, String operationName) {
        for (OperationInstance candidate : owner.getOperations()) {
            if (this.isSameOperation(candidate, expectedType)
                    && candidate instanceof OperationInheritingFromSD candidateSdOperation
                    && Objects.equals(sdOperationRef, candidateSdOperation.getSDOperationRef())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSameOperation(OperationInstance candidate, OperationInstance expectedOperation) {
        return expectedOperation != null
                && expectedOperation.getClass().equals(candidate.getClass())
                && Objects.equals(expectedOperation.getName(), candidate.getName());
    }

    private Optional<Steps> findSteps(ResourceSet resourceSet) {
        return resourceSet.getResources().stream()
                .filter(resource -> !resource.getContents().isEmpty() && resource.getContents().get(0) instanceof Steps)
                .map(resource -> (Steps) resource.getContents().get(0))
                .findFirst();
    }

    private void logOperationLinkSnapshot(String editingContextId, Step4 step4, String phase) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        AtomicInteger linkCount = new AtomicInteger();
        AtomicInteger danglingEndpointCount = new AtomicInteger();

        for (ComponentImplementation componentImplementation : step4.getComponentImplementations()) {
            for (OperationLink operationLink : componentImplementation.getOperationLinks()) {
                linkCount.incrementAndGet();
                this.logOperationLinkDetails(editingContextId, phase, componentImplementation, operationLink, danglingEndpointCount);
            }
        }

        LOGGER.debug("[SVC-SNAPSHOT] editingContextId={} phase={} componentImplementationCount={} operationLinkCount={} danglingEndpointCount={}",
                editingContextId,
                phase,
                step4.getComponentImplementations().size(),
                linkCount.get(),
                danglingEndpointCount.get());
    }

    private void logOperationLinkDetails(String editingContextId, String phase, ComponentImplementation componentImplementation,
            OperationLink operationLink, AtomicInteger danglingEndpointCount) {
        if (operationLink instanceof DataLinkToServiceOperation dataLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "writer", dataLink.getWriter(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "reader", dataLink.getReader(), danglingEndpointCount);
        } else if (operationLink instanceof DataLinkActivatableFifo dataLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "writer", dataLink.getWriter(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "reader", dataLink.getReader(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkToDefinitionOperation eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkToDefinitionOperationFromTrigger eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkActivatableFifo eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkActivatableFifoFromTrigger eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkActivatingFifo eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof EventLinkActivatingFifoFromTrigger eventLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "sender", eventLink.getSender(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "receiver", eventLink.getReceiver(), danglingEndpointCount);
        } else if (operationLink instanceof RequestLinkActivatableFifo requestLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "client", requestLink.getClient(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "server", requestLink.getServer(), danglingEndpointCount);
        } else if (operationLink instanceof RequestLinkActivatingActivatableFifo requestLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "client", requestLink.getClient(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "server", requestLink.getServer(), danglingEndpointCount);
        } else if (operationLink instanceof RequestLinkActivatingToReferenceOperation requestLink) {
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "client", requestLink.getClient(), danglingEndpointCount);
            this.logEndpoint(editingContextId, phase, componentImplementation.getName(), operationLink, "server", requestLink.getServer(), danglingEndpointCount);
        }
    }

    private void logEndpoint(String editingContextId, String phase, String componentImplementationName, OperationLink operationLink,
            String endpointRole, OperationInstance endpoint, AtomicInteger danglingEndpointCount) {
        if (endpoint == null || endpoint.eContainer() == null) {
            danglingEndpointCount.incrementAndGet();
        }

        EObject container = endpoint != null ? endpoint.eContainer() : null;
        LOGGER.debug("[SVC-SNAPSHOT] editingContextId={} phase={} componentImplementation={} operationLinkClass={} operationLinkId={} endpointRole={} endpointClass={} endpointName={} endpointContainerClass={} endpointContainerName={}",
                editingContextId,
                phase,
                componentImplementationName,
                operationLink.eClass().getName(),
                operationLink.getId(),
                endpointRole,
                this.className(endpoint),
                endpoint != null ? endpoint.getName() : "null",
                this.className(container),
                this.semanticNameOf(container));
    }

    private String identityOf(Object object) {
        return object != null ? Integer.toHexString(System.identityHashCode(object)) : "null";
    }

    private String semanticNameOf(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            var method = object.getClass().getMethod("getName");
            Object value = method.invoke(object);
            return String.valueOf(value);
        } catch (ReflectiveOperationException exception) {
            return "null";
        }
    }

    private Optional<Path> resolveLatestServicesDir(String editingContextId) {
        Optional<String> optionalProjectId = this.projectEditingContextService.getProjectId(editingContextId);
        optionalProjectId.ifPresent(projectId ->
                LOGGER.debug("[SVC-REPAIR] Resolved projectId {} for editing context {}", projectId, editingContextId));

        Optional<Path> projectWorkspaceDir = optionalProjectId
                .map(projectId -> Paths.get(this.workspaceDir, projectId));
        projectWorkspaceDir.ifPresent(path -> LOGGER.debug("[SVC-REPAIR] Inspecting project workspace directory {}", path));

        Optional<Path> projectScopedDirectory = projectWorkspaceDir
                .filter(Files::isDirectory)
                .flatMap(this::findLatestServicesDir);

        if (projectScopedDirectory.isPresent()) {
            return projectScopedDirectory;
        }

        LOGGER.warn("[SVC-REPAIR] Falling back to global workspace scan under {}", this.workspaceDir);
        return this.findLatestServicesDirInWorkspace(Paths.get(this.workspaceDir));
    }

    private Optional<Path> findLatestServicesDir(Path projectWorkspaceDir) {
        Path legacyDir = projectWorkspaceDir.resolve("Steps").resolve(SERVICES_DIR);
        if (Files.isDirectory(legacyDir)) {
            return Optional.of(legacyDir);
        }

        try (Stream<Path> children = Files.list(projectWorkspaceDir)) {
            return children
                    .filter(Files::isDirectory)
                    .map(workspace -> workspace.resolve("Steps").resolve(SERVICES_DIR))
                    .filter(Files::isDirectory)
                    .max(Comparator.comparingLong(this::lastModifiedOrMin));
        } catch (IOException exception) {
            LOGGER.warn("[SVC-REPAIR] Failed to inspect workspace directory {}", projectWorkspaceDir, exception);
            return Optional.empty();
        }
    }

    private Optional<Path> findLatestServicesDirInWorkspace(Path workspaceRoot) {
        if (!Files.isDirectory(workspaceRoot)) {
            return Optional.empty();
        }

        try (Stream<Path> paths = Files.find(workspaceRoot, 4,
                (path, attributes) -> attributes.isDirectory() && Objects.equals(path.getFileName().toString(), SERVICES_DIR))) {
            return paths
                    .filter(path -> path.getParent() != null && Objects.equals(path.getParent().getFileName().toString(), "Steps"))
                    .filter(this::containsInterfaceXml)
                    .max(Comparator.comparingLong(this::lastModifiedOrMin));
        } catch (IOException exception) {
            LOGGER.warn("[SVC-REPAIR] Failed to scan workspace root {}", workspaceRoot, exception);
            return Optional.empty();
        }
    }

    private boolean containsInterfaceXml(Path servicesDir) {
        try (Stream<Path> children = Files.list(servicesDir)) {
            return children.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".interface.xml"));
        } catch (IOException exception) {
            return false;
        }
    }

    private long lastModifiedOrMin(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private technology.ecoa.interface_._2.ServiceDefinition parseInterfaceXml(Path path) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xml", new interResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(interPackage.eNS_URI, interPackage.eINSTANCE);

        URI uri = URI.createFileURI(path.toAbsolutePath().toString());
        Resource resource = resourceSet.getResource(uri, false);
        if (resource == null) {
            resource = resourceSet.createResource(uri);
        }

        Map<Object, Object> loadOptions = new HashMap<>();
        loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        resource.load(loadOptions);

        if (!resource.getContents().isEmpty()) {
            EObject root = resource.getContents().get(0);
            if (root instanceof technology.ecoa.interface_._2.DocumentRoot documentRoot) {
                return documentRoot.getServiceDefinition();
            }
        }
        throw new IOException("Unexpected root content in file " + path);
    }

    private technology.ecoa.interface_._2.ServiceDefinition parseInterfaceXml(byte[] xmlBytes, String sourceName) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xml", new interResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(interPackage.eNS_URI, interPackage.eINSTANCE);

        URI uri = URI.createURI("memory:/" + sourceName);
        Resource resource = resourceSet.createResource(uri);
        Map<Object, Object> loadOptions = new HashMap<>();
        loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        resource.load(new ByteArrayInputStream(xmlBytes), loadOptions);

        if (!resource.getContents().isEmpty()) {
            EObject root = resource.getContents().get(0);
            if (root instanceof technology.ecoa.interface_._2.DocumentRoot documentRoot) {
                return documentRoot.getServiceDefinition();
            }
        }
        throw new IOException("Unexpected root content in cached XML " + sourceName);
    }

    private technology.ecoa.implementation._2.ComponentImplementation parseImplementationXml(Path path) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xml", new impResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(impPackage.eNS_URI, impPackage.eINSTANCE);

        URI uri = URI.createFileURI(path.toAbsolutePath().toString());
        Resource resource = resourceSet.getResource(uri, false);
        if (resource == null) {
            resource = resourceSet.createResource(uri);
        }

        Map<Object, Object> loadOptions = new HashMap<>();
        loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        resource.load(loadOptions);

        if (!resource.getContents().isEmpty()) {
            EObject root = resource.getContents().get(0);
            if (root instanceof technology.ecoa.implementation._2.DocumentRoot documentRoot) {
                return documentRoot.getComponentImplementation();
            }
        }
        throw new IOException("Unexpected root content in file " + path);
    }

    private technology.ecoa.implementation._2.ComponentImplementation parseImplementationXml(byte[] xmlBytes, String sourceName) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xml", new impResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(impPackage.eNS_URI, impPackage.eINSTANCE);

        URI uri = URI.createURI("memory:/" + sourceName);
        Resource resource = resourceSet.createResource(uri);
        Map<Object, Object> loadOptions = new HashMap<>();
        loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        resource.load(new ByteArrayInputStream(xmlBytes), loadOptions);

        if (!resource.getContents().isEmpty()) {
            EObject root = resource.getContents().get(0);
            if (root instanceof technology.ecoa.implementation._2.DocumentRoot documentRoot) {
                return documentRoot.getComponentImplementation();
            }
        }
        throw new IOException("Unexpected root content in cached XML " + sourceName);
    }

    private technology.ecoa.interface_._2.ServiceDefinition loadInterfaceDefinition(String editingContextId, String fileName, Path fallbackPath) throws IOException {
        byte[] cachedXml = this.interfaceXmlCache.get(editingContextId, fileName);
        if (cachedXml != null) {
            LOGGER.info("[SVC-REPAIR] Restoring '{}' from cached raw XML", fileName);
            return this.parseInterfaceXml(cachedXml, fileName);
        }
        return this.parseInterfaceXml(fallbackPath);
    }

    private technology.ecoa.implementation._2.ComponentImplementation loadImplementationDefinition(String editingContextId, String fileName, Path fallbackPath) throws IOException {
        byte[] cachedXml = this.interfaceXmlCache.get(editingContextId, fileName);
        if (cachedXml != null) {
            LOGGER.info("[SVC-REPAIR] Restoring '{}' from cached raw XML", fileName);
            return this.parseImplementationXml(cachedXml, fileName);
        }
        if (fallbackPath == null) {
            throw new IOException("No implementation XML available for " + fileName);
        }
        return this.parseImplementationXml(fallbackPath);
    }
}
