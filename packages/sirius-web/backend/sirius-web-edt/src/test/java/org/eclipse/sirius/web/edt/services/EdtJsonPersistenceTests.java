/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.emf.services.EObjectIDManager;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.junit.jupiter.api.Test;

import edtimplementation.ComponentImplementation;
import edtimplementation.DataLinkToServiceOperation;
import edtimplementation.DataWriterInstance;
import edtimplementation.EdtimplementationFactory;
import edtimplementation.EdtimplementationPackage;
import edtimplementation.EventDefinitionInstance;
import edtimplementation.EventLinkToDefinitionOperation;
import edtimplementation.EventSenderInstance;
import edtimplementation.ModuleInstance;
import edtimplementation.PropertyValue;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtinterface.Data;
import edtinterface.EDTInterfaceFactory;
import edtinterface.EDTInterfacePackage;
import edtinterface.Event;
import edtinterface.ServiceDefinition;
import edtlogical.EdtlogicalFactory;
import edtlogical.EdtlogicalPackage;
import edtlogical.LogicalComputingNode;
import edtlogical.LogicalComputingPlatform;
import edtlogical.LogicalSystem;
import edtproject.ComponentDefinition;
import edtproject.ComponentDefinitionService;
import edtproject.EDTProjectFactory;
import edtproject.EDTProjectPackage;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step2;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;
import edttype.EDTTypePackage;
import edttype.util.EDTTypeDefaultCreator;
import org.eclipse.sirius.web.edt.importexport.EdtEcoaImportService;
import org.eclipse.sirius.web.edt.importexport.converters.NodesDeploymentExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.NodesDeploymentImportConverter;

/**
 * Tests the JSON persistence behavior of imported EDT semantic elements used by
 * the component implementation diagram.
 */
public class EdtJsonPersistenceTests {

    @Test
    public void givenComponentImplementationServicePortsAndLinksWhenJsonRoundTripThenSemanticElementsArePreserved() throws IOException {
        Steps steps = this.createSteps();
        ComponentImplementation componentImplementation = steps.getStep4().getComponentImplementations().get(0);
        ServiceOfLinkedComponentDefinition service = componentImplementation.getComponentDefinitionServices().get(0);

        assertThat(steps.getStep1().getServices()).hasSize(1);
        assertThat(steps.getStep1().getServices().get(0).getOperations()).hasSize(2);
        assertThat(service.getOperations()).hasSize(2);
        assertThat(componentImplementation.getOperationLinks()).hasSize(2);

        Steps reloadedSteps = this.roundTrip(steps);
        ComponentImplementation reloadedComponentImplementation = reloadedSteps.getStep4().getComponentImplementations().get(0);
        ServiceOfLinkedComponentDefinition reloadedService = reloadedComponentImplementation.getComponentDefinitionServices().get(0);

        assertThat(reloadedSteps.getStep1().getServices()).hasSize(1);
        assertThat(reloadedSteps.getStep1().getServices().get(0).getOperations())
                .as("service definition operations")
                .hasSize(2);
        assertThat(reloadedService.getOperations())
                .as("component implementation service operations")
                .hasSize(2);
        assertThat(reloadedComponentImplementation.getOperationLinks())
                .as("component implementation links")
                .hasSize(2);
    }

    @Test
    public void givenImportedEcoaZipWhenJsonRoundTripThenImportedSemanticCountsArePreserved() throws IOException {
        Path sampleZip = Path.of("..", "..", "..", "..", "doc", "ecoa流程", "VD_double_operations.zip");
        assertThat(Files.exists(sampleZip))
                .as("sample import archive should exist")
                .isTrue();

        EdtEcoaImportService importService = new EdtEcoaImportService(new NoOpEditingContextSearchService());
        Optional<Steps> optionalSteps = importService.parseZipToSteps(Files.readAllBytes(sampleZip));

        assertThat(optionalSteps).isPresent();
        Steps importedSteps = optionalSteps.orElseThrow();

        int serviceDefinitionOperationCount = this.totalServiceDefinitionOperations(importedSteps);
        int linkedServiceReferenceOperationCount = this.totalLinkedServiceReferenceOperations(importedSteps);
        int operationLinkCount = this.totalOperationLinks(importedSteps);

        assertThat(serviceDefinitionOperationCount).isGreaterThan(0);
        assertThat(linkedServiceReferenceOperationCount).isGreaterThan(0);
        assertThat(operationLinkCount).isGreaterThan(0);

        Steps reloadedSteps = this.roundTrip(importedSteps);

        assertThat(this.totalServiceDefinitionOperations(reloadedSteps))
                .as("service definition operations after import round-trip")
                .isEqualTo(serviceDefinitionOperationCount);
        assertThat(this.totalLinkedServiceReferenceOperations(reloadedSteps))
                .as("linked service/reference operations after import round-trip")
                .isEqualTo(linkedServiceReferenceOperationCount);
        assertThat(this.totalOperationLinks(reloadedSteps))
                .as("operation links after import round-trip")
                .isEqualTo(operationLinkCount);
    }

