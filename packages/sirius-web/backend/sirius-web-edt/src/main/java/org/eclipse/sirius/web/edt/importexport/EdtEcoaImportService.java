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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.edt.importexport.converters.AssemblyImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentDefinitionImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ComponentImplementationImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.DeploymentImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.LogicalSystemImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.NodesDeploymentImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.ServiceDefinitionImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.DDSBindingImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.TCPBindingImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.UDPBindingImportConverter;
import org.eclipse.sirius.web.edt.importexport.converters.TypesImportConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edtdds.DDSBinding;
import edtlogical.LogicalComputingPlatformLink;
import edttcp.TCPBinding;
import edtudp.UDPBinding;
import edtproject.EDTProjectFactory;
import edtproject.FinalAssembly;
import edtproject.Step;
import edtproject.Step0;
import edtproject.Step1;
import edtproject.Step2;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;
import edttype.util.EDTTypeDefaultCreator;
import technology.ecoa.deployment._2.depPackage;
import technology.ecoa.deployment._2.util.depResourceFactoryImpl;
import technology.ecoa.implementation._2.impPackage;
import technology.ecoa.implementation._2.util.impResourceFactoryImpl;
import technology.ecoa.interface_._2.interPackage;
import technology.ecoa.interface_._2.util.interResourceFactoryImpl;
import technology.ecoa.logicalsystem._2.logPackage;
import technology.ecoa.logicalsystem._2.util.logResourceFactoryImpl;
import technology.ecoa.project._2.EcoaProject;
import technology.ecoa.project._2.projPackage;
import technology.ecoa.project._2.util.projResourceFactoryImpl;
import technology.ecoa.types._2.typPackage;
import technology.ecoa.types._2.util.typResourceFactoryImpl;

/**
 * Service for importing ECOA XML projects into EDT format.
 */
