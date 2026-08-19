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
package org.eclipse.sirius.web.edt.importexport;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.edt.importexport.converters.AssemblyExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentDefinitionExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentImplementationExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.DeploymentExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.LogicalSystemExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.NodesDeploymentExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ServiceDefinitionExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.DDSBindingExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.TCPBindingExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.UDPBindingExportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.TypesExportConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edtimplementation.ComponentImplementation;
import edtinterface.ServiceDefinition;
import edtlogical.LogicalComputingPlatformLink;
import edtdds.DDSBinding;
import edttcp.TCPBinding;
import edtudp.UDPBinding;
import edtproject.ComponentDefinition;
import edtproject.Composite;
import edtproject.Step;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step2;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;
import edttype.Library;
import technology.ecoa.project._2.EcoaProject;
import technology.ecoa.project._2.Files;
import technology.ecoa.project._2.projFactory;

/**
 * Service for exporting EDT projects to ECOA XML format.
 * Based on the original XMLExporter from edt-tmp.
 */
@Service
public class EdtEcoaExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtEcoaExportService.class);
    private static final String STEPS_FOLDER = "Steps";

    private final IEditingContextSearchService editingContextSearchService;

    private final EcoaInterfaceXmlCache interfaceXmlCache;

    public EdtEcoaExportService(IEditingContextSearchService editingContextSearchService,
            EcoaInterfaceXmlCache interfaceXmlCache) {
        this.editingContextSearchService = Objects.requireNonNull(editingContextSearchService);
        this.interfaceXmlCache = Objects.requireNonNull(interfaceXmlCache);
    }

    /**
     * Export an ECOA project to a ZIP file.
     *
     * @param editingContextId the editing context ID
     * @param projectName      the project name
     * @return the ZIP file bytes, or empty if export failed
     */
    public Optional<byte[]> exportToZip(String editingContextId, String projectName) {
        return this.editingContextSearchService.findById(editingContextId)
                .filter(IEMFEditingContext.class::isInstance)
                .map(IEMFEditingContext.class::cast)
                .flatMap(editingContext -> {
                    ResourceSet resourceSet = editingContext.getDomain().getResourceSet();
                    return findSteps(resourceSet).flatMap(steps -> exportStepsToZip(steps, projectName, editingContextId));
                });
    }

    private Optional<Steps> findSteps(ResourceSet resourceSet) {
        for (Resource resource : resourceSet.getResources()) {
            for (EObject root : resource.getContents()) {
                if (root instanceof Steps steps) {
                    return Optional.of(steps);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<byte[]> exportStepsToZip(Steps steps, String projectName, String editingContextId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            String stepsPath = STEPS_FOLDER + "/";
            // Sanitize project name for use in file names: replace spaces/special chars with underscores
            // This prevents command-line argument parsing errors in ecoa tools (ecoa-ldp, ecoa-exvt, etc.)
            String sanitizedProjectName = projectName.replaceAll("[\\s]+", "_");

            // Export project.xml
            exportProjectXml(zos, steps, projectName, sanitizedProjectName, stepsPath);

            // Export each step
            EList<Step> stepList = steps.getStep();
            for (Step step : stepList) {
                if (step instanceof Step0 step0) {
                    exportStep0(zos, step0, stepsPath);
                } else if (step instanceof Step1 step1) {
                    exportStep1(zos, step1, stepsPath, editingContextId);
                } else if (step instanceof Step2 step2) {
                    exportStep2(zos, step2, stepsPath);
                } else if (step instanceof Step3 step3) {
                    exportStep3(zos, step3, stepsPath);
                } else if (step instanceof Step4 step4) {
                    exportStep4(zos, step4, stepsPath, editingContextId);
                } else if (step instanceof Step5 step5) {
                    exportStep5(zos, step5, stepsPath);
                }
            }

            zos.finish();
            return Optional.of(baos.toByteArray());

        } catch (IOException e) {
            LOGGER.error("Error exporting ECOA project", e);
            return Optional.empty();
        }
    }

    /**
     * Get the list of components from InitialAssembly (Step 3) without exporting.
     * @param editingContextId the editing context ID
     * @return a list of component names
     */
    public Optional<java.util.List<String>> getInitialAssemblyComponents(String editingContextId) {
        return this.editingContextSearchService.findById(editingContextId)
                .filter(IEMFEditingContext.class::isInstance)
                .map(IEMFEditingContext.class::cast)
                .map(editingContext -> {
                    ResourceSet resourceSet = editingContext.getDomain().getResourceSet();
                    Optional<Steps> stepsOpt = findSteps(resourceSet);
                    if (stepsOpt.isPresent()) {
                        for (Step step : stepsOpt.get().getStep()) {
                            if (step instanceof Step3 step3 && step3.getInitialAssembly() != null) {
                                return step3.getInitialAssembly().getComponents().stream()
                                        .map(edtproject.Component::getName)
                                        .filter(Objects::nonNull)
                                        .toList();
                            }
                        }
                    }
                    return java.util.List.<String>of();
                });
    }

    /**
     * Export project.xml file containing references to all other files.
     */
    private void exportProjectXml(ZipOutputStream zos, Steps steps, String projectName, String sanitizedProjectName, String stepsPath) throws IOException {
        EcoaProject ecoaProject = createEcoaProject(steps, projectName);
        byte[] xmlBytes = serializeToXml(ecoaProject, "xml");

        // Use sanitized name for the .project.xml file to avoid spaces in command-line arguments
        String projectXmlPath = stepsPath + sanitizedProjectName + ".project.xml";
        zos.putNextEntry(new ZipEntry(projectXmlPath));
        zos.write(xmlBytes);
        zos.closeEntry();

        LOGGER.info("Exported: {}", projectXmlPath);
    }

    /**
     * Create ECOA Project from EDT Steps (mirrors EDTProjectExportConverter).
     */
    private EcoaProject createEcoaProject(Steps steps, String projectName) {
        projFactory factory = projFactory.eINSTANCE;
        EcoaProject project = factory.createEcoaProject();
        project.setName(projectName);

        // OutputDirectory
        if (steps.getOutputDirectory() != null && steps.getOutputDirectory().getName() != null && !steps.getOutputDirectory().getName().isBlank()) {
            project.getOutputDirectory().add(steps.getOutputDirectory().getName());
        } else {
            project.getOutputDirectory().add("6-output");
        }

        // Helper to find step
        Step0 step0 = null;
        Step1 step1 = null;
        Step2 step2 = null;
        Step3 step3 = null;
        Step4 step4 = null;
        Step5 step5 = null;

        for (edtproject.Step step : steps.getStep()) {
            if (step instanceof Step0 s) step0 = s;
            else if (step instanceof Step1 s) step1 = s;
            else if (step instanceof Step2 s) step2 = s;
            else if (step instanceof Step3 s) step3 = s;
            else if (step instanceof Step4 s) step4 = s;
            else if (step instanceof Step5 s) step5 = s;
        }

        // Step 0 - Types
        if (step0 != null && !step0.getTypes().isEmpty()) {
            Files typesFiles = factory.createFiles();
            for (Library library : step0.getTypes()) {
                String filePath = step0.getFolderName() + "/" + library.getName().replaceAll("\\.", "__") + ".types.xml";
                typesFiles.getFile().add(filePath);
            }
            project.getTypes().add(typesFiles);
        }

        // Step 1 - Services
        if (step1 != null && !step1.getServices().isEmpty()) {
            Files serviceFiles = factory.createFiles();
            for (ServiceDefinition sd : step1.getServices()) {
                String filePath = step1.getFolderName() + "/" + sd.getName() + ".interface.xml";
                serviceFiles.getFile().add(filePath);
            }
            project.getServiceDefinitions().add(serviceFiles);
        }

        // Step 2 - Component Definitions
        if (step2 != null && !step2.getComponentDefinitions().isEmpty()) {
            Files cdFiles = factory.createFiles();
            for (var cd : step2.getComponentDefinitions()) {
                String filePath = step2.getFolderName() + "/" + cd.getName() + "/" + cd.getName() + ".componentType";
                cdFiles.getFile().add(filePath);
            }
            project.getComponentDefinitions().add(cdFiles);
        }

        // Step 3 - Initial Assembly
        if (step3 != null && step3.getInitialAssembly() != null && !step3.getInitialAssembly().getComponents().isEmpty()) {
            String filePath = step3.getFolderName() + "/" + step3.getInitialAssembly().getName() + ".composite";
            project.getInitialAssembly().add(filePath);
        }

        // Step 4 - Component Implementations
        if (step4 != null && !step4.getComponentImplementations().isEmpty()) {
            Files ciFiles = factory.createFiles();
            for (var ci : step4.getComponentImplementations()) {
                String filePath = step4.getFolderName() + "/" + ci.getName() + "/" + ci.getName() + ".impl.xml";
                ciFiles.getFile().add(filePath);
            }
            project.getComponentImplementations().add(ciFiles);
        }

        // Step 5 - Integration
        if (step5 != null) {
            if (step5.getFinalAssembly() != null && step5.getFinalAssembly().getFinalAssembly() != null) {
                String filePath = step5.getFolderName() + "/" + step5.getFinalAssembly().getName() + ".impl.composite";
                project.getImplementationAssembly().add(filePath);
            }
            if (step5.getLogicalSystem() != null) {
                String filePath = step5.getFolderName() + "/" + step5.getLogicalSystem().getFileNamePrefix() + ".logical-system.xml";
                project.getLogicalSystem().add(filePath);
            }
            if (step5.getDeployment() != null) {
                String filePath = step5.getFolderName() + "/" + step5.getDeployment().getName() + ".deployment.xml";
                project.getDeploymentSchema().add(filePath);
            }
        }

        return project;
    }

    /**
     * Export Step 0 (Types/Libraries).
     */
    private void exportStep0(ZipOutputStream zos, Step0 step0, String stepsPath) throws IOException {
        String folderPath = stepsPath + step0.getFolderName() + "/";

        for (Library library : step0.getTypes()) {
            String fileName = library.getName().replaceAll("\\.", "__") + ".types.xml";
            technology.ecoa.types._2.DocumentRoot docRoot = TypesExportConverter.recreateLibrary(library);

            byte[] xmlBytes = serializeToXml(docRoot, "xml");
            zos.putNextEntry(new ZipEntry(folderPath + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}", folderPath, fileName);
        }
    }

    /**
     * Export Step 1 (Service Definitions).
     */
    private void exportStep1(ZipOutputStream zos, Step1 step1, String stepsPath, String editingContextId) throws IOException {
        String folderPath = stepsPath + step1.getFolderName() + "/";

        for (ServiceDefinition sd : step1.getServices()) {
            String fileName = sd.getName() + ".interface.xml";
            byte[] xmlBytes;

            if (!sd.getOperations().isEmpty()) {
                // Normal path: convert from EDT model to ECOA XML
                technology.ecoa.interface_._2.DocumentRoot docRoot = ServiceDefinitionExportConverter.recreateServiceDefinition(sd);
                xmlBytes = serializeToXml(docRoot, "xml");
            } else {
                // Fallback: Sirius Web JSON serializer cannot round-trip IS_INTERFACE=true
                // OperationType sub-types (Event/Data/RequestResponse). Use cached raw XML bytes.
                LOGGER.warn("[SVC-EXPORT] '{}' operations empty (Sirius Web serialization loss). Trying cached raw XML...", sd.getName());
                xmlBytes = this.interfaceXmlCache.get(editingContextId, fileName);
                if (xmlBytes != null) {
                    LOGGER.info("[SVC-EXPORT] '{}' using cached raw XML ({} bytes)", fileName, xmlBytes.length);
                } else {
                    LOGGER.warn("[SVC-EXPORT] '{}' no cached XML found, exporting empty interface", fileName);
                    technology.ecoa.interface_._2.DocumentRoot docRoot = ServiceDefinitionExportConverter.recreateServiceDefinition(sd);
                    xmlBytes = serializeToXml(docRoot, "xml");
                }
            }

            zos.putNextEntry(new ZipEntry(folderPath + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}", folderPath, fileName);
        }
    }

    /**
     * Export Step 2 (Component Definitions).
     */
    private void exportStep2(ZipOutputStream zos, Step2 step2, String stepsPath) throws IOException {
        String folderPath = stepsPath + step2.getFolderName() + "/";

        for (ComponentDefinition cd : step2.getComponentDefinitions()) {
            String subFolder = cd.getName() + "/";
            String fileName = cd.getName() + ".componentType";
            org.open.oasis.docs.ns.opencsa.sca.sca.DocumentRoot docRoot = 
                    ComponentDefinitionExportConverter.recreateComponentType(cd);

            byte[] xmlBytes = serializeToXml(docRoot, "xml");
            zos.putNextEntry(new ZipEntry(folderPath + subFolder + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}{}", folderPath, subFolder, fileName);
        }
    }

    /**
     * Export Step 3 (Initial Assembly).
     */
    private void exportStep3(ZipOutputStream zos, Step3 step3, String stepsPath) throws IOException {
        Composite composite = step3.getInitialAssembly();
        if (composite == null || composite.getComponents().isEmpty()) {
            LOGGER.debug("Step3: No initial assembly to export");
            return;
        }

        String folderPath = stepsPath + step3.getFolderName() + "/";
        String fileName = composite.getName() + ".composite";

        org.open.oasis.docs.ns.opencsa.sca.sca.DocumentRoot docRoot = 
                AssemblyExportConverter.recreateComposite(composite, false, composite.getName());

        byte[] xmlBytes = serializeToXml(docRoot, "xml");
        zos.putNextEntry(new ZipEntry(folderPath + fileName));
        zos.write(xmlBytes);
        zos.closeEntry();

        LOGGER.info("Exported: {}{}", folderPath, fileName);
    }

    /**
     * Export Step 4 (Component Implementations).
     */
    private void exportStep4(ZipOutputStream zos, Step4 step4, String stepsPath, String editingContextId) throws IOException {
        String folderPath = stepsPath + step4.getFolderName() + "/";

        for (ComponentImplementation ci : step4.getComponentImplementations()) {
            String subFolder = ci.getName() + "/";
            String fileName = ci.getName() + ".impl.xml";
            byte[] xmlBytes;

            // Check whether any module instance PropertyValue.value was lost during Sirius Web
            // JSON round-trip. If so, fall back to cached raw XML.
            boolean hasNullPropertyValue = ci.getInstances().stream()
                    .filter(edtimplementation.ModuleInstance.class::isInstance)
                    .map(edtimplementation.ModuleInstance.class::cast)
                    .flatMap(mi -> mi.getPropertyValues().stream())
                    .anyMatch(pv -> pv.getValue() == null || pv.getValue().isBlank());

            if (hasNullPropertyValue) {
                LOGGER.warn("[IMPL-EXPORT] '{}' has null PropertyValue(s) (Sirius Web serialization loss). Trying cached raw XML...", fileName);
                xmlBytes = this.interfaceXmlCache.get(editingContextId, fileName);
                if (xmlBytes != null) {
                    LOGGER.info("[IMPL-EXPORT] '{}' using cached raw XML ({} bytes)", fileName, xmlBytes.length);
                } else {
                    LOGGER.warn("[IMPL-EXPORT] '{}' no cached XML found, exporting with missing values", fileName);
                    technology.ecoa.implementation._2.DocumentRoot docRoot =
                            ComponentImplementationExportConverter.recreateComponentImplementation(ci);
                    xmlBytes = serializeToXml(docRoot, "xml");
                }
            } else {
                technology.ecoa.implementation._2.DocumentRoot docRoot =
                        ComponentImplementationExportConverter.recreateComponentImplementation(ci);
                xmlBytes = serializeToXml(docRoot, "xml");
            }

            zos.putNextEntry(new ZipEntry(folderPath + subFolder + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}{}", folderPath, subFolder, fileName);
        }
    }

    /**
     * Export Step 5 (Integration: FinalAssembly, LogicalSystem, Deployment).
     */
    private void exportStep5(ZipOutputStream zos, Step5 step5, String stepsPath) throws IOException {
        String folderPath = stepsPath + step5.getFolderName() + "/";

        // Export Final Assembly
        if (step5.getFinalAssembly() != null && step5.getFinalAssembly().getFinalAssembly() != null) {
            Composite composite = step5.getFinalAssembly().getFinalAssembly();
            String fileName = step5.getFinalAssembly().getName() + ".impl.composite";

            org.open.oasis.docs.ns.opencsa.sca.sca.DocumentRoot docRoot = 
                    AssemblyExportConverter.recreateComposite(composite, true, step5.getFinalAssembly().getName());

            byte[] xmlBytes = serializeToXml(docRoot, "xml");
            zos.putNextEntry(new ZipEntry(folderPath + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}", folderPath, fileName);
        }

        // Export Logical System
        if (step5.getLogicalSystem() != null) {
            String fileName = step5.getLogicalSystem().getFileNamePrefix() + ".logical-system.xml";
            technology.ecoa.logicalsystem._2.DocumentRoot docRoot =
                    LogicalSystemExportConverter.recreateLogicalSystem(step5.getLogicalSystem());

            byte[] xmlBytes = serializeToXml(docRoot, "xml");
            // Inject dds/ddsDomainId attributes for links that use DDS middleware
            xmlBytes = injectDdsAttributes(xmlBytes, step5.getLogicalSystem());
            zos.putNextEntry(new ZipEntry(folderPath + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}", folderPath, fileName);
        }

        // Export Deployment
        if (step5.getDeployment() != null) {
            String fileName = step5.getDeployment().getName() + ".deployment.xml";
            technology.ecoa.deployment._2.DocumentRoot docRoot = 
                    DeploymentExportConverter.recreateDeployment(step5.getDeployment());

            byte[] xmlBytes = serializeToXml(docRoot, "xml");
            zos.putNextEntry(new ZipEntry(folderPath + fileName));
            zos.write(xmlBytes);
            zos.closeEntry();

            LOGGER.info("Exported: {}{}", folderPath, fileName);
        }

        // Export custom nodes deployment file (only when at least one node has a non-blank IP)
        if (step5.getLogicalSystem() != null) {
            var nodesDeploymentBytes = NodesDeploymentExportConverter.toXmlBytes(step5.getLogicalSystem());
            if (nodesDeploymentBytes.isPresent()) {
                String fileName = "nodes_deployment.xml";
                zos.putNextEntry(new ZipEntry(folderPath + fileName));
                zos.write(nodesDeploymentBytes.get());
                zos.closeEntry();
                LOGGER.info("Exported: {}{}", folderPath, fileName);
            }
        }

        // Export UDP Bindings (*.udp-binding.xml)
        for (UDPBinding udpBinding : step5.getUDPBindings()) {
            if (udpBinding.getName() != null && !udpBinding.getName().isBlank()) {
                String fileName = udpBinding.getName() + ".udp-binding.xml";
                byte[] xmlBytes = UDPBindingExportConverter.toXmlBytes(udpBinding);
                zos.putNextEntry(new ZipEntry(folderPath + fileName));
                zos.write(xmlBytes);
                zos.closeEntry();
                LOGGER.info("Exported: {}{}", folderPath, fileName);
            }
        }

        // Export DDS Bindings (*.dds-binding.xml)
        for (DDSBinding ddsBinding : step5.getDDSBindings()) {
            if (ddsBinding.getName() != null && !ddsBinding.getName().isBlank()) {
                String fileName = ddsBinding.getName() + ".dds-binding.xml";
                byte[] xmlBytes = DDSBindingExportConverter.toXmlBytes(ddsBinding);
                zos.putNextEntry(new ZipEntry(folderPath + fileName));
                zos.write(xmlBytes);
                zos.closeEntry();
                LOGGER.info("Exported: {}{}", folderPath, fileName);
            }
        }

        // Export TCP Bindings (*.tcp-params.xml)
        for (TCPBinding tcpBinding : step5.getTCPBindings()) {
            if (tcpBinding.getName() != null && !tcpBinding.getName().isBlank()) {
                String fileName = tcpBinding.getName() + ".tcp-params.xml";
                byte[] xmlBytes = TCPBindingExportConverter.toXmlBytes(tcpBinding);
                zos.putNextEntry(new ZipEntry(folderPath + fileName));
                zos.write(xmlBytes);
                zos.closeEntry();
                LOGGER.info("Exported: {}{}", folderPath, fileName);
            }
        }
    }

    /**
     * Post-processes the serialized logical-system XML to inject {@code dds="true"} and
     * {@code ddsDomainId="N"} attributes on {@code <transportBinding>} elements that
     * belong to links with DDS middleware enabled.
     * <p>
     * These non-standard attributes are ignored by standard ECOA validators but are
     * read by the Python ecoa-tools pipeline to activate DDS compilation flags.
     */
    private byte[] injectDdsAttributes(byte[] xmlBytes, edtlogical.LogicalSystem edtLogicalSystem) {
        boolean hasDdsLink = edtLogicalSystem.getLogicalComputingPlatformLinks().stream()
                .anyMatch(LogicalComputingPlatformLink::isUseDDS);
        if (!hasDdsLink) {
            return xmlBytes;
        }
        // Build a map from link id → (useDDS, ddsDomainId)
        Map<String, Integer> ddsLinkIds = new HashMap<>();
        for (LogicalComputingPlatformLink link : edtLogicalSystem.getLogicalComputingPlatformLinks()) {
            if (link.isUseDDS() && link.getId() != null) {
                ddsLinkIds.put(link.getId(), link.getDdsDomainId());
            }
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));

            NodeList linkNodes = doc.getElementsByTagNameNS("*", "link");
            for (int i = 0; i < linkNodes.getLength(); i++) {
                org.w3c.dom.Element linkEl = (org.w3c.dom.Element) linkNodes.item(i);
                String linkId = linkEl.getAttribute("id");
                if (ddsLinkIds.containsKey(linkId)) {
                    NodeList tbNodes = linkEl.getElementsByTagNameNS("*", "transportBinding");
                    if (tbNodes.getLength() > 0) {
                        org.w3c.dom.Element tb = (org.w3c.dom.Element) tbNodes.item(0);
                        tb.setAttribute("dds", "true");
                        tb.setAttribute("ddsDomainId", String.valueOf(ddsLinkIds.get(linkId)));
                    }
                }
            }

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("Failed to inject DDS attributes into logical-system.xml; DDS links may not compile correctly", e);
            return xmlBytes;
        }
    }

    /**
     * Serialize an EObject to XML bytes using a proper ResourceSet and ExtendedMetaData.
     * This ensures that FeatureMap-based elements (like operations and parameters) are correctly exported.
     */
    private byte[] serializeToXml(EObject object, String extension) throws IOException {
        ResourceSet resourceSet = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        
        // Register all potential ECOA and SCA packages to ensure ExtendedMetaData works correctly during save
        resourceSet.getPackageRegistry().put(technology.ecoa.project._2.projPackage.eNS_URI, technology.ecoa.project._2.projPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(technology.ecoa.types._2.typPackage.eNS_URI, technology.ecoa.types._2.typPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(technology.ecoa.interface_._2.interPackage.eNS_URI, technology.ecoa.interface_._2.interPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(technology.ecoa.implementation._2.impPackage.eNS_URI, technology.ecoa.implementation._2.impPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(technology.ecoa.logicalsystem._2.logPackage.eNS_URI, technology.ecoa.logicalsystem._2.logPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(technology.ecoa.deployment._2.depPackage.eNS_URI, technology.ecoa.deployment._2.depPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eNS_URI, org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eINSTANCE);

        // Register the basic XML Resource Factory for the given extension
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(extension, new org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl());

        URI uri = URI.createFileURI(System.getProperty("java.io.tmpdir") + "/ecoa-export-temp." + extension);
        Resource resource = resourceSet.createResource(uri);
        resource.getContents().add(object);

        Map<String, Object> saveOptions = new HashMap<>();
        saveOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        saveOptions.put(XMLResource.OPTION_ENCODING, "UTF-8");
        saveOptions.put(XMLResource.OPTION_SAVE_TYPE_INFORMATION, Boolean.FALSE);
        // Ensure we don't use XMI tags in our ECOA XML files
        saveOptions.put(org.eclipse.emf.ecore.xmi.XMIResource.OPTION_SUPPRESS_XMI, Boolean.TRUE);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.save(outputStream, saveOptions);

        return outputStream.toByteArray();
    }
}
