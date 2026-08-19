package org.eclipse.sirius.web.edt.importexport;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
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
import org.eclipse.sirius.web.domain.services.api.IMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edtproject.Steps;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * IEditingContextEventHandler that handles importing ECOA Steps ZIP into an EDT project.
 *
 * <p>
 * Strategy: full replacement of the existing Steps resource. Merging individual elements from newSteps into an existing
 * resource causes DanglingHREFException because non-containment cross-resource references (e.g. BasicType 'uint32')
 * cannot be resolved after the move. Instead we move the entire self-contained Steps root object into a new
 * JSONResource, replacing any previously imported Steps.
 *
 * <p>
 * After the replacement, emits SEMANTIC_CHANGE to trigger Sirius Web DB persistence.
 *
 * <p>
 * Additionally, raw ECOA interface XML bytes are cached in {@link EcoaInterfaceXmlCache} to work around a known
 * limitation: Sirius Web's JSON serializer cannot round-trip EMF objects whose containment reference declares an
 * INTERFACE type (OperationType). The sub-type information is lost after DB persist. The cache provides the original
 * XML as a fallback during export.
 */
@Service
public class EdtImportEcoaStepsEventHandler implements IEditingContextEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdtImportEcoaStepsEventHandler.class);

    private final EdtEcoaImportService importService;

    private final IMessageService messageService;

    private final EcoaInterfaceXmlCache interfaceXmlCache;

    private final Counter counter;

    public EdtImportEcoaStepsEventHandler(EdtEcoaImportService importService, IMessageService messageService,
            EcoaInterfaceXmlCache interfaceXmlCache, MeterRegistry meterRegistry) {
        this.importService = Objects.requireNonNull(importService);
        this.messageService = Objects.requireNonNull(messageService);
        this.interfaceXmlCache = Objects.requireNonNull(interfaceXmlCache);
        this.counter = Counter.builder(Monitoring.EVENT_HANDLER).tag(Monitoring.NAME, this.getClass().getSimpleName()).register(meterRegistry);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof EdtImportEcoaStepsInput;
    }

    @Override
    public void handle(One<IPayload> payloadSink, Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        this.counter.increment();

        IPayload payload = new ErrorPayload(input.id(), this.messageService.unexpectedError());
        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, editingContext.getId(), input);

        if (input instanceof EdtImportEcoaStepsInput importInput && editingContext instanceof IEMFEditingContext emfEditingContext) {
            try {
                // 1. Parse ZIP → Steps in a self-contained temporary ResourceSet
                Optional<Steps> optNewSteps = this.importService.parseZipToSteps(importInput.zipBytes());

                if (optNewSteps.isPresent()) {
                    Steps newSteps = optNewSteps.get();
                    var resourceSet = emfEditingContext.getDomain().getResourceSet();

                    // 2. Remove any existing Steps resource in the editing context
                    resourceSet.getResources().stream().filter(r -> !r.getContents().isEmpty() && r.getContents().get(0) instanceof Steps).findFirst().ifPresent(existing -> {
                        existing.getContents().clear();
                        resourceSet.getResources().remove(existing);
                        LOGGER.info("Removed existing Steps resource before import");
                    });

                    // 3. Create a new JSON Resource (Sirius Web persistence format) and transfer newSteps.
                    // All contained objects move with newSteps, and intra-resource non-containment
                    // references (e.g. ComponentDefinition → Library type) remain valid because both
                    // source and target objects are moved together into the same new resource.
                    var documentId = UUID.randomUUID();
                    Resource jsonResource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
                    String projectName = importInput.projectName() != null ? importInput.projectName() : "ECOA Import";
                    jsonResource.eAdapters().add(new ResourceMetadataAdapter(projectName));
                    resourceSet.getResources().add(jsonResource);

                    // Detach from the old (temp) resource before adopting into the JSON resource
                    Resource oldResource = newSteps.eResource();
                    if (oldResource != null) {
                        oldResource.getContents().remove(newSteps);
                    }
                    jsonResource.getContents().add(newSteps);

                    LOGGER.info("ECOA Steps imported as JSON resource: {}", documentId);

                    // 4. Cache raw interface XML bytes from the ZIP to work around Sirius Web JSON
                    // serializer limitations with IS_INTERFACE=true polymorphic OperationType subtypes.
                    cacheInterfaceXmls(editingContext.getId(), importInput.zipBytes());

                    changeDescription = new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId(), input);
                    payload = new SuccessPayload(input.id());
                } else {
                    payload = new ErrorPayload(input.id(), "Failed to parse ECOA Steps from ZIP");
                }
            } catch (Exception e) {
                LOGGER.error("Error during ECOA Steps import", e);
                payload = new ErrorPayload(input.id(), "Import error: " + e.getMessage());
            }
        }

        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }

    /**
     * Extracts {@code 1-Services/*.interface.xml} and {@code 4-ComponentImplementations/*.impl.xml}
     * entries from the ZIP and stores them in the cache.
     * <p>
     * This covers two Sirius Web JSON serialization losses:
     * 1. IS_INTERFACE=true OperationType subtypes (Event/Data/RequestResponse) → interface.xml
     * 2. PropertyValue.value EAttribute → impl.xml
     */
    private void cacheInterfaceXmls(String editingContextId, byte[] zipBytes) {
        this.interfaceXmlCache.clear(editingContextId);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory()) {
                    if (name.contains("1-Services/") && name.endsWith(".interface.xml")) {
                        String fileName = name.substring(name.lastIndexOf('/') + 1);
                        byte[] bytes = zis.readAllBytes();
                        this.interfaceXmlCache.store(editingContextId, fileName, bytes);
                        LOGGER.info("Cached interface XML '{}' ({} bytes) for editing context {}", fileName, bytes.length, editingContextId);
                    } else if (name.contains("4-ComponentImplementations/") && name.endsWith(".impl.xml")) {
                        String fileName = name.substring(name.lastIndexOf('/') + 1);
                        byte[] bytes = zis.readAllBytes();
                        this.interfaceXmlCache.store(editingContextId, fileName, bytes);
                        LOGGER.info("Cached impl XML '{}' ({} bytes) for editing context {}", fileName, bytes.length, editingContextId);
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to cache XML bytes from ZIP", e);
        }
    }
}