@Service
public class EdtEcoaImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtEcoaImportService.class);
    private static final EDTProjectFactory EDTFACTORY = EDTProjectFactory.eINSTANCE;

    private static final String ZERO_TYPES = "0-Types";
    private static final String ONE_SERVICES = "1-Services";
    private static final String TWO_COMPONENT_DEFINITIONS = "2-ComponentDefinitions";
    private static final String THREE_INITIAL_ASSEMBLY = "3-InitialAssembly";
    private static final String FOUR_COMPONENT_IMPLEMENTATIONS = "4-ComponentImplementations";
    private static final String FIVE_INTEGRATION = "5-Integration";

    private final IEditingContextSearchService editingContextSearchService;

    public EdtEcoaImportService(IEditingContextSearchService editingContextSearchService) {
        this.editingContextSearchService = Objects.requireNonNull(editingContextSearchService);
    }

    /**
     * Parse a ZIP archive and return the resulting Steps model WITHOUT writing to the editing context.
     * Used by EdtImportEcoaStepsEventHandler for proper event-driven persistence.
     */
    public Optional<Steps> parseZipToSteps(byte[] zipBytes) {
        LOGGER.info("Parsing ECOA ZIP to Steps model");
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ecoa-import-");
            extractZip(zipBytes, tempDir);

            Path projectXmlPath = findProjectXml(tempDir);
            if (projectXmlPath == null) {
                LOGGER.warn("No project.xml found in ZIP.");
                return Optional.empty();
            }

            Steps steps = importFromProjectXml(projectXmlPath);
            return Optional.ofNullable(steps);

        } catch (IOException e) {
            LOGGER.error("Error parsing ECOA ZIP", e);
            return Optional.empty();
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    public Optional<Steps> importFromZip(String editingContextId, byte[] zipBytes) {
        LOGGER.info("Starting ECOA import");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ecoa-import-");
            extractZip(zipBytes, tempDir);

            Path projectXmlPath = findProjectXml(tempDir);
            if (projectXmlPath == null) {
                LOGGER.warn("No project.xml found, existing.");
                return Optional.empty();
            }

            Steps steps = importFromProjectXml(projectXmlPath);

            if (steps != null) {
                addToEditingContext(editingContextId, steps);
                return Optional.of(steps);
            }

        } catch (IOException e) {
            LOGGER.error("Error during ECOA import", e);
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }

        return Optional.empty();
    }

    private void extractZip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath);
                }
                zis.closeEntry();
            }
        }
    }

    private Path findProjectXml(Path baseDir) throws IOException {
        return Files.walk(baseDir)
                .filter(p -> p.getFileName().toString().endsWith(".project.xml"))
                .findFirst()
                .orElse(null);
    }

    private Steps importFromProjectXml(Path projectXmlPath) throws IOException {
        EcoaProject ecoaProject = parseProjectXml(projectXmlPath);
        if (ecoaProject == null) {
            LOGGER.error("Failed to parse project.xml");
            return null;
        }

        Path stepsDir = projectXmlPath.getParent();
        Steps steps = createEDTProject(ecoaProject);

        // Find steps
        Step0 step0 = null;
        Step1 step1 = null;
        Step2 step2 = null;
        Step3 step3 = null;
        Step4 step4 = null;
        Step5 step5 = null;

        for (Step step : steps.getStep()) {
            if (step instanceof Step0 s) step0 = s;
            else if (step instanceof Step1 s) step1 = s;
            else if (step instanceof Step2 s) step2 = s;
            else if (step instanceof Step3 s) step3 = s;
            else if (step instanceof Step4 s) step4 = s;
            else if (step instanceof Step5 s) step5 = s;
        }

        Map<edttype.Library, ArrayList<String>> associatedUsedLibraries = new HashMap<>();
        Map<EObject, String> associatedUsedTypes = new HashMap<>();
        ArrayList<EObject> associatedConstantToTypes = new ArrayList<>();
        StringBuilder missingElements = new StringBuilder();

        // Step 0 - Types
        if (step0 != null) {
            importStep0(ecoaProject, stepsDir, step0, associatedUsedLibraries, associatedUsedTypes, associatedConstantToTypes);
            TypesImportConverter.findAndAssociateUsedLibraries(associatedUsedLibraries, step0);
            TypesImportConverter.findAndAssociateTypes(associatedUsedTypes, step0);
        }

        // Step 1 - Services
        if (step1 != null && step0 != null) importStep1(ecoaProject, stepsDir, step0, step1);

        // Step 2 - Component Definitions
        if (step2 != null) importStep2(ecoaProject, stepsDir, steps, missingElements);

        // Step 3 - Initial Assembly
        if (step3 != null) importStep3(ecoaProject, stepsDir, steps, missingElements);

        // Step 4 - Component Implementations
        if (step4 != null) importStep4(ecoaProject, stepsDir, steps);

        // Step 5 - Integration (Final Assembly, Logical System, Deployment)
        if (step5 != null) importStep5(ecoaProject, stepsDir, steps, missingElements);

        if (missingElements.length() > 0) {
            LOGGER.warn("Import warnings:\n{}", missingElements);
        }

        return steps;
    }

    private Steps createEDTProject(EcoaProject ecoaProject) {
        Steps steps = EDTFACTORY.createSteps();
        
        Step0 step0 = EDTFACTORY.createStep0();
        step0.setFolderName(ZERO_TYPES);
        step0.getBasicTypes().addAll(EDTTypeDefaultCreator.createBasicTypes());
        step0.getEcoaPredefinedTypes().addAll(EDTTypeDefaultCreator.createPredefinedTypes(step0));
        steps.getStep().add(step0);

        Step1 step1 = EDTFACTORY.createStep1();
        step1.setFolderName(ONE_SERVICES);
        steps.getStep().add(step1);
        
        Step2 step2 = EDTFACTORY.createStep2();
        step2.setFolderName(TWO_COMPONENT_DEFINITIONS);
        steps.getStep().add(step2);

        Step3 step3 = EDTFACTORY.createStep3();
        step3.setFolderName(THREE_INITIAL_ASSEMBLY);
        steps.getStep().add(step3);

        Step4 step4 = EDTFACTORY.createStep4();
        step4.setFolderName(FOUR_COMPONENT_IMPLEMENTATIONS);
        steps.getStep().add(step4);

        Step5 step5 = EDTFACTORY.createStep5();
        step5.setFolderName(FIVE_INTEGRATION);
        steps.getStep().add(step5);

        steps.setOutputDirectory(EDTFACTORY.createOutputDirectory());
        return steps;
    }

    private void importStep0(EcoaProject ecoaProject, Path stepsDir, Step0 step0,
            Map<edttype.Library, ArrayList<String>> libs, Map<EObject, String> types, ArrayList<EObject> consts) {
        for (var bg : ecoaProject.getTypes()) {
            for (String f : bg.getFile()) {
                if (f.isBlank()) continue;
                try {
                    Path p = stepsDir.resolve(f.replace("\\", "/"));
                    var lib = parseTypesXml(p);
                    if (lib != null) {
                        step0.getTypes().add(TypesImportConverter.createEDTLibrary(
                                lib, p.getFileName().toString(), libs, types, consts));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to import types: {}", f, e);
                }
            }
        }
    }

    private void importStep1(EcoaProject ecoaProject, Path stepsDir, Step0 step0, Step1 step1) {
        for (var bg : ecoaProject.getServiceDefinitions()) {
            for (String f : bg.getFile()) {
                if (f.isBlank()) continue;
                try {
                    Path p = stepsDir.resolve(f.replace("\\", "/"));
                    var svc = parseInterfaceXml(p);
                    if (svc != null) {
                        step1.getServices().add(ServiceDefinitionImportConverter.createEDTServiceDefinition(
                                svc, p.getFileName().toString(), step0));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to import service: {}", f, e);
                }
            }
        }
    }

    private void importStep2(EcoaProject ecoaProject, Path stepsDir, Steps steps, StringBuilder missing) {
        for (var bg : ecoaProject.getComponentDefinitions()) {
            for (String f : bg.getFile()) {
                if (f.isBlank()) continue;
                try {
                    Path p = stepsDir.resolve(f.replace("\\", "/"));
                    var ct = parseComponentTypeXml(p);
                    if (ct != null) {
                        steps.getStep2().getComponentDefinitions().add(ComponentDefinitionImportConverter.createEDTComponentDefinition(
                                ct, p.getFileName().toString(), steps.getStep0(), new ArrayList<>(), steps.getStep1(), missing));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to import component definition: {}", f, e);
                }
            }
        }
    }

    private void importStep3(EcoaProject ecoaProject, Path stepsDir, Steps steps, StringBuilder missing) {
        for (String f : ecoaProject.getInitialAssembly()) {
             if (f.isBlank()) continue;
             try {
                 Path p = stepsDir.resolve(f.replace("\\", "/"));
                 var comp = parseCompositeXml(p);
                 if (comp != null) {
                     steps.getStep3().setInitialAssembly(AssemblyImportConverter.createEDTComposite(
                             comp, steps.getStep2(), steps.getStep0(), steps.getStep1(), missing));
                 }
             } catch (Exception e) {
                 LOGGER.error("Failed to import initial assembly: {}", f, e);
             }
        }
    }

    private void importStep4(EcoaProject ecoaProject, Path stepsDir, Steps steps) {
        for (var bg : ecoaProject.getComponentImplementations()) {
            for (String f : bg.getFile()) {
                if (f.isBlank()) continue;
                try {
                    Path p = stepsDir.resolve(f.replace("\\", "/"));
                    var imp = parseImplementationXml(p);
                    if (imp != null) {
                        // Find associated component definition
                        String compDefName = imp.getComponentDefinition();
                        edtproject.ComponentDefinition compDef = steps.getStep2().findComponentDefinitionByName(compDefName);
                        if (compDef != null) {
                             steps.getStep4().getComponentImplementations().add(ComponentImplementationImportConverter.createEDTComponentImplementation(
                                     imp, p.getFileName().toString(), compDef, new ArrayList<>(), steps.getStep0()));
                        } else {
                            LOGGER.error("Component Definition {} not found for implementation {}", compDefName, f);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to import component implementation: {}", f, e);
                }
            }
        }
    }

    private void importStep5(EcoaProject ecoaProject, Path stepsDir, Steps steps, StringBuilder missing) {
        // Implementation Assembly
        for (String f : ecoaProject.getImplementationAssembly()) {
             if (f.isBlank()) continue;
             try {
                 Path p = stepsDir.resolve(f.replace("\\", "/"));
                 var comp = parseCompositeXml(p);
                 
                 if (comp != null && steps.getStep3().getInitialAssembly() == null) {
                     steps.getStep3().setInitialAssembly(AssemblyImportConverter.createEDTComposite(
                             comp, steps.getStep2(), steps.getStep0(), steps.getStep1(), missing));
                 }
                 
                 if (comp != null && steps.getStep3().getInitialAssembly() != null) {
                      AssemblyImportConverter.addImplementationToInitialAssembly(
                              comp, steps.getStep3().getInitialAssembly(), steps.getStep4(), steps.getStep0(), missing);
                      
                      FinalAssembly finalAssembly = EDTFACTORY.createFinalAssembly();
                      finalAssembly.setFinalAssembly(steps.getStep3().getInitialAssembly());
                      finalAssembly.setName(comp.getName());
                      steps.getStep5().setFinalAssembly(finalAssembly);
                 }
             } catch (Exception e) {
                 LOGGER.error("Failed to import final assembly: {}", f, e);
             }
        }

        // Logical System
        for (String f : ecoaProject.getLogicalSystem()) {
             if (f.isBlank()) continue;
             try {
                 Path p = stepsDir.resolve(f.replace("\\", "/"));
                 var ls = parseLogicalSystemXml(p);
                 if (ls != null) {
                      var edtLs = LogicalSystemImportConverter.createEDTLogicalSystem(
                              ls, p.getFileName().toString());
                      // Apply DDS middleware settings from non-standard transportBinding attributes
                      applyDdsAttributes(edtLs, p);
                      steps.getStep5().setLogicalSystem(edtLs);
                 }
             } catch (Exception e) {
                 LOGGER.error("Failed to import logical system: {}", f, e);
             }
        }

        // Deployment
        for (String f : ecoaProject.getDeploymentSchema()) {
             if (f.isBlank()) continue;
             try {
                 Path p = stepsDir.resolve(f.replace("\\", "/"));
                 var dep = parseDeploymentXml(p);
                 if (dep != null && steps.getStep5().getFinalAssembly() != null && steps.getStep5().getLogicalSystem() != null) {
                      steps.getStep5().setDeployment(DeploymentImportConverter.createEDTDeployment(
                              dep, p.getFileName().toString(), steps.getStep5().getFinalAssembly(), steps.getStep5().getLogicalSystem()));
                 }
             } catch (Exception e) {
                 LOGGER.error("Failed to import deployment: {}", f, e);
             }
        }

        // Custom nodes deployment file
        if (steps.getStep5().getLogicalSystem() != null) {
            try {
                Path nodesDeploymentPath = findNodesDeploymentXml(stepsDir, steps.getStep5().getFolderName());
                if (nodesDeploymentPath != null) {
                    NodesDeploymentImportConverter.apply(nodesDeploymentPath, steps.getStep5().getLogicalSystem());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to import nodes_deployment.xml", e);
            }
        }

        // Import DDS Bindings (*.dds-binding.xml)
        try {
            for (Path ddsFile : findFilesBySuffix(stepsDir, steps.getStep5().getFolderName(), ".dds-binding.xml")) {
                DDSBinding binding = DDSBindingImportConverter.parse(ddsFile);
                if (binding != null) {
                    steps.getStep5().getDDSBindings().add(binding);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to import dds-binding.xml files", e);
        }

        // Import UDP Bindings (*.udp-binding.xml)
        try {
            for (Path udpFile : findFilesBySuffix(stepsDir, steps.getStep5().getFolderName(), ".udp-binding.xml")) {
                UDPBinding binding = UDPBindingImportConverter.parse(udpFile);
                if (binding != null) {
                    steps.getStep5().getUDPBindings().add(binding);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to import udp-binding.xml files", e);
        }

        // Import TCP Bindings (*.tcp-params.xml)
        try {
            for (Path tcpFile : findFilesBySuffix(stepsDir, steps.getStep5().getFolderName(), ".tcp-params.xml")) {
                TCPBinding binding = TCPBindingImportConverter.parse(tcpFile);
                if (binding != null) {
                    steps.getStep5().getTCPBindings().add(binding);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to import tcp-params.xml files", e);
        }
    }

    // --- XML Parsers ---

    private EcoaProject parseProjectXml(Path path) throws IOException {
        return loadModel(path, "xml", projPackage.eNS_URI, projPackage.eINSTANCE, new projResourceFactoryImpl(), technology.ecoa.project._2.DocumentRoot.class).getECOAProject();
    }

    private technology.ecoa.types._2.Library parseTypesXml(Path path) throws IOException {
        return loadModel(path, "xml", typPackage.eNS_URI, typPackage.eINSTANCE, new typResourceFactoryImpl(), technology.ecoa.types._2.DocumentRoot.class).getLibrary();
    }

    private technology.ecoa.interface_._2.ServiceDefinition parseInterfaceXml(Path path) throws IOException {
        return loadModel(path, "xml", interPackage.eNS_URI, interPackage.eINSTANCE, new interResourceFactoryImpl(), technology.ecoa.interface_._2.DocumentRoot.class).getServiceDefinition();
    }
    
    private org.open.oasis.docs.ns.opencsa.sca.sca.ComponentType parseComponentTypeXml(Path path) throws IOException {
        return loadModel(path, "xml", org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eNS_URI, org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eINSTANCE, 
                new org.open.oasis.docs.ns.opencsa.sca.sca.util.scaResourceFactoryImpl(), org.open.oasis.docs.ns.opencsa.sca.sca.DocumentRoot.class).getComponentType();
    }
    
    private org.open.oasis.docs.ns.opencsa.sca.sca.Composite parseCompositeXml(Path path) throws IOException {
         return loadModel(path, "xml", org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eNS_URI, org.open.oasis.docs.ns.opencsa.sca.sca.scaPackage.eINSTANCE, 
                 new org.open.oasis.docs.ns.opencsa.sca.sca.util.scaResourceFactoryImpl(), org.open.oasis.docs.ns.opencsa.sca.sca.DocumentRoot.class).getComposite();
    }

    private technology.ecoa.implementation._2.ComponentImplementation parseImplementationXml(Path path) throws IOException {
        return loadModel(path, "xml", impPackage.eNS_URI, impPackage.eINSTANCE, new impResourceFactoryImpl(), technology.ecoa.implementation._2.DocumentRoot.class).getComponentImplementation();
    }

    private technology.ecoa.logicalsystem._2.LogicalSystem parseLogicalSystemXml(Path path) throws IOException {
         return loadModel(path, "xml", logPackage.eNS_URI, logPackage.eINSTANCE, new logResourceFactoryImpl(), technology.ecoa.logicalsystem._2.DocumentRoot.class).getLogicalSystem();
    }

    private technology.ecoa.deployment._2.Deployment parseDeploymentXml(Path path) throws IOException {
         return loadModel(path, "xml", depPackage.eNS_URI, depPackage.eINSTANCE, new depResourceFactoryImpl(), technology.ecoa.deployment._2.DocumentRoot.class).getDeployment();
    }

    @SuppressWarnings("unchecked")
    private <T> T loadModel(Path path, String ext, String nsURI, EObject pkg, Object rFactory, Class<T> rootClass) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        // Register the factory for the provided 'ext'
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(ext, rFactory);
        // Also register for the actual file extension (e.g. 'componentType', 'composite')
        // because some ECOA files don't use '.xml' as their extension
        String filename = path.getFileName().toString();
        if (filename.contains(".")) {
            String actualExt = filename.substring(filename.lastIndexOf('.') + 1);
            if (!actualExt.equals(ext)) {
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(actualExt, rFactory);
            }
        }
        resourceSet.getPackageRegistry().put(nsURI, pkg);

        URI uri = URI.createFileURI(path.toAbsolutePath().toString());
        // Use false to NOT auto-load, so that we can pass our load options (ExtendedMetaData)
        // before the first load. If we use getResource(uri, true), the resource is loaded
        // immediately with default options and a subsequent load() call is a no-op.
        Resource resource = resourceSet.getResource(uri, false);
        if (resource == null) {
            resource = resourceSet.createResource(uri);
        }

        Map<Object, Object> loadOptions = new HashMap<>();
        loadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
        resource.load(loadOptions);

        if (!resource.getContents().isEmpty()) {
            EObject root = resource.getContents().get(0);
            if (rootClass.isInstance(root)) {
                return (T) root;
            }
        }
        throw new IOException("Unexpected root content in file " + path);
    }

    private void addToEditingContext(String editingContextId, Steps steps) {
        this.editingContextSearchService.findById(editingContextId)
                .filter(IEMFEditingContext.class::isInstance)
                .map(IEMFEditingContext.class::cast)
                .ifPresent(editingContext -> {
                    ResourceSet resourceSet = editingContext.getDomain().getResourceSet();

                    // Remove any existing Steps resource to avoid duplicates
                    resourceSet.getResources().stream()
                            .filter(r -> !r.getContents().isEmpty() && r.getContents().get(0) instanceof Steps)
                            .findFirst()
                            .ifPresent(existing -> {
                                existing.getContents().clear();
                                resourceSet.getResources().remove(existing);
                                LOGGER.info("Removed existing Steps resource before re-import");
                            });

                    // Use JSONResourceFactory so Sirius Web can persist this resource to DB
                    var documentId = java.util.UUID.randomUUID();
                    var resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
                    resource.eAdapters().add(new ResourceMetadataAdapter("ECOA Import"));
                    resourceSet.getResources().add(resource);
                    resource.getContents().add(steps);
                    LOGGER.info("Added imported steps to editing context as JSON resource: {}", documentId);
                });
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up temp directory: {}", tempDir, e);
        }
    }

    private java.util.List<Path> findFilesBySuffix(Path stepsDir, String step5FolderName, String suffix) throws IOException {
        try (var paths = Files.walk(stepsDir.resolve(step5FolderName))) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(suffix))
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.nio.file.NoSuchFileException e) {
            return java.util.Collections.emptyList();
        }
    }

    private Path findNodesDeploymentXml(Path stepsDir, String step5FolderName) throws IOException {
        Path conventionalPath = stepsDir.resolve(step5FolderName).resolve("nodes_deployment.xml");
        if (Files.exists(conventionalPath)) {
            return conventionalPath;
        }
        try (var paths = Files.walk(stepsDir)) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("nodes_deployment.xml"))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Reads the raw logical-system XML file with DOM to extract non-standard {@code dds} and
     * {@code ddsDomainId} attributes from {@code <transportBinding>} elements.
     * These attributes are injected during export but are invisible to the standard EMF parser.
     * <p>
     * Also handles the old format: if {@code protocol="DDS"}, sets {@code useDDS=true} and
     * reads the domain ID from the DDS binding file if available.
     */
    private void applyDdsAttributes(edtlogical.LogicalSystem edtLs, Path xmlPath) {
        if (edtLs == null || edtLs.getLogicalComputingPlatformLinks().isEmpty()) {
            return;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(xmlPath.toFile());

            // Build a map: link id → transportBinding element
            Map<String, org.w3c.dom.Element> linkMap = new HashMap<>();
            NodeList linkNodes = doc.getElementsByTagNameNS("*", "link");
            for (int i = 0; i < linkNodes.getLength(); i++) {
                org.w3c.dom.Element linkEl = (org.w3c.dom.Element) linkNodes.item(i);
                String linkId = linkEl.getAttribute("id");
                NodeList tbNodes = linkEl.getElementsByTagNameNS("*", "transportBinding");
                if (tbNodes.getLength() > 0) {
                    linkMap.put(linkId, (org.w3c.dom.Element) tbNodes.item(0));
                }
            }

            for (LogicalComputingPlatformLink edtLink : edtLs.getLogicalComputingPlatformLinks()) {
                if (edtLink.getId() == null) {
                    continue;
                }
                org.w3c.dom.Element tb = linkMap.get(edtLink.getId());
                if (tb == null) {
                    continue;
                }
                // New format: dds="true" attribute
                if ("true".equalsIgnoreCase(tb.getAttribute("dds"))) {
                    edtLink.setUseDDS(true);
                    String domainIdStr = tb.getAttribute("ddsDomainId");
                    if (!domainIdStr.isBlank()) {
                        try {
                            edtLink.setDdsDomainId(Integer.parseInt(domainIdStr));
                        } catch (NumberFormatException ex) {
                            edtLink.setDdsDomainId(0);
                        }
                    }
                }
                // Old format compatibility: protocol="DDS" → mark as DDS (domain ID defaults to 0)
                if ("DDS".equals(tb.getAttribute("protocol"))) {
                    edtLink.setUseDDS(true);
                    // Remap protocol to underlying transport (TCP as fallback)
                    if (edtLink.getTransportBindingProtocol() == null
                            || edtLink.getTransportBindingProtocol().equals("DDS")) {
                        edtLink.setTransportBindingProtocol("TCP");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read DDS attributes from {}: {}", xmlPath, e.getMessage());
        }
    }
}