    @Test
    public void givenLogicalNodeIpAddressWhenJsonRoundTripThenIpAddressIsPreserved() throws IOException {
        Steps steps = this.createSteps();
        LogicalComputingNode logicalComputingNode = this.addLogicalNode(steps, "main", "192.168.248.129");

        Steps reloadedSteps = this.roundTrip(steps);
        LogicalComputingNode reloadedNode = reloadedSteps.getStep5()
                .getLogicalSystem()
                .getLogicalComputingPlatforms()
                .get(0)
                .getLogicalComputingNodes()
                .get(0);

        assertThat(logicalComputingNode.getIpAddress()).isEqualTo("192.168.248.129");
        assertThat(reloadedNode.getIpAddress()).isEqualTo("192.168.248.129");
    }

    @Test
    public void givenNodesDeploymentXmlWhenImportedThenIpAddressesAreAppliedToMatchingNodes() throws IOException {
        LogicalSystem logicalSystem = EdtlogicalFactory.eINSTANCE.createLogicalSystem();
        logicalSystem.setId("ls1");
        logicalSystem.setFileNamePrefix("ls1");

        LogicalComputingPlatform platform = EdtlogicalFactory.eINSTANCE.createLogicalComputingPlatform();
        platform.setId("platform1");
        logicalSystem.getLogicalComputingPlatforms().add(platform);

        LogicalComputingNode mainNode = EdtlogicalFactory.eINSTANCE.createLogicalComputingNode();
        mainNode.setId("main");
        platform.getLogicalComputingNodes().add(mainNode);

        LogicalComputingNode machine1Node = EdtlogicalFactory.eINSTANCE.createLogicalComputingNode();
        machine1Node.setId("machine1");
        platform.getLogicalComputingNodes().add(machine1Node);

        LogicalSystem exportedLogicalSystem = EdtlogicalFactory.eINSTANCE.createLogicalSystem();
        exportedLogicalSystem.setId("ls1");
        exportedLogicalSystem.setFileNamePrefix("ls1");
        LogicalComputingPlatform exportedPlatform = EdtlogicalFactory.eINSTANCE.createLogicalComputingPlatform();
        exportedPlatform.setId("platform1");
        exportedLogicalSystem.getLogicalComputingPlatforms().add(exportedPlatform);
        exportedPlatform.getLogicalComputingNodes().add(this.newLogicalNode("main", "192.168.248.129"));
        exportedPlatform.getLogicalComputingNodes().add(this.newLogicalNode("machine1", "192.168.248.130"));

        byte[] xmlBytes = NodesDeploymentExportConverter.toXmlBytes(exportedLogicalSystem).orElseThrow();
        String xmlContent = new String(xmlBytes, StandardCharsets.UTF_8);
        assertThat(xmlContent).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xmlContent).doesNotContain("standalone=");
        Path tempFile = Files.createTempFile("nodes_deployment", ".xml");
        Files.write(tempFile, xmlBytes);

        try {
            NodesDeploymentImportConverter.apply(tempFile, logicalSystem);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        assertThat(mainNode.getIpAddress()).isEqualTo("192.168.248.129");
        assertThat(machine1Node.getIpAddress()).isEqualTo("192.168.248.130");
    }

