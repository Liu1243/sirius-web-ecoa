package org.eclipse.sirius.web.edt.importexport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.emf.services.IDAdapter;
import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.collaborative.api.Monitoring;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.graphql.api.UploadFile;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.document.services.api.IUploadFileLoader;
import org.eclipse.sirius.web.application.document.services.api.UploadedResource;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.RepresentationCompositeIdProvider;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationContentCreationService;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationMetadataCreationService;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationMetadataDeletionService;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.SemanticData;
import org.eclipse.sirius.web.domain.services.IResult;
import org.eclipse.sirius.web.domain.services.Success;
import org.eclipse.sirius.web.domain.services.api.IMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;

import edtimplementation.ComponentImplementation;
import edtimplementation.DataLink;
import edtimplementation.EventLink;
import edtimplementation.Instance;
import edtimplementation.ModuleInstance;
import edtimplementation.OperationLink;
import edtimplementation.ReferenceOfLinkedComponentDefinition;
import edtimplementation.RequestLink;
import edtimplementation.ServiceOfLinkedComponentDefinition;
import edtinterface.ServiceDefinition;
import edtlogical.LogicalSystem;
import edtproject.Composite;
import edtproject.Step1;
import edtproject.Step3;
import edtproject.Step4;
import edtproject.Step5;
import edtproject.Steps;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * IEditingContextEventHandler that imports a Sirius Web project ZIP into an existing EDT project.
 *
 * <p><b>Strategy:</b>
 * <ol>
 *   <li>Parse the ZIP to find the {@code documents/*.json} file that contains a {@link Steps} root.</li>
 *   <li>Load it into the editing context's ResourceSet via {@link IUploadFileLoader}.</li>
 *   <li>Immediately remove the loaded resource from the ResourceSet (we will move Steps to a
 *       fresh JSONResource so that the intermediate upload document does not appear as a phantom
 *       node in the explorer tree — fix for "filename.json in treeview" bug).</li>
 *   <li>Remove the existing Steps resource from the editing context's ResourceSet.</li>
 *   <li>Move the loaded Steps into a fresh JSONResource (same pattern as
 *       {@link EdtImportEcoaStepsEventHandler}) so Sirius Web persists it correctly.</li>
 *   <li>Delete all existing representation metadata for this editing context (stale references
 *       to old object IDs) and re-create representation metadata stubs from {@code representations/*.json}
 *       entries found in the ZIP, using name-based lookup to resolve ComponentImplementation targetObjectIds.</li>
 *   <li>Optionally import selected ComponentCode versions from
 *       {@code component-code/component-code-manifest.json}.</li>
 * </ol>
 * Emits {@code SEMANTIC_CHANGE} to trigger DB persistence.
 */
@Service
public class EdtImportSiriusWebZipEventHandler implements IEditingContextEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtImportSiriusWebZipEventHandler.class);

    private static final String DOCUMENTS_DIR = "documents/";
    private static final String REPRESENTATIONS_DIR = "representations/";
    private static final String COMPONENT_CODE_DIR = "component-code/";
    private static final String CC_MANIFEST_FILE = "component-code-manifest.json";
    private static final String ECOA_INTERFACE_CACHE_DIR = "ecoa-interface-cache/";
    /** Embedded ECOA XML bundle written by {@link EcoaXmlProjectExportParticipant}. */
    private static final String ECOA_BUNDLE_ENTRY = EcoaXmlProjectExportParticipant.ECOA_BUNDLE_ENTRY;

    /** Suffix used in diagram labels for Component Implementation Diagrams. */
    private static final String CI_DIAGRAM_SUFFIX = " - Component Implementation Diagram";
    private static final String COMPOSITE_DIAGRAM_SUFFIX = " - Composite Diagram";
    private static final String LOGICAL_SYSTEM_DIAGRAM_SUFFIX = " - Logical System Diagram";

    private final IUploadFileLoader uploadFileLoader;
    private final IComponentCodeVersionService versionService;
    private final IComponentCodeTagService tagService;
    private final ObjectMapper objectMapper;
    private final IMessageService messageService;
    private final IRepresentationMetadataDeletionService representationMetadataDeletionService;
    private final IRepresentationMetadataCreationService representationMetadataCreationService;
    private final IRepresentationContentCreationService representationContentCreationService;
    private final EdtEcoaExportService ecoaExportService;
    private final EdtEcoaImportService ecoaImportService;
    private final EcoaInterfaceXmlCache interfaceXmlCache;
    private final Counter counter;

    public EdtImportSiriusWebZipEventHandler(
            IUploadFileLoader uploadFileLoader,
            IComponentCodeVersionService versionService,
            IComponentCodeTagService tagService,
            ObjectMapper objectMapper,
            IMessageService messageService,
            IRepresentationMetadataDeletionService representationMetadataDeletionService,
            IRepresentationMetadataCreationService representationMetadataCreationService,
            IRepresentationContentCreationService representationContentCreationService,
            EdtEcoaExportService ecoaExportService,
            EdtEcoaImportService ecoaImportService,
            EcoaInterfaceXmlCache interfaceXmlCache,
            MeterRegistry meterRegistry) {
        this.uploadFileLoader = Objects.requireNonNull(uploadFileLoader);
        this.versionService = Objects.requireNonNull(versionService);
        this.tagService = Objects.requireNonNull(tagService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.messageService = Objects.requireNonNull(messageService);
        this.representationMetadataDeletionService = Objects.requireNonNull(representationMetadataDeletionService);
        this.representationMetadataCreationService = Objects.requireNonNull(representationMetadataCreationService);
        this.representationContentCreationService = Objects.requireNonNull(representationContentCreationService);
        this.ecoaExportService = Objects.requireNonNull(ecoaExportService);
        this.ecoaImportService = Objects.requireNonNull(ecoaImportService);
        this.interfaceXmlCache = Objects.requireNonNull(interfaceXmlCache);
        this.counter = Counter.builder(Monitoring.EVENT_HANDLER)
                .tag(Monitoring.NAME, this.getClass().getSimpleName())
                .register(meterRegistry);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof EdtImportSiriusWebZipInput;
    }

    @Override
    public void handle(One<IPayload> payloadSink, Many<ChangeDescription> changeDescriptionSink,
            IEditingContext editingContext, IInput input) {
        this.counter.increment();

        IPayload payload = new ErrorPayload(input.id(), this.messageService.unexpectedError());
        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, editingContext.getId(), input);

        if (!(input instanceof EdtImportSiriusWebZipInput importInput)) {
            payloadSink.tryEmitValue(payload);
            changeDescriptionSink.tryEmitNext(changeDescription);
            return;
        }
        if (!(editingContext instanceof IEMFEditingContext emfEditingContext)) {
            payloadSink.tryEmitValue(payload);
            changeDescriptionSink.tryEmitNext(changeDescription);
            return;
        }

        try {
            // 1. Parse the ZIP — extract Steps JSON, representations, and component-code manifest.
            ZipContents zipContents = parseZip(importInput.zipBytes());

            if (zipContents.stepsDocumentBytes() == null) {
                payload = new ErrorPayload(input.id(), "ZIP contains no Steps document");
                payloadSink.tryEmitValue(payload);
                changeDescriptionSink.tryEmitNext(changeDescription);
                return;
            }

            // 2. Load the Steps JSON via uploadFileLoader.
            //    NOTE: UploadFileLoader IGNORES the ResourceSet parameter and always loads into
            //    emfEditingContext.getDomain().getResourceSet().  We must remove the loaded
            //    resource immediately afterwards to prevent it appearing as a phantom document
            //    node in the explorer tree (bug: "filename.json in treeview").
            var targetResourceSet = emfEditingContext.getDomain().getResourceSet();

            UploadFile uploadFile = new UploadFile(
                    zipContents.stepsDocumentName(),
                    new ByteArrayInputStream(zipContents.stepsDocumentBytes()));

            IResult<UploadedResource> loadResult = this.uploadFileLoader.load(
                    targetResourceSet, emfEditingContext, uploadFile, true, false);

            if (!(loadResult instanceof Success<UploadedResource> success)) {
                payload = new ErrorPayload(input.id(), "Failed to load Steps document from ZIP");
                payloadSink.tryEmitValue(payload);
                changeDescriptionSink.tryEmitNext(changeDescription);
                return;
            }

            Resource loadedResource = success.data().resource();

            // KEY: DocumentSanitizedJsonContentProvider.refreshElementIds() always regenerates
            // ALL EObject IDAdapter UUIDs with fresh random values.  The idMapping tracks
            // oldUUID -> newUUID for every EObject in the imported Steps resource.  We need
            // this mapping later to rewrite representation targetObjectId values.
            Map<String, String> semanticIdMapping = success.data().idMapping();

            // 3. Immediately remove the loaded resource from the editing context's ResourceSet.
            //    We will place the Steps into a fresh JSONResource ourselves (step 5), so the
            //    intermediate upload document must not remain in the RS.
            targetResourceSet.getResources().remove(loadedResource);

            // 4. Extract Steps from the (now-detached) loaded resource.
            Steps jsonLoadedSteps = loadedResource.getContents().stream()
                    .filter(Steps.class::isInstance)
                    .map(Steps.class::cast)
                    .findFirst()
                    .orElse(null);

            if (jsonLoadedSteps == null) {
                payload = new ErrorPayload(input.id(), "Steps document does not contain a Steps root object");
                payloadSink.tryEmitValue(payload);
                changeDescriptionSink.tryEmitNext(changeDescription);
                return;
            }

            // 4a. Build a name -> UUID map from the JSON-loaded Steps BEFORE the ECOA roundtrip.
            //     refreshElementIds() has already run on jsonLoadedSteps, so every EObject has a
            //     fresh IDAdapter with a stable (for this import session) UUID.  We capture the
            //     ComponentImplementation name -> UUID mapping now so we can re-apply those same
            //     UUIDs to the cleanSteps produced by the ECOA roundtrip (which has no IDAdapters).
            Map<String, String> compImplNameToUUID = buildCompImplNameToUUID(jsonLoadedSteps);
            LOGGER.info("Built compImplNameToUUID with {} entries", compImplNameToUUID.size());

            // 4b'. Restore EcoaInterfaceXmlCache from the ZIP before any ECOA operation.
            //      exportStepsToZip() uses this cache as fallback when EDT model service
            //      operations are empty (lost by Sirius Web JSON serialization).  Without
            //      pre-populating it, the roundtrip would produce empty service interfaces
            //      and all connections would be lost.
            if (!zipContents.interfaceCacheEntries().isEmpty()) {
                zipContents.interfaceCacheEntries().forEach((fileName, bytes) ->
                        this.interfaceXmlCache.store(editingContext.getId(), fileName, bytes));
                LOGGER.info("Populated EcoaInterfaceXmlCache with {} entries from ZIP", zipContents.interfaceCacheEntries().size());
            }

            // 4b. Rebuild the clean EDT model.
            //
            //     Strategy (in priority order):
            //
            //     1. PREFERRED — use the embedded ECOA XML bundle (ecoa-steps-bundle.zip).
            //        Written by EcoaXmlProjectExportParticipant during export.  The bundle is
            //        generated by EdtEcoaExportService.exportToZip() which already uses the
            //        EcoaInterfaceXmlCache as fallback for empty service definitions.  Importing
            //        from this bundle guarantees a fully correct EDT model on any machine.
            //
            //     2. FALLBACK — ECOA roundtrip: export the JSON-loaded Steps → re-import.
            //        Used when the ZIP was produced before the embedded bundle feature was
            //        introduced (old format ZIPs).  Requires the EcoaInterfaceXmlCache to be
            //        populated (step 4b') so that empty service definitions can be recovered.
            Steps newSteps;
            if (zipContents.ecoaStepsBundleBytes() != null) {
                LOGGER.info("Using embedded ECOA bundle to rebuild Steps (guaranteed correct connections)");
                var bundleStepsOpt = this.ecoaImportService.parseZipToSteps(zipContents.ecoaStepsBundleBytes());
                newSteps = bundleStepsOpt.orElse(null);
                if (newSteps == null) {
                    LOGGER.warn("Embedded ECOA bundle import failed - falling back to JSON-loaded Steps");
                    newSteps = jsonLoadedSteps;
                } else {
                    LOGGER.info("Embedded ECOA bundle import successful - Steps model is clean");
                    assignCompImplUUIDs(newSteps, compImplNameToUUID);
                }
            } else {
                LOGGER.info("No embedded ECOA bundle found - using ECOA roundtrip (old ZIP format)");
                newSteps = rebuildStepsViaEcoaRoundtrip(jsonLoadedSteps,
                        importInput.projectName(), editingContext.getId());
                if (newSteps == null) {
                    LOGGER.warn("ECOA roundtrip failed - falling back to JSON-loaded Steps (connections may be missing)");
                    newSteps = jsonLoadedSteps;
                } else {
                    LOGGER.info("ECOA roundtrip successful - using cleanly rebuilt Steps model");
                    assignCompImplUUIDs(newSteps, compImplNameToUUID);
                }
            }

            // 5. Remove the existing Steps resource from the editing context.
            //    Now that loadedResource has been removed (step 3), only the OLD Steps resource
            //    can match this filter - so findFirst() is deterministic.
            targetResourceSet.getResources().stream()
                    .filter(r -> !r.getContents().isEmpty() && r.getContents().get(0) instanceof Steps)
                    .findFirst()
                    .ifPresent(existing -> {
                        existing.getContents().clear();
                        targetResourceSet.getResources().remove(existing);
                        LOGGER.info("Removed existing Steps resource before Sirius Web ZIP import");
                    });

            // 6. Move Steps into a fresh JSONResource in the editing context.
            var documentId = UUID.randomUUID();
            Resource jsonResource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
            jsonResource.eAdapters().add(new ResourceMetadataAdapter(
                    importInput.projectName() != null ? importInput.projectName() : "Imported Project"));
            targetResourceSet.getResources().add(jsonResource);

            // Detach from any intermediate resource before moving to jsonResource.
            Resource tempResource = newSteps.eResource();
            if (tempResource != null) {
                tempResource.getContents().remove(newSteps);
            }
            jsonResource.getContents().add(newSteps);

            LOGGER.info("Sirius Web ZIP import: Steps placed in JSON resource {}", documentId);

            // 7. Delete stale representations and recreate metadata stubs from ZIP.
            //
            //    After the ECOA roundtrip the element UUIDs come from two sources:
            //      - ComponentImplementations: we just pre-assigned the refreshed UUIDs from
            //        jsonLoadedSteps via assignCompImplUUIDs(), so compImplNameToUUID maps
            //        CI name -> correct UUID.
            //      - All other objects: their UUIDs are assigned lazily by Sirius Web on first
            //        access, so we cannot know them now.  semanticIdMapping covers objects that
            //        were in jsonLoadedSteps (old -> refreshed UUID).
            //
            //    We create metadata-only entries (no content) so that diagram stubs appear in
            //    the explorer tree immediately.  Sirius Web creates the actual diagram content
            //    on first open via the normal rendering path.
            recreateRepresentationMetadata(importInput, emfEditingContext,
                    zipContents, compImplNameToUUID, semanticIdMapping);

            // 8. Import selected ComponentCode versions.
            //    Fix: use importInput.projectId() (the project entity UUID), NOT editingContext.getId()
            //    (the semantic-data UUID), because IComponentCodeVersionService identifies projects
            //    by their project table UUID.
            //    Also pass semanticIdMapping so that componentId values (EObject UUIDs from the
            //    source project) are remapped to the new UUIDs assigned during loading.
            if (!importInput.selectedVersionIds().isEmpty() && zipContents.ccManifestBytes() != null) {
                importSelectedVersions(importInput.projectId(), zipContents, importInput.selectedVersionIds(), semanticIdMapping);
            }

            changeDescription = new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId(), input);
            payload = new SuccessPayload(input.id());

        } catch (Exception e) {
            LOGGER.error("Error during Sirius Web ZIP import", e);
            payload = new ErrorPayload(input.id(), "Import error: " + e.getMessage());
        }

        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }

    // -----------------------------------------------------------------------
    // ECOA roundtrip rebuild
    // -----------------------------------------------------------------------

    /**
     * Rebuilds a clean EDT model from a JSON-loaded Steps by performing an
     * ECOA XML roundtrip: exports the Steps to ECOA XML format (same output as
     * the dedicated ECOA export endpoint) and immediately re-imports using
     * {@link EdtEcoaImportService#parseZipToSteps(byte[])}.
     *
     * <p>This mirrors exactly what {@code EdtImportEcoaStepsEventHandler} does, and
     * avoids the {@code setModuleType()} side-effect that destroys
     * {@code OperationLinks} (DataLink / EventLink / RequestLink) when EMF JSON
     * deserialization triggers the method before proxy resolution has completed.
     *
     * @return the cleanly rebuilt Steps, or {@code null} if the roundtrip fails
     */
    private Steps rebuildStepsViaEcoaRoundtrip(Steps steps, String projectName, String editingContextId) {
        try {
            // Diagnostic: count DataLinks and service operations in source Steps
            logStepsDiagnostics("BEFORE-ROUNDTRIP", steps);

            String name = projectName != null ? projectName : "imported";
            var ecoaZipOpt = this.ecoaExportService.exportStepsToZip(steps, name, editingContextId);
            if (ecoaZipOpt.isEmpty()) {
                LOGGER.warn("ECOA export produced no bytes - cannot perform roundtrip");
                return null;
            }
            byte[] ecoaZipBytes = ecoaZipOpt.get();
            LOGGER.info("ECOA roundtrip: exported {} bytes of ECOA XML", ecoaZipBytes.length);
            var cleanStepsOpt = this.ecoaImportService.parseZipToSteps(ecoaZipBytes);
            if (cleanStepsOpt.isPresent()) {
                logStepsDiagnostics("AFTER-ROUNDTRIP", cleanStepsOpt.get());
            }
            return cleanStepsOpt.orElse(null);
        } catch (Exception e) {
            LOGGER.error("ECOA roundtrip failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Logs diagnostic counts for DataLinks, ModuleInstance operations, and service operations
     * so that we can trace exactly where connections are lost during the import pipeline.
     */
    private void logStepsDiagnostics(String phase, Steps steps) {
        if (steps == null || steps.getStep4() == null) {
            LOGGER.info("[DIAG {}] No Step4 to inspect", phase);
            return;
        }
        for (ComponentImplementation ci : steps.getStep4().getComponentImplementations()) {
            int dataLinks = 0, eventLinks = 0, requestLinks = 0;
            for (OperationLink link : ci.getOperationLinks()) {
                if (link instanceof DataLink) dataLinks++;
                else if (link instanceof EventLink) eventLinks++;
                else if (link instanceof RequestLink) requestLinks++;
            }
            int moduleInstanceOps = 0, serviceOps = 0, refOps = 0;
            for (Instance inst : ci.getInstances()) {
                if (inst instanceof ModuleInstance mi) {
                    moduleInstanceOps += mi.getOperations().size();
                } else if (inst instanceof ServiceOfLinkedComponentDefinition svc) {
                    serviceOps += svc.getOperations().size();
                } else if (inst instanceof ReferenceOfLinkedComponentDefinition ref) {
                    refOps += ref.getOperations().size();
                }
            }
            LOGGER.info("[DIAG {}] CI='{}': DataLinks={}, EventLinks={}, RequestLinks={} | ModuleInstanceOps={}, ServiceOps={}, RefOps={}",
                    phase, ci.getName(), dataLinks, eventLinks, requestLinks,
                    moduleInstanceOps, serviceOps, refOps);
        }

        // Also check Step1 service definitions
        steps.getStep().stream()
                .filter(Step1.class::isInstance)
                .map(Step1.class::cast)
                .findFirst()
                .ifPresent(step1 -> {
                    for (ServiceDefinition sd : step1.getServices()) {
                        LOGGER.info("[DIAG {}] ServiceDefinition='{}': operations={}", phase, sd.getName(), sd.getOperations().size());
                    }
                });
    }

    // -----------------------------------------------------------------------
    // ComponentImplementation UUID management
    // -----------------------------------------------------------------------

    /**
     * Builds a map from ComponentImplementation name to UUID by traversing the
     * Step4 of the given Steps.  The JSON-loaded Steps have had
     * {@code refreshElementIds()} applied, so every EObject carries a fresh
     * {@link IDAdapter} with a stable UUID for this import session.
     *
     * @param steps the JSON-loaded Steps (with IDAdapters already populated)
     * @return name -> UUID string map; empty if Step4 is absent or has no entries
     */
    private Map<String, String> buildCompImplNameToUUID(Steps steps) {
        Map<String, String> result = new HashMap<>();
        if (steps == null) {
            return result;
        }

        // ComponentImplementations (Step4)
        if (steps.getStep4() != null) {
            Step4 step4 = steps.getStep4();
            for (ComponentImplementation ci : step4.getComponentImplementations()) {
                String name = ci.getName();
                if (name == null || name.isBlank()) continue;
                ci.eAdapters().stream()
                        .filter(IDAdapter.class::isInstance)
                        .map(IDAdapter.class::cast)
                        .findFirst()
                        .ifPresent(adapter -> result.put(name, adapter.getId().toString()));
            }
        }

        // Composite / Initial Assembly (Step3) — for composite diagrams
        if (steps.getStep3() != null && steps.getStep3().getInitialAssembly() != null) {
            Composite composite = steps.getStep3().getInitialAssembly();
            String name = composite.getName();
            if (name != null && !name.isBlank()) {
                composite.eAdapters().stream()
                        .filter(IDAdapter.class::isInstance)
                        .map(IDAdapter.class::cast)
                        .findFirst()
                        .ifPresent(adapter -> result.put(name, adapter.getId().toString()));
            }
        }

        // LogicalSystem (Step5) — for logical system diagrams
        if (steps.getStep5() != null && steps.getStep5().getLogicalSystem() != null) {
            LogicalSystem ls = steps.getStep5().getLogicalSystem();
            String prefix = ls.getFileNamePrefix();
            if (prefix != null && !prefix.isBlank()) {
                ls.eAdapters().stream()
                        .filter(IDAdapter.class::isInstance)
                        .map(IDAdapter.class::cast)
                        .findFirst()
                        .ifPresent(adapter -> result.put(prefix, adapter.getId().toString()));
            }
        }

        return result;
    }

    /**
     * Assigns pre-determined UUIDs (from {@code compImplNameToUUID}) to the
     * ComponentImplementations in the given Steps by attaching an
     * {@link IDAdapter} with the mapped UUID.
     *
     * <p>The cleanSteps produced by the ECOA roundtrip do not carry IDAdapters -
     * Sirius Web assigns UUIDs lazily on first access.  By pre-populating the
     * IDAdapters here we ensure that the representation metadata targetObjectIds
     * we are about to store will match what Sirius Web reads from these objects.
     *
     * @param steps              the cleanly rebuilt Steps (no IDAdapters yet)
     * @param compImplNameToUUID name -> UUID string map built from jsonLoadedSteps
     */
    private void assignCompImplUUIDs(Steps steps, Map<String, String> compImplNameToUUID) {
        if (steps == null || compImplNameToUUID.isEmpty()) {
            return;
        }
        int assigned = 0;

        // ComponentImplementations (Step4)
        if (steps.getStep4() != null) {
            for (ComponentImplementation ci : steps.getStep4().getComponentImplementations()) {
                String name = ci.getName();
                if (name == null || name.isBlank()) continue;
                String uuidStr = compImplNameToUUID.get(name);
                if (uuidStr == null) continue;
                ci.eAdapters().removeIf(IDAdapter.class::isInstance);
                ci.eAdapters().add(new IDAdapter(UUID.fromString(uuidStr)));
                assigned++;
            }
        }

        // Composite (Step3) — for composite diagrams
        if (steps.getStep3() != null && steps.getStep3().getInitialAssembly() != null) {
            Composite composite = steps.getStep3().getInitialAssembly();
            String name = composite.getName();
            if (name != null && !name.isBlank()) {
                String uuidStr = compImplNameToUUID.get(name);
                if (uuidStr != null) {
                    composite.eAdapters().removeIf(IDAdapter.class::isInstance);
                    composite.eAdapters().add(new IDAdapter(UUID.fromString(uuidStr)));
                    assigned++;
                }
            }
        }

        // LogicalSystem (Step5) — for logical system diagrams
        if (steps.getStep5() != null && steps.getStep5().getLogicalSystem() != null) {
            LogicalSystem ls = steps.getStep5().getLogicalSystem();
            String prefix = ls.getFileNamePrefix();
            if (prefix != null && !prefix.isBlank()) {
                String uuidStr = compImplNameToUUID.get(prefix);
                if (uuidStr != null) {
                    ls.eAdapters().removeIf(IDAdapter.class::isInstance);
                    ls.eAdapters().add(new IDAdapter(UUID.fromString(uuidStr)));
                    assigned++;
                }
            }
        }

        LOGGER.info("assignCompImplUUIDs: pre-assigned {} IDAdapters (CIs + Composite + LogicalSystem)", assigned);
    }

    // -----------------------------------------------------------------------
    // Representation metadata recreation
    // -----------------------------------------------------------------------

    /**
     * Deletes all existing representation metadata for the editing context, then
     * recreates metadata-only stubs from the representation JSON entries in the ZIP.
     *
     * <p><b>Why metadata-only (no content)?</b> After the ECOA roundtrip the semantic
     * element UUIDs change.  Storing old content JSON (with stale node/edge IDs) would
     * cause rendering failures.  Instead we create empty stubs so that diagram entries
     * appear in the explorer tree; Sirius Web regenerates the content on first open via
     * its normal {@code DiagramEventProcessor} refresh path.
     *
     * <p><b>targetObjectId resolution:</b>
     * <ul>
     *   <li>If the representation label matches the pattern
     *       {@code "{name} - Component Implementation Diagram"}, we look up the CI name
     *       in {@code compImplNameToUUID}.  This UUID was pre-assigned to the cleanSteps
     *       CI via {@link #assignCompImplUUIDs}.</li>
     *   <li>Otherwise we apply {@code semanticIdMapping} (oldUUID -> refreshedUUID from
     *       {@code refreshElementIds()}) to the raw targetObjectId from the ZIP.</li>
     * </ul>
     *
     * @param compImplNameToUUID name -> UUID map built from jsonLoadedSteps (same UUIDs
     *                            pre-assigned to cleanSteps CIs)
     * @param semanticIdMapping  oldUUID -> newUUID map produced by {@link IUploadFileLoader#load}
     */
    private void recreateRepresentationMetadata(
            EdtImportSiriusWebZipInput importInput,
            IEMFEditingContext emfEditingContext,
            ZipContents zipContents,
            Map<String, String> compImplNameToUUID,
            Map<String, String> semanticIdMapping) {

        try {
            UUID semanticDataId = UUID.fromString(emfEditingContext.getId());
            var semanticDataRef = AggregateReference.<SemanticData, UUID>to(semanticDataId);

            // Delete all stale representation metadata.
            this.representationMetadataDeletionService.deleteAllRepresentationMetadata(importInput, semanticDataRef);
            LOGGER.info("Deleted all existing representations before recreating metadata stubs");

            if (zipContents.representations().isEmpty()) {
                LOGGER.info("ZIP contains no representations - no stubs to create");
                return;
            }

            int created = 0;
            for (Map.Entry<String, byte[]> entry : zipContents.representations().entrySet()) {
                boolean ok = createRepresentationMetadataStub(
                        importInput, semanticDataId, semanticDataRef,
                        entry.getKey(), entry.getValue(),
                        compImplNameToUUID, semanticIdMapping);
                if (ok) {
                    created++;
                }
            }
            LOGGER.info("Created {} representation metadata stubs from ZIP", created);

        } catch (Exception e) {
            LOGGER.warn("Failed to recreate representation metadata: {}", e.getMessage());
        }
    }

    /**
     * Creates a single representation metadata stub (no content) for one ZIP representation entry.
     *
     * @return {@code true} if the stub was created successfully, {@code false} otherwise
     */
    private boolean createRepresentationMetadataStub(
            EdtImportSiriusWebZipInput importInput,
            UUID semanticDataId,
            AggregateReference<SemanticData, UUID> semanticDataRef,
            String fileName,
            byte[] bytes,
            Map<String, String> compImplNameToUUID,
            Map<String, String> semanticIdMapping) {
        try {
            JsonNode root = this.objectMapper.readTree(bytes);

            String descriptionId = root.path("descriptionId").asText(null);
            String rawTargetObjectId = root.path("targetObjectId").asText(null);
            String label = root.path("label").asText("Imported Diagram");
            String kind = root.path("kind").asText(null);

            if (descriptionId == null || rawTargetObjectId == null || kind == null) {
                LOGGER.warn("Skipping malformed representation JSON in ZIP entry '{}' (missing required fields)", fileName);
                return false;
            }

            // Resolve the targetObjectId for the new project.
            String targetObjectId = resolveTargetObjectId(label, rawTargetObjectId, compImplNameToUUID, semanticIdMapping);

            UUID newRepMetadataId = UUID.randomUUID();
            String compositeId = new RepresentationCompositeIdProvider().getId(semanticDataId, newRepMetadataId);

            RepresentationMetadata repMetadata = RepresentationMetadata.newRepresentationMetadata(compositeId)
                    .representationMetadataId(newRepMetadataId)
                    .semanticData(semanticDataRef)
                    .kind(kind)
                    .label(label)
                    .descriptionId(descriptionId)
                    .targetObjectId(targetObjectId)
                    .iconURLs(List.of())
                    .documentation("")
                    .build(importInput);
            this.representationMetadataCreationService.create(repMetadata);

            // Create minimal diagram content so the DiagramEventProcessor can initialise.
            // Without a content row the event processor cannot be found, causing GraphQL
            // null errors on nodeDescriptions / dropNodeCompatibility.  The refresher will
            // immediately replace this empty diagram with the correct semantic content.
            String minimalContent = buildMinimalDiagramContent(newRepMetadataId, targetObjectId, descriptionId, kind);
            this.representationContentCreationService.create(
                    importInput, semanticDataRef,
                    AggregateReference.to(newRepMetadataId),
                    minimalContent, "", "");

            LOGGER.info("Created representation metadata+content stub '{}' ({}) targetObjectId={} (from entry '{}')",
                    label, kind, targetObjectId, fileName);
            return true;

        } catch (Exception e) {
            LOGGER.warn("Failed to create representation metadata stub from ZIP entry '{}': {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the targetObjectId for a representation in the new project.
     *
     * <p>For Component Implementation Diagrams the label is
     * {@code "{ciName} - Component Implementation Diagram"}.  We extract the CI name and
     * look it up in {@code compImplNameToUUID} to get the UUID that was pre-assigned to the
     * clean Steps model.
     *
     * <p>For all other representations we apply {@code semanticIdMapping} (old -> new UUID).
     * If the old UUID is not in the map we fall back to the raw value unchanged.
     *
     * @param label              representation label from the ZIP JSON
     * @param rawTargetObjectId  targetObjectId value from the ZIP JSON (uses old UUIDs)
     * @param compImplNameToUUID CI name -> new UUID (pre-assigned to cleanSteps)
     * @param semanticIdMapping  oldUUID -> newUUID from refreshElementIds()
     * @return resolved targetObjectId for the new project
     */
    private String resolveTargetObjectId(
            String label,
            String rawTargetObjectId,
            Map<String, String> compImplNameToUUID,
            Map<String, String> semanticIdMapping) {

        // Component Implementation Diagram: "{ciName} - Component Implementation Diagram"
        if (label != null && label.endsWith(CI_DIAGRAM_SUFFIX)) {
            String ciName = label.substring(0, label.length() - CI_DIAGRAM_SUFFIX.length()).trim();
            String uuid = compImplNameToUUID.get(ciName);
            if (uuid != null) {
                LOGGER.debug("Resolved CI diagram '{}' targetObjectId via name map: {}", label, uuid);
                return uuid;
            }
            LOGGER.warn("CI diagram '{}': CI name '{}' not found in compImplNameToUUID, falling back", label, ciName);
        }

        // Composite Diagram: "{compositeName} - Composite Diagram"
        if (label != null && label.endsWith(COMPOSITE_DIAGRAM_SUFFIX)) {
            String compositeName = label.substring(0, label.length() - COMPOSITE_DIAGRAM_SUFFIX.length()).trim();
            String uuid = compImplNameToUUID.get(compositeName);
            if (uuid != null) {
                LOGGER.debug("Resolved composite diagram '{}' targetObjectId via name map: {}", label, uuid);
                return uuid;
            }
            LOGGER.warn("Composite diagram '{}': name '{}' not found in nameToUUID map, falling back", label, compositeName);
        }

        // Logical System Diagram: "{logicalSystemPrefix} - Logical System Diagram"
        if (label != null && label.endsWith(LOGICAL_SYSTEM_DIAGRAM_SUFFIX)) {
            String lsPrefix = label.substring(0, label.length() - LOGICAL_SYSTEM_DIAGRAM_SUFFIX.length()).trim();
            String uuid = compImplNameToUUID.get(lsPrefix);
            if (uuid != null) {
                LOGGER.debug("Resolved logical system diagram '{}' targetObjectId via name map: {}", label, uuid);
                return uuid;
            }
            LOGGER.warn("Logical system diagram '{}': prefix '{}' not found in nameToUUID map, falling back", label, lsPrefix);
        }

        // Fall back to semanticIdMapping for all other representations.
        return semanticIdMapping.getOrDefault(rawTargetObjectId, rawTargetObjectId);
    }

    /**
     * Builds a minimal but structurally valid Sirius Web Diagram JSON string.
     *
     * <p>The content has empty {@code nodes}, {@code edges} and {@code layoutData}
     * arrays/objects.  When the user opens the diagram the DiagramEventProcessor
     * loads this content, runs the diagram refresher over the semantic model, and
     * replaces it with the fully computed representation containing all SYNCHRONIZED
     * elements (nodes, edges, DataLink/EventLink/RequestLink connections).
     *
     * <p>Without at least a minimal content row the event processor cannot be
     * initialised, causing GraphQL null errors on nodeDescriptions /
     * dropNodeCompatibility.
     */
    private String buildMinimalDiagramContent(UUID repId, String targetObjectId,
                                              String descriptionId, String kind) {
        ObjectNode content = this.objectMapper.createObjectNode();
        content.put("id", repId.toString());
        content.put("kind", kind);
        content.put("targetObjectId", targetObjectId);
        content.put("descriptionId", descriptionId);
        content.set("nodes", this.objectMapper.createArrayNode());
        content.set("edges", this.objectMapper.createArrayNode());
        // nodeLayoutData / edgeLayoutData / labelLayoutData are stored as
        // Map<String, XxxLayoutData> in DiagramLayoutData, so they must be
        // serialized as JSON objects {} (not arrays []).
        ObjectNode layoutData = this.objectMapper.createObjectNode();
        layoutData.set("nodeLayoutData", this.objectMapper.createObjectNode());
        layoutData.set("edgeLayoutData", this.objectMapper.createObjectNode());
        layoutData.set("labelLayoutData", this.objectMapper.createObjectNode());
        content.set("layoutData", layoutData);
        return content.toString();
    }

    // -----------------------------------------------------------------------
    // ZIP parsing
    // -----------------------------------------------------------------------

    /**
     * Reads the ZIP and extracts:
     * <ul>
     *   <li>The first {@code documents/*.json} entry (Steps document)</li>
     *   <li>All {@code representations/*.json} entries</li>
     *   <li>The {@code component-code/component-code-manifest.json} bytes (if present)</li>
     *   <li>All {@code component-code/{componentId}/{version}/files.json} entries</li>
     * </ul>
     */
    ZipContents parseZip(byte[] zipBytes) throws IOException {
        byte[] stepsDocBytes = null;
        String stepsDocName = "document.json";
        byte[] ccManifestBytes = null;
        byte[] ecoaStepsBundleBytes = null;
        Map<String, byte[]> ccFiles = new HashMap<>();
        Map<String, byte[]> representations = new HashMap<>();
        Map<String, byte[]> interfaceCacheEntries = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                // Strip leading "{projectName}/" prefix
                int slash = name.indexOf('/');
                String relative = slash >= 0 ? name.substring(slash + 1) : name;

                byte[] bytes = zis.readAllBytes();

                if (relative.startsWith(DOCUMENTS_DIR) && relative.endsWith(".json")) {
                    // Take the first (and usually only) documents/*.json as the Steps doc.
                    if (stepsDocBytes == null) {
                        stepsDocBytes = bytes;
                        stepsDocName = relative.substring(DOCUMENTS_DIR.length());
                    }
                } else if (relative.startsWith(REPRESENTATIONS_DIR) && relative.endsWith(".json")) {
                    // e.g. representations/{repUUID}.json
                    String repFileName = relative.substring(REPRESENTATIONS_DIR.length());
                    representations.put(repFileName, bytes);
                } else if (relative.equals(COMPONENT_CODE_DIR + CC_MANIFEST_FILE)) {
                    ccManifestBytes = bytes;
                } else if (relative.startsWith(COMPONENT_CODE_DIR) && relative.endsWith("/files.json")) {
                    ccFiles.put(relative, bytes);
                } else if (relative.startsWith(ECOA_INTERFACE_CACHE_DIR)) {
                    // e.g. ecoa-interface-cache/svc_PingPong.interface.xml
                    String fileName = relative.substring(ECOA_INTERFACE_CACHE_DIR.length());
                    if (!fileName.isEmpty()) {
                        interfaceCacheEntries.put(fileName, bytes);
                    }
                } else if (relative.equals(ECOA_BUNDLE_ENTRY)) {
                    // Embedded full ECOA XML bundle (written by EcoaXmlProjectExportParticipant).
                    ecoaStepsBundleBytes = bytes;
                    LOGGER.info("Found embedded ECOA bundle in ZIP ({} bytes)", bytes.length);
                }

                zis.closeEntry();
            }
        }

        return new ZipContents(stepsDocBytes, stepsDocName, ccManifestBytes, ccFiles, representations, interfaceCacheEntries, ecoaStepsBundleBytes);
    }

    // -----------------------------------------------------------------------
    // Representation import (legacy - kept for reference, not called from handle())
    // -----------------------------------------------------------------------

    /**
     * Deletes all existing representations for the editing context, then recreates them
     * from the representation JSON bytes found in the ZIP.
     *
     * <p><b>UUID remapping:</b> {@code DocumentSanitizedJsonContentProvider.refreshElementIds()}
     * always regenerates every EObject's {@code IDAdapter} UUID during loading.  The
     * {@code semanticIdMapping} (oldUUID -> newUUID) must be applied to every
     * {@code targetObjectId} value so the imported representations point to the correct
     * EObjects in the new project.
     *
     * @param semanticIdMapping oldUUID -> newUUID mapping produced by {@link IUploadFileLoader#load}
     */
    private void importRepresentations(
            EdtImportSiriusWebZipInput importInput,
            IEMFEditingContext emfEditingContext,
            ZipContents zipContents,
            Map<String, String> semanticIdMapping) {

        if (zipContents.representations().isEmpty()) {
            LOGGER.info("ZIP contains no representations - skipping representation import");
            return;
        }

        try {
            UUID semanticDataId = UUID.fromString(emfEditingContext.getId());
            var semanticDataRef = AggregateReference.<SemanticData, UUID>to(semanticDataId);

            // Delete stale representations before recreating from ZIP.
            this.representationMetadataDeletionService.deleteAllRepresentationMetadata(importInput, semanticDataRef);
            LOGGER.info("Deleted all existing representations before ZIP representation import");

            for (Map.Entry<String, byte[]> entry : zipContents.representations().entrySet()) {
                importSingleRepresentation(importInput, semanticDataId, semanticDataRef,
                        entry.getKey(), entry.getValue(), semanticIdMapping);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to import representations from ZIP: {}", e.getMessage());
        }
    }

    private void importSingleRepresentation(
            EdtImportSiriusWebZipInput importInput,
            UUID semanticDataId,
            AggregateReference<SemanticData, UUID> semanticDataRef,
            String fileName,
            byte[] bytes,
            Map<String, String> semanticIdMapping) {
        try {
            JsonNode root = this.objectMapper.readTree(bytes);

            String descriptionId = root.path("descriptionId").asText(null);
            String rawTargetObjectId = root.path("targetObjectId").asText(null);
            String label = root.path("label").asText("Imported Diagram");
            String kind = root.path("kind").asText(null);
            JsonNode representationNode = root.path("representation");

            if (descriptionId == null || rawTargetObjectId == null || kind == null || representationNode.isMissingNode()) {
                LOGGER.warn("Skipping malformed representation JSON in ZIP entry '{}'", fileName);
                return;
            }

            String oldRepId = representationNode.path("id").asText(null);

            UUID newRepMetadataId = UUID.randomUUID();
            String compositeId = new RepresentationCompositeIdProvider().getId(semanticDataId, newRepMetadataId);

            String targetObjectId = semanticIdMapping.getOrDefault(rawTargetObjectId, rawTargetObjectId);

            String contentJson = this.objectMapper.writeValueAsString(representationNode);
            if (oldRepId != null && !oldRepId.isEmpty()) {
                contentJson = contentJson.replace(oldRepId, newRepMetadataId.toString());
            }
            for (Map.Entry<String, String> mapping : semanticIdMapping.entrySet()) {
                contentJson = contentJson.replace(mapping.getKey(), mapping.getValue());
            }

            try {
                contentJson = this.remapDiagramElementIds(contentJson, newRepMetadataId.toString());
            } catch (Exception e) {
                LOGGER.warn("Failed to remap diagram element IDs for '{}': {}", label, e.getMessage());
            }

            RepresentationMetadata repMetadata = RepresentationMetadata.newRepresentationMetadata(compositeId)
                    .representationMetadataId(newRepMetadataId)
                    .semanticData(semanticDataRef)
                    .kind(kind)
                    .label(label)
                    .descriptionId(descriptionId)
                    .targetObjectId(targetObjectId)
                    .iconURLs(List.of())
                    .documentation("")
                    .build(importInput);
            this.representationMetadataCreationService.create(repMetadata);

            this.representationContentCreationService.create(
                    importInput, semanticDataRef,
                    AggregateReference.to(newRepMetadataId),
                    contentJson, "", "");

            LOGGER.info("Imported representation '{}' ({}) from ZIP entry '{}' (repId: {} -> {}, targetObjectId: {} -> {})",
                    label, kind, fileName, oldRepId, newRepMetadataId, rawTargetObjectId, targetObjectId);

        } catch (Exception e) {
            LOGGER.warn("Failed to import representation from ZIP entry '{}': {}", fileName, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // ComponentCode version import
    // -----------------------------------------------------------------------

    /**
     * Imports the selected ComponentCode versions from the ZIP into the given project.
     *
     * @param projectId          the actual project entity UUID (NOT the editing-context / semantic-data UUID)
     * @param zipContents        parsed ZIP contents including the CC manifest and files
     * @param selectedVersionIds list of "{componentId}/{versionName}" keys chosen by the user
     * @param semanticIdMapping  oldUUID -> newUUID mapping produced by {@link IUploadFileLoader#load}.
     *                           The componentId values in the manifest are EObject UUIDs from the source
     *                           project.  After loading, {@code refreshElementIds()} assigns new UUIDs to
     *                           all EObjects.  We must use the new UUID when calling
     *                           {@link IComponentCodeVersionService#createPendingComponentCodeVersion} so
     *                           that the version is associated with the correct component in the target
     *                           project.
     */
    private void importSelectedVersions(UUID projectId, ZipContents zipContents, List<String> selectedVersionIds,
            Map<String, String> semanticIdMapping) {
        List<Map<String, Object>> allVersions = parseManifestVersions(zipContents.ccManifestBytes());
        if (allVersions.isEmpty()) {
            return;
        }

        for (Map<String, Object> vd : allVersions) {
            String componentId = (String) vd.get("componentId");
            String versionName = (String) vd.get("versionName");

            String versionKey = componentId + "/" + versionName;
            if (!selectedVersionIds.contains(versionKey)) {
                continue;
            }

            String filesKey = COMPONENT_CODE_DIR + componentId + "/" + versionName + "/files.json";
            byte[] filesBytes = zipContents.ccFiles().get(filesKey);
            if (filesBytes == null) {
                LOGGER.warn("files.json not found in ZIP for key '{}', skipping", filesKey);
                continue;
            }

            String newComponentId = semanticIdMapping.getOrDefault(componentId, componentId);

            String componentName = (String) vd.get("componentName");
            String commitMessage = (String) vd.get("commitMessage");
            String author = (String) vd.get("author");
            String modelVersionId = (String) vd.get("modelVersionId");
            String codeContent = new String(filesBytes, java.nio.charset.StandardCharsets.UTF_8);

            try {
                var createdVersion = this.versionService.createPendingComponentCodeVersion(
                        projectId, newComponentId,
                        componentName != null ? componentName : componentId,
                        versionName, codeContent, commitMessage,
                        author != null ? author : "imported",
                        modelVersionId);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tags = (List<Map<String, Object>>) vd.get("tags");
                if (tags != null) {
                    for (Map<String, Object> tagData : tags) {
                        importTag(projectId, createdVersion.id(), tagData);
                    }
                }
                LOGGER.info("Imported component code version {}/{} (componentId: {} -> {})",
                        newComponentId, versionName, componentId, newComponentId);
            } catch (Exception e) {
                LOGGER.warn("Failed to import version {}/{}: {}", componentId, versionName, e.getMessage());
            }
        }
    }

    private void importTag(UUID projectId, UUID versionId, Map<String, Object> tagData) {
        String tagName = (String) tagData.get("name");
        String tagColor = (String) tagData.get("color");
        if (tagName == null) return;
        try {
            var existingTags = this.tagService.getComponentCodeTags(projectId);
            UUID tagId = existingTags.stream()
                    .filter(t -> tagName.equals(t.name()))
                    .findFirst()
                    .map(t -> t.id())
                    .orElseGet(() -> this.tagService.createComponentCodeTag(
                            projectId, tagName, tagColor != null ? tagColor : "#808080").id());
            this.tagService.addTagToVersion(versionId, tagId);
        } catch (Exception e) {
            LOGGER.warn("Failed to import tag '{}' for version {}: {}", tagName, versionId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseManifestVersions(byte[] manifestBytes) {
        if (manifestBytes == null) return List.of();
        try {
            Map<String, Object> manifest = this.objectMapper.readValue(
                    manifestBytes, new TypeReference<Map<String, Object>>() { });
            Object versions = manifest.get("versions");
            if (versions instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to parse component-code-manifest.json: {}", e.getMessage());
        }
        return List.of();
    }

    // -----------------------------------------------------------------------
    // Preview (called from controller without going through event processor)
    // -----------------------------------------------------------------------

    /**
     * Parse the ZIP and return a list of available ComponentCode versions for the UI preview step.
     * Each entry is a map with keys: componentId, componentName, versionName, commitMessage, author, tags.
     */
    public List<Map<String, Object>> previewVersions(byte[] zipBytes) {
        try {
            ZipContents contents = parseZip(zipBytes);
            return parseManifestVersions(contents.ccManifestBytes());
        } catch (IOException e) {
            LOGGER.warn("Failed to parse ZIP for preview: {}", e.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Diagram element ID remapping (used by legacy importRepresentations path)
    // -----------------------------------------------------------------------

    /**
     * Re-computes node and edge hash IDs in the representation content JSON so they
     * match what Sirius Web's diagram refresher will compute from the new semantic UUIDs.
     *
     * <p>After semantic UUID replacement the {@code targetObjectId} values inside each node
     * and edge are updated to new UUIDs.  However, the element {@code id} fields (computed
     * via {@code NodeIdProvider.getNodeId} and {@code EdgeComponent.computeEdgeId}) are
     * deterministic hashes of the parent chain + description + semantic UUID.  Because the
     * semantic UUIDs changed, these hashes must also be recomputed; otherwise domain-based
     * SYNCHRONIZED edges cannot match their source/target nodes in the
     * {@code DiagramRenderingCache} and fail to render.
     *
     * @param contentJson       UUID-replaced representation content JSON string
     * @param newRepMetadataId  new representation metadata UUID (used as the diagram element's parent ID)
     * @return updated JSON string with recomputed node/edge IDs
     */
    private String remapDiagramElementIds(String contentJson, String newRepMetadataId) throws IOException {
        ObjectNode diagram = (ObjectNode) this.objectMapper.readTree(contentJson);

        Map<String, String> nodeIdMap = new HashMap<>();

        JsonNode topLevelNodes = diagram.path("nodes");
        if (topLevelNodes.isArray()) {
            this.remapNodeIds((ArrayNode) topLevelNodes, newRepMetadataId, "CHILD_NODE", nodeIdMap);
        }

        Map<String, String> edgeIdMap = new HashMap<>();
        JsonNode edgesNode = diagram.path("edges");
        if (edgesNode.isArray()) {
            Map<String, Integer> edgeCounters = new HashMap<>();
            for (JsonNode edgeJson : edgesNode) {
                if (!(edgeJson instanceof ObjectNode edge)) continue;

                String oldEdgeId = edge.path("id").asText(null);
                String oldSrc = edge.path("sourceId").asText(null);
                String oldTgt = edge.path("targetId").asText(null);
                String edgeDescId = edge.path("descriptionId").asText("");

                String newSrc = nodeIdMap.getOrDefault(oldSrc, oldSrc != null ? oldSrc : "");
                String newTgt = nodeIdMap.getOrDefault(oldTgt, oldTgt != null ? oldTgt : "");

                edge.put("sourceId", newSrc);
                edge.put("targetId", newTgt);

                String prefixKey = edgeDescId + newSrc + newTgt;
                String prefixHash = UUID.nameUUIDFromBytes(prefixKey.getBytes()).toString();
                int cnt = edgeCounters.getOrDefault(prefixHash, 0);
                edgeCounters.put(prefixHash, cnt + 1);

                String rawEdgeId = edgeDescId + ": " + newSrc + " --> " + newTgt + " - " + cnt;
                String newEdgeId = UUID.nameUUIDFromBytes(rawEdgeId.getBytes()).toString();
                edge.put("id", newEdgeId);

                if (oldEdgeId != null && !oldEdgeId.isEmpty()) {
                    edgeIdMap.put(oldEdgeId, newEdgeId);
                }
            }
        }

        JsonNode layoutData = diagram.path("layoutData");
        if (layoutData instanceof ObjectNode ld) {
            JsonNode nodeLayoutData = ld.path("nodeLayoutData");
            if (nodeLayoutData.isArray()) {
                for (JsonNode item : nodeLayoutData) {
                    if (item instanceof ObjectNode nld) {
                        String oldId = nld.path("id").asText(null);
                        if (oldId != null) {
                            nld.put("id", nodeIdMap.getOrDefault(oldId, oldId));
                        }
                    }
                }
            }
            JsonNode edgeLayoutData = ld.path("edgeLayoutData");
            if (edgeLayoutData.isArray()) {
                for (JsonNode item : edgeLayoutData) {
                    if (item instanceof ObjectNode eld) {
                        String oldId = eld.path("id").asText(null);
                        if (oldId != null) {
                            eld.put("id", edgeIdMap.getOrDefault(oldId, oldId));
                        }
                    }
                }
            }
        }

        return this.objectMapper.writeValueAsString(diagram);
    }

    /**
     * Recursively traverses a node array, recomputes each node's {@code id} using the
     * {@code NodeIdProvider} formula, and records the old-&gt;new mapping for edge remapping.
     *
     * <p>Formula (mirrors {@code NodeIdProvider.getNodeId}):
     * <pre>UUID5(parentElementId + containmentKind + nodeDescriptionId + targetObjectId)</pre>
     */
    private void remapNodeIds(ArrayNode nodes, String parentId, String containmentKind, Map<String, String> nodeIdMap) {
        for (JsonNode nodeJson : nodes) {
            if (!(nodeJson instanceof ObjectNode node)) continue;

            String oldId = node.path("id").asText(null);
            String descId = node.path("descriptionId").asText("");
            String targetObjectId = node.path("targetObjectId").asText("");

            String raw = parentId + containmentKind + descId + targetObjectId;
            String newId = UUID.nameUUIDFromBytes(raw.getBytes()).toString();

            if (oldId != null && !oldId.isEmpty()) {
                nodeIdMap.put(oldId, newId);
            }
            node.put("id", newId);

            JsonNode childNodes = node.path("childNodes");
            if (childNodes.isArray()) {
                this.remapNodeIds((ArrayNode) childNodes, newId, "CHILD_NODE", nodeIdMap);
            }

            JsonNode borderNodes = node.path("borderNodes");
            if (borderNodes.isArray()) {
                this.remapNodeIds((ArrayNode) borderNodes, newId, "BORDER_NODE", nodeIdMap);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Inner records
    // -----------------------------------------------------------------------

    record ZipContents(
            byte[] stepsDocumentBytes,
            String stepsDocumentName,
            byte[] ccManifestBytes,
            Map<String, byte[]> ccFiles,
            /** Key = "{repUUID}.json", value = raw JSON bytes of representation file. */
            Map<String, byte[]> representations,
            /** Key = fileName (e.g. "svc_PingPong.interface.xml"), value = raw XML bytes. */
            Map<String, byte[]> interfaceCacheEntries,
            /** Raw bytes of the embedded ECOA XML bundle ({@link EcoaXmlProjectExportParticipant#ECOA_BUNDLE_ENTRY}), or null if absent. */
            byte[] ecoaStepsBundleBytes) { }
}