    @Test
    public void givenModuleInstanceWithPropertyValueWhenJsonRoundTripThenValueIsPreserved() throws IOException {
        Steps steps = this.createSteps();
        ModuleInstance moduleInstance = (ModuleInstance) steps.getStep4()
                .getComponentImplementations().get(0).getInstances().get(0);

        PropertyValue pv = EdtimplementationFactory.eINSTANCE.createPropertyValue();
        pv.setName("reader_id");
        pv.setValue("42");
        moduleInstance.getPropertyValues().add(pv);

        assertThat(moduleInstance.getPropertyValues()).hasSize(1);
        assertThat(moduleInstance.getPropertyValues().get(0).getValue()).isEqualTo("42");

        Steps reloaded = this.roundTrip(steps);
        ModuleInstance reloadedMI = (ModuleInstance) reloaded.getStep4()
                .getComponentImplementations().get(0).getInstances().get(0);

        assertThat(reloadedMI.getPropertyValues())
                .as("PropertyValue list should survive JSON round-trip")
                .hasSize(1);
        assertThat(reloadedMI.getPropertyValues().get(0).getValue())
                .as("PropertyValue.value should survive JSON round-trip (was lost before XMLTypePackage registration)")
                .isEqualTo("42");
    }

    private Steps createSteps() {
        var projectFactory = EDTProjectFactory.eINSTANCE;
        var interfaceFactory = EDTInterfaceFactory.eINSTANCE;
        var implementationFactory = EdtimplementationFactory.eINSTANCE;

        Steps steps = projectFactory.createSteps();
        Step0 step0 = projectFactory.createStep0();
        Step1 step1 = projectFactory.createStep1();
        Step2 step2 = projectFactory.createStep2();
        Step3 step3 = projectFactory.createStep3();
        Step4 step4 = projectFactory.createStep4();
        Step5 step5 = projectFactory.createStep5();

        step0.setFolderName("0-Types");
        step1.setFolderName("1-Services");
        step2.setFolderName("2-ComponentDefinitions");
        step3.setFolderName("3-InitialAssembly");
        step4.setFolderName("4-ComponentImplementations");
        step5.setFolderName("5-Integration");

        step0.getBasicTypes().addAll(EDTTypeDefaultCreator.createBasicTypes());
        step0.getEcoaPredefinedTypes().addAll(EDTTypeDefaultCreator.createPredefinedTypes(step0));

        ServiceDefinition serviceDefinition = interfaceFactory.createServiceDefinition();
        serviceDefinition.setName("Telemetry");

        Data data = interfaceFactory.createData();
        data.setName("telemetryData");
        data.setType(step0.findBasicType("uint32"));
        serviceDefinition.getOperations().add(data);

        Event event = interfaceFactory.createEvent();
        event.setName("telemetryEvent");
        serviceDefinition.getOperations().add(event);
        step1.getServices().add(serviceDefinition);

        ComponentDefinition componentDefinition = projectFactory.createComponentDefinition();
        componentDefinition.setName("TelemetryComponent");

        ComponentDefinitionService componentDefinitionService = projectFactory.createComponentDefinitionService();
        componentDefinitionService.setName("telemetry");
        componentDefinitionService.setSyntax(serviceDefinition);
        componentDefinition.getServices().add(componentDefinitionService);
        step2.getComponentDefinitions().add(componentDefinition);

        ComponentImplementation componentImplementation = implementationFactory.createComponentImplementation();
        componentImplementation.setName("TelemetryImpl");
        componentImplementation.setComponentDefinition(componentDefinition);

        ModuleInstance moduleInstance = implementationFactory.createModuleInstance();
        moduleInstance.setName("moduleA");

        DataWriterInstance dataWriterInstance = implementationFactory.createDataWriterInstance();
        dataWriterInstance.setName("writer");
        moduleInstance.getOperations().add(dataWriterInstance);

        EventSenderInstance eventSenderInstance = implementationFactory.createEventSenderInstance();
        eventSenderInstance.setName("sender");
        moduleInstance.getOperations().add(eventSenderInstance);
        componentImplementation.getInstances().add(moduleInstance);

        ServiceOfLinkedComponentDefinition service = componentImplementation.getComponentDefinitionServices().get(0);
        DataLinkToServiceOperation dataLink = implementationFactory.createDataLinkToServiceOperation();
        dataLink.setId(1);
        dataLink.setWriter(dataWriterInstance);
        dataLink.setReader(service.getOperations().stream()
                .filter(edtimplementation.VersionedDataServiceInstance.class::isInstance)
                .map(edtimplementation.VersionedDataServiceInstance.class::cast)
                .findFirst()
                .orElseThrow());
        componentImplementation.getOperationLinks().add(dataLink);

        EventLinkToDefinitionOperation eventLink = implementationFactory.createEventLinkToDefinitionOperation();
        eventLink.setId(2);
        eventLink.setSender(eventSenderInstance);
        eventLink.setReceiver(service.getOperations().stream()
                .filter(EventDefinitionInstance.class::isInstance)
                .map(EventDefinitionInstance.class::cast)
                .findFirst()
                .orElseThrow());
        componentImplementation.getOperationLinks().add(eventLink);
        step4.getComponentImplementations().add(componentImplementation);

        steps.getStep().add(step0);
        steps.getStep().add(step1);
        steps.getStep().add(step2);
        steps.getStep().add(step3);
        steps.getStep().add(step4);
        steps.getStep().add(step5);
        steps.setOutputDirectory(projectFactory.createOutputDirectory());
        return steps;
    }

    private Steps roundTrip(Steps steps) throws IOException {
        JSONResourceFactory resourceFactory = new JSONResourceFactory();

        ResourceSetImpl writeResourceSet = this.createResourceSet();
        JsonResource writeResource = resourceFactory.createResourceFromPath(UUID.randomUUID().toString());
        writeResourceSet.getResources().add(writeResource);
        writeResource.getContents().add(steps);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Map<Object, Object> saveOptions = new HashMap<>();
        saveOptions.put(JsonResource.OPTION_ID_MANAGER, new EObjectIDManager());
        saveOptions.put(JsonResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
        writeResource.save(outputStream, saveOptions);

        ResourceSetImpl readResourceSet = this.createResourceSet();
        JsonResource readResource = resourceFactory.createResourceFromPath(UUID.randomUUID().toString());
        readResourceSet.getResources().add(readResource);
        readResource.load(new ByteArrayInputStream(outputStream.toByteArray()), Map.of());

        return (Steps) readResource.getContents().get(0);
    }

    private ResourceSetImpl createResourceSet() {
        ResourceSetImpl resourceSet = new ResourceSetImpl();
        resourceSet.getPackageRegistry().put(EDTProjectPackage.eNS_URI, EDTProjectPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(EDTInterfacePackage.eNS_URI, EDTInterfacePackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(EdtimplementationPackage.eNS_URI, EdtimplementationPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(EdtlogicalPackage.eNS_URI, EdtlogicalPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(EDTTypePackage.eNS_URI, EDTTypePackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(XMLTypePackage.eNS_URI, XMLTypePackage.eINSTANCE);
        return resourceSet;
    }

    private LogicalComputingNode addLogicalNode(Steps steps, String nodeId, String ipAddress) {
        LogicalSystem logicalSystem = EdtlogicalFactory.eINSTANCE.createLogicalSystem();
        logicalSystem.setId("ls1");
        logicalSystem.setFileNamePrefix("ls1");

        LogicalComputingPlatform platform = EdtlogicalFactory.eINSTANCE.createLogicalComputingPlatform();
        platform.setId("platform1");
        logicalSystem.getLogicalComputingPlatforms().add(platform);

        LogicalComputingNode node = this.newLogicalNode(nodeId, ipAddress);
        platform.getLogicalComputingNodes().add(node);
        steps.getStep5().setLogicalSystem(logicalSystem);
        return node;
    }

    private LogicalComputingNode newLogicalNode(String nodeId, String ipAddress) {
        LogicalComputingNode node = EdtlogicalFactory.eINSTANCE.createLogicalComputingNode();
        node.setId(nodeId);
        node.setIpAddress(ipAddress);
        return node;
    }

    private int totalServiceDefinitionOperations(Steps steps) {
        return steps.getStep1().getServices().stream()
                .mapToInt(serviceDefinition -> serviceDefinition.getOperations().size())
                .sum();
    }

    private int totalLinkedServiceReferenceOperations(Steps steps) {
        return steps.getStep4().getComponentImplementations().stream()
                .mapToInt(componentImplementation -> componentImplementation.getComponentDefinitionServices().stream()
                        .mapToInt(service -> service.getOperations().size())
                        .sum()
                        + componentImplementation.getComponentDefinitionReferences().stream()
                                .mapToInt(reference -> reference.getOperations().size())
                                .sum())
                .sum();
    }

    private int totalOperationLinks(Steps steps) {
        return steps.getStep4().getComponentImplementations().stream()
                .mapToInt(componentImplementation -> componentImplementation.getOperationLinks().size())
                .sum();
    }

    private static final class NoOpEditingContextSearchService implements IEditingContextSearchService {

        @Override
        public boolean existsById(String editingContextId) {
            return false;
        }

        @Override
        public Optional<IEditingContext> findById(String editingContextId) {
            return Optional.empty();
        }
    }
}
