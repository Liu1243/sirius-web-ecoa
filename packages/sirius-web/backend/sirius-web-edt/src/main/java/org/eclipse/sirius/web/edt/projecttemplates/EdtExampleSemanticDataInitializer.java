/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
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
package org.eclipse.sirius.web.edt.projecttemplates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextPersistenceService;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.events.ICause;
import org.eclipse.sirius.web.application.editingcontext.services.api.IResourceLoader;
import org.eclipse.sirius.web.application.project.services.api.ISemanticDataInitializer;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.events.ProjectSemanticDataCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.events.SemanticDataCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edtproject.EDTProjectFactory;
import edttype.util.EDTTypeDefaultCreator;

/**
 * Used to create the EDT example project with sample data.
 *
 * @author EDT Team
 */
@Service
public class EdtExampleSemanticDataInitializer implements ISemanticDataInitializer {

    private static final String EXAMPLE_DOCUMENT_NAME = "EDT Project";

    private final Logger logger = LoggerFactory.getLogger(EdtExampleSemanticDataInitializer.class);

    private final IEditingContextPersistenceService editingContextPersistenceService;

    private final IResourceLoader resourceLoader;

    public EdtExampleSemanticDataInitializer(IEditingContextPersistenceService editingContextPersistenceService, IResourceLoader resourceLoader) {
        this.editingContextPersistenceService = Objects.requireNonNull(editingContextPersistenceService);
        this.resourceLoader = Objects.requireNonNull(resourceLoader);
    }

    @Override
    public boolean canHandle(String projectTemplateId) {
        return EdtProjectTemplateProvider.EXAMPLE_PROJECT_TEMPLATE_ID.equals(projectTemplateId);
    }

    @Override
    public void handle(ICause cause, IEditingContext editingContext, String projectTemplateId) {
        if (editingContext instanceof IEMFEditingContext emfEditingContext) {
            String projectName = EXAMPLE_DOCUMENT_NAME;
            if (cause instanceof ProjectSemanticDataCreatedEvent projectSemanticDataCreatedEvent
                    && projectSemanticDataCreatedEvent.causedBy() instanceof SemanticDataCreatedEvent semanticDataCreatedEvent
                    && semanticDataCreatedEvent.causedBy() instanceof ProjectCreatedEvent projectCreatedEvent) {
                projectName = projectCreatedEvent.project().getName();
            }

            var documentId = UUID.randomUUID();
            var resourceSet = emfEditingContext.getDomain().getResourceSet();

            // Create the main resource
            var resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
            var resourceMetadataAdapter = new ResourceMetadataAdapter(projectName);
            resource.eAdapters().add(resourceMetadataAdapter);
            resourceSet.getResources().add(resource);

            try {
                // Parse the full JSON content
                String exampleContent = this.getExampleDocumentContent();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(exampleContent);

                // Get namespaces for fragment loading
                JsonNode nsNode = rootNode.get("ns");
                String nsJson = nsNode != null ? nsNode.toString() : "{}";

                // Get the Steps content array from JSON
                JsonNode contentArray = rootNode.get("content");
                if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                    JsonNode stepsNode = contentArray.get(0);
                    JsonNode stepsDataNode = stepsNode.get("data");

                    if (stepsDataNode != null) {
                        JsonNode stepArray = stepsDataNode.get("Step");

                        // Create the root Steps object
                        var einstance = EDTProjectFactory.eINSTANCE;
                        var steps = einstance.createSteps();
                        resource.getContents().add(steps);

                        if (stepArray != null && stepArray.isArray()) {
                            for (JsonNode stepNode : stepArray) {
                                String eClass = stepNode.get("eClass").asText();
                                EObject stepObject = null;

                                // Create specific step based on eClass or index
                                if (eClass.endsWith("Step0")) {
                                    stepObject = einstance.createStep0();
                                } else if (eClass.endsWith("Step1")) {
                                    stepObject = einstance.createStep1();
                                } else if (eClass.endsWith("Step2")) {
                                    stepObject = einstance.createStep2();
                                } else if (eClass.endsWith("Step3")) {
                                    stepObject = einstance.createStep3();
                                } else if (eClass.endsWith("Step4")) {
                                    stepObject = einstance.createStep4();
                                } else if (eClass.endsWith("Step5")) {
                                    stepObject = einstance.createStep5();
                                }

                                if (stepObject != null) {
                                    populateStep(stepObject, stepNode.get("data"), nsJson);
                                    steps.getStep().add((edtproject.Step) stepObject);
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                this.logger.error("Failed to parse and load example EDT project JSON", e);
                // Fallback to empty project in case of error
                resource.getContents().clear();
                this.createEmptyProject(emfEditingContext, documentId, projectName);
            }

            this.editingContextPersistenceService.persist(cause, editingContext);
        }
    }

    /**
     * Populates a Step object with data from the JSON node. Uses EMF reflection to set features. Complex containment
     * features are loaded by creating a temporary JSON loading context.
     */
    private void populateStep(EObject step, JsonNode dataNode, String nsJson) throws IOException {
        if (dataNode == null)
            return;

        var eClass = step.eClass();
        Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();

        while (fields.hasNext()) {
            var field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            EStructuralFeature structuralFeature = eClass.getEStructuralFeature(fieldName);
            if (structuralFeature == null && fieldName != null && !fieldName.isEmpty()) {
                // Try lowercase if exact match fails (e.g. "Types" vs "types")
                String uncapitalized = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                structuralFeature = eClass.getEStructuralFeature(uncapitalized);
            }

            if (structuralFeature != null && !structuralFeature.isDerived() && structuralFeature.isChangeable()) {
                if (fieldValue.isValueNode()) {
                    if (fieldValue.isTextual()) {
                        step.eSet(structuralFeature, fieldValue.asText());
                    } else if (fieldValue.isBoolean()) {
                        step.eSet(structuralFeature, fieldValue.asBoolean());
                    } else if (fieldValue.isInt()) {
                        step.eSet(structuralFeature, fieldValue.asInt());
                    }
                } else if (fieldValue.isArray() || fieldValue.isObject()) {
                    // Complex content: extract, wrap as full JSON, load as resource fragment
                    if (structuralFeature instanceof EReference reference && reference.isContainment()) {
                        String fragmentJson = "{\"json\":{\"version\":\"1.0\",\"encoding\":\"utf-8\"},\"ns\":" + nsJson + ",\"content\":" + fieldValue.toString() + "}";

                        // Load fragment
                        var fragmentResource = new JSONResourceFactory().createResourceFromPath(UUID.randomUUID().toString());
                        try (var is = new ByteArrayInputStream(fragmentJson.getBytes(StandardCharsets.UTF_8))) {
                            fragmentResource.load(is, Collections.emptyMap());

                            // Move contents to step
                            if (structuralFeature.isMany()) {
                                @SuppressWarnings("unchecked")
                                List<Object> targetList = (List<Object>) step.eGet(structuralFeature);
                                targetList.addAll(fragmentResource.getContents());
                            } else if (!fragmentResource.getContents().isEmpty()) {
                                step.eSet(structuralFeature, fragmentResource.getContents().get(0));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the hardcoded example document content from the "EDT - example" database. This content was extracted from
     * the database and hardcoded here to avoid SQL queries at runtime.
     */
    private String getExampleDocumentContent() {
        return """
                {"json":{"version":"1.0","encoding":"utf-8"},"ns":{"EDTProject2":"EDTProject2","EDTService":"EDTService","EDTTypes":"EDTTypes","edtdeployment":"edtdeployment","edtimplementation":"edtimplementation","edtlogical":"edtlogical"},"migration":{"lastMigrationPerformed":"NodeDescriptionLayoutStrategyMigrationParticipant","migrationVersion":"2025.6.0-202506011000"},"content":[{"id":"b039a7f8-90fd-4695-aa1e-e4538270f8ed","eClass":"EDTProject2:Steps","data":{"Step":[{"id":"47a862ab-4e0b-4334-b3db-cf576ab0b3d9","eClass":"EDTProject2:Step0","data":{"FolderName":"0-Types","Types":[{"id":"8fc77723-7007-452f-808b-c05851556550","eClass":"EDTTypes:Library","data":{"DataTypes":[{"id":"7fa894f2-2161-4da3-87bd-58913305aa82","eClass":"EDTTypes:Record","data":{"Name":"vector_data","field":[{"id":"772fb4c6-752b-4733-8efe-41e1dcf227af","eClass":"EDTTypes:Field","data":{"name":"nb_x","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"bc73de70-bbc0-4e53-a276-0150e9344853","eClass":"EDTTypes:Field","data":{"name":"nb_y","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"b915468e-e874-485f-b5a7-fab93b58a6e2","eClass":"EDTTypes:Field","data":{"name":"writer_id","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}}]}}],"Name":"VD_lib"}}],"BasicTypes":[{"id":"852e01b3-37dc-454d-960a-ff2c85f531bc","eClass":"EDTTypes:BasicType","data":{"Name":"boolean8"}},{"id":"eb4efe53-2a08-4241-8cf4-0ea32af08de3","eClass":"EDTTypes:BasicType","data":{"Name":"int8"}},{"id":"9bedb381-8e28-4932-801d-250cc0896082","eClass":"EDTTypes:BasicType","data":{"Name":"int16"}},{"id":"92e77ade-26eb-4704-b84b-acca8e3024e9","eClass":"EDTTypes:BasicType","data":{"Name":"int32"}},{"id":"0a5627bb-d143-47fb-9674-5c62a008a28b","eClass":"EDTTypes:BasicType","data":{"Name":"int64"}},{"id":"aca1de22-0f95-4231-90e2-12d7154bcd88","eClass":"EDTTypes:BasicType","data":{"Name":"uint8"}},{"id":"7793bead-6edf-466e-bfce-f60764125279","eClass":"EDTTypes:BasicType","data":{"Name":"uint16"}},{"id":"8675e0ae-7380-4b82-abbf-6fe490124eae","eClass":"EDTTypes:BasicType","data":{"Name":"uint32"}},{"id":"b784f9af-ce87-4274-a0cb-2b6d1ca9e442","eClass":"EDTTypes:BasicType","data":{"Name":"uint64"}},{"id":"8a1ce849-20c7-4205-95b5-d0f5dd3fc397","eClass":"EDTTypes:BasicType","data":{"Name":"char8"}},{"id":"c099b986-6b45-4f90-8b75-cdbad4867971","eClass":"EDTTypes:BasicType","data":{"Name":"byte"}},{"id":"e13daf60-ecd8-494e-a5ab-9e8b74b1befb","eClass":"EDTTypes:BasicType","data":{"Name":"float32"}},{"id":"7291611a-2fc5-4f9a-b3f3-3bf5ca715586","eClass":"EDTTypes:BasicType","data":{"Name":"double64"}}],"EcoaPredefinedTypes":[{"id":"c5723124-f1c7-467b-b139-4749eeb18343","eClass":"EDTTypes:RecordPredefined","data":{"Name":"hr_time","field":[{"id":"acf7137a-8c05-42bc-9858-e7f64b4358fd","eClass":"EDTTypes:FieldPredefined","data":{"name":"seconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"2a629973-73b3-4d3b-a643-4dee5664fcc7","eClass":"EDTTypes:FieldPredefined","data":{"name":"nanoseconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}}]}},{"id":"5ef5b8a2-6c6f-4e27-809c-216a40f28669","eClass":"EDTTypes:RecordPredefined","data":{"Name":"global_time","field":[{"id":"bd3e4246-37ba-405f-b968-ed5de56c76c9","eClass":"EDTTypes:FieldPredefined","data":{"name":"seconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"59e2edd4-e382-499e-8b4c-20ed8d1c4b03","eClass":"EDTTypes:FieldPredefined","data":{"name":"nanoseconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}}]}},{"id":"895354ab-5f2e-47d0-b388-b659dfb253f0","eClass":"EDTTypes:RecordPredefined","data":{"Name":"duration","field":[{"id":"de2e5513-44ff-4322-92fb-7a954950396f","eClass":"EDTTypes:FieldPredefined","data":{"name":"seconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"13d641ed-1646-44fd-8073-487ce174e46d","eClass":"EDTTypes:FieldPredefined","data":{"name":"nanoseconds","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}}]}},{"id":"898ab5ca-6961-4eea-b7fc-f294d9189cb4","eClass":"EDTTypes:ArrayPredefined","data":{"Name":"log","maxNumber":"256","ItemType":"8a1ce849-20c7-4205-95b5-d0f5dd3fc397"}},{"id":"f643b630-d7d8-484e-b329-33a51f284c2d","eClass":"EDTTypes:ArrayPredefined","data":{"Name":"pinfo_filename","maxNumber":"256","ItemType":"8a1ce849-20c7-4205-95b5-d0f5dd3fc397"}},{"id":"78645134-3130-4a5b-9c3c-e83a36258e79","eClass":"EDTTypes:SimplePredefined","data":{"Name":"error_id","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"9072f9ce-b05b-44a1-9e20-82a515e6cc2b","eClass":"EDTTypes:SimplePredefined","data":{"Name":"error_code","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"e42de95d-3254-4865-9802-324e30ec062b","eClass":"EDTTypes:SimplePredefined","data":{"Name":"asset_id","Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"95618cd4-5b2a-43a7-922a-a2c597a18ef8","eClass":"EDTTypes:EnumPredefined","data":{"Name":"asset_type","value":[{"id":"c6eb46c5-386e-4757-a684-ee28f70a7121","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"COMPONENT","valnum":"0"}},{"id":"34e03211-d089-4d7c-a6bb-19c4c14737ed","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"PROTECTION_DOMAIN","valnum":"1"}},{"id":"694bf54e-fe32-4519-acff-4282b0b36c6a","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"NODE","valnum":"2"}},{"id":"ff976e0b-af85-45c0-b648-a7c2e0f83420","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"PLATFORM","valnum":"3"}},{"id":"f0e050a7-51a9-4c69-85e0-f1309e8e2006","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"SERVICE","valnum":"4"}},{"id":"ea6d4315-00b7-4858-8f9f-2a4752f12152","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"DEPLOYMENT","valnum":"5"}}],"Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"341beaf4-c314-4a89-a75c-8e0e958dae2a","eClass":"EDTTypes:EnumPredefined","data":{"Name":"error_type","value":[{"id":"29c30c24-b947-4f86-9158-714d8c17a9f1","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"RESOURCE_NOT_AVAILABLE","valnum":"0"}},{"id":"ca27a6f0-bb1a-4613-bb3d-b03ba878f655","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"UNAVAILABLE","valnum":"1"}},{"id":"e93f5963-630d-4d07-8840-fd4c8fcdc3ea","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"MEMORY_VIOLATION","valnum":"2"}},{"id":"c852ed47-fe16-48a4-8ff1-ad51e118086b","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"NUMERICAL_ERROR","valnum":"3"}},{"id":"3d7ddf1d-3da2-45ce-8ea5-dce79a2349e9","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"ILLEGAL_INSTRUCTION","valnum":"4"}},{"id":"d4351bd9-4588-4829-bd76-a66db3fe29d9","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"STACK_OVERFLOW","valnum":"5"}},{"id":"22c668d1-e9d3-4f11-876e-8b81d00801ac","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"DEADLINE_VIOLATION","valnum":"6"}},{"id":"7a289413-1df6-4f28-bdff-b47069093ccd","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OVERFLOW","valnum":"7"}},{"id":"6cb3b800-0c4e-4e5e-841a-6c69b8cf01ff","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"UNDERFLOW","valnum":"8"}},{"id":"40bb28c8-b127-453c-a4a8-2c3776a4f548","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"ILLEGAL_INPUT_ARGS","valnum":"9"}},{"id":"dabe8835-5689-4192-b600-6d53d8930c2e","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"ILLEGAL_OUTPUT_ARGS","valnum":"10"}},{"id":"34864ee8-c294-4a25-b9aa-f58bc913e434","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"ERROR","valnum":"11"}},{"id":"7a7f00a0-ce6b-468e-b321-3f4e1f2a1e27","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"FATAL_ERROR","valnum":"12"}},{"id":"0fc99493-6e16-4e1a-807c-af6ed3fc9704","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"HARDWARE_FAULT","valnum":"13"}},{"id":"e5be2951-ca9f-4976-88f7-c67297f0d393","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"POWER_FAIL","valnum":"14"}},{"id":"affe68d8-78be-4839-ac97-c26a07cba47d","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"COMMUNICATION_ERROR","valnum":"15"}},{"id":"7416760b-0232-40e3-8d0f-36d18f28f82e","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"INVALID_CONFIG","valnum":"16"}},{"id":"e454e6be-28d6-45d1-a041-e824ee1d90e9","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"INITIALISATION_PROBLEM","valnum":"17"}},{"id":"d3523f73-cf29-4e33-9598-02aff04d3f08","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"CLOCK_UNSYNCHRONIZED","valnum":"18"}},{"id":"8b13d5d3-d287-4828-b939-bb5b60e3b4de","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"UNKNOWN_OPERATION","valnum":"19"}},{"id":"49ba7dea-4d16-407e-af9b-ea47595dfa40","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OPERATION_OVERRATED","valnum":"20"}},{"id":"f31f1969-790a-43e6-b372-828020cd4c36","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OPERATION_UNDERRATED","valnum":"21"}}],"Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"4c62b83f-e9de-46b9-8318-fb1e17c0bcb7","eClass":"EDTTypes:EnumPredefined","data":{"Name":"recovery_action_type","value":[{"id":"ac4d1fac-34b1-44c0-a533-c8120827a77a","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"SHUTDOWN","valnum":"0"}},{"id":"a6bbb45a-0a31-4727-82e7-6f47c9d36e24","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"COLD_RESTART","valnum":"1"}},{"id":"b91dab7b-f60d-42ff-af73-9af06226d812","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"WARM_RESTART","valnum":"2"}},{"id":"783cde28-ada4-4ded-a750-dd26584d0fa5","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"CHANGE_DEPLOYMENT","valnum":"3"}}],"Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"40344bde-fbff-48a7-86d8-439a1afa423f","eClass":"EDTTypes:EnumPredefined","data":{"Name":"seek_whence_type","value":[{"id":"b6336282-24d2-4b6f-a6d7-bd0398ae6d04","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"SEEK_SET","valnum":"0"}},{"id":"ea0d88ee-aede-410b-98d1-1d46b26a758c","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"SEEK_CUR","valnum":"1"}},{"id":"cbadb8e4-0f1e-46ed-bdee-d03ba1b6e61a","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"SEEK_END","valnum":"2"}}],"Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}},{"id":"50feecf8-fb1d-4821-886a-62385ec4bd2d","eClass":"EDTTypes:EnumPredefined","data":{"Name":"return_status","value":[{"id":"132b8e79-c0b9-43ec-80a7-fe6bd1a98b0f","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OK","valnum":"0"}},{"id":"815431a0-6e7f-4c7c-8c55-1ea8ef43f727","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"INVALID_HANDLE","valnum":"1"}},{"id":"f4b7d96e-8cce-4473-a5d0-a14442b1ea8c","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"DATA_NOT_INITIALIZED","valnum":"2"}},{"id":"c75ca77b-91ea-4840-8ba5-77056537c6a3","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"NO_DATA","valnum":"3"}},{"id":"6bca3259-7aff-4639-a2e8-930629c6e42d","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"INVALID_IDENTIFIER","valnum":"4"}},{"id":"7ab439bc-d1b5-4b01-a36a-317e23492c3b","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"NO_RESPONSE","valnum":"5"}},{"id":"aef242ee-292e-4808-b2a6-034973006b28","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OPERATION_ALREADY_PENDING","valnum":"6"}},{"id":"c01c1e04-7b15-4d0b-bd8d-0c22cc4e2918","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"CLOCK_UNSYNCHRONIZED","valnum":"7"}},{"id":"27af1190-a7e9-4608-829a-3daa117f8de7","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"RESOURCE_NOT_AVAILABLE","valnum":"8"}},{"id":"bfa283aa-cd9e-4172-9fa0-5d1639506c3b","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"OPERATION_NOT_AVAILABLE","valnum":"9"}},{"id":"546f3c93-4bee-4907-9eed-f65601c5a665","eClass":"EDTTypes:EnumValuePredefined","data":{"name":"INVALID_PARAMETER","valnum":"10"}}],"Type":"8675e0ae-7380-4b82-abbf-6fe490124eae"}}]}},{"id":"abbbbeb3-989f-44b7-a1d7-80e61b06b6ef","eClass":"EDTProject2:Step1","data":{"FolderName":"1-Services","Services":[{"id":"b4f15fb6-174b-44c1-bcf3-77865f8d13db","eClass":"EDTService:ServiceDefinition","data":{"Name":"svc_VD","usedLibraries":["8fc77723-7007-452f-808b-c05851556550"]}},{"id":"6928ac7c-abdf-44bc-b1af-22e365b34cbe","eClass":"EDTService:ServiceDefinition","data":{"Name":"svc_finish"}}]}},{"id":"510988d2-acdf-4f5e-8a35-8b32f65961a6","eClass":"EDTProject2:Step2","data":{"FolderName":"2-ComponentDefinitions","ComponentDefinitions":[{"id":"347fcbbf-439f-4302-8668-036aae0b3170","eClass":"EDTProject2:ComponentDefinition","data":{"Name":"Writer","References":[{"id":"a1d0c5f6-dafc-4d61-b9c2-4c30954724de","eClass":"EDTProject2:ComponentDefinitionReference","data":{"Syntax":"6928ac7c-abdf-44bc-b1af-22e365b34cbe","name":"svc_finish"}}],"Services":[{"id":"f21cf2b9-bff9-4180-b787-9889741bf398","eClass":"EDTProject2:ComponentDefinitionService","data":{"Syntax":"b4f15fb6-174b-44c1-bcf3-77865f8d13db","name":"svc_writer"}}]}},{"id":"ed4f28b8-de76-4116-8b00-00a1f8de3202","eClass":"EDTProject2:ComponentDefinition","data":{"Name":"Finisher","Services":[{"id":"22ba5823-6227-4347-a579-388072cd66ca","eClass":"EDTProject2:ComponentDefinitionService","data":{"Syntax":"6928ac7c-abdf-44bc-b1af-22e365b34cbe","name":"finish_interface"}}]}},{"id":"11ca4855-35ad-407f-9ff1-88a6da30f5f0","eClass":"EDTProject2:ComponentDefinition","data":{"Name":"Reader","References":[{"id":"5240ad32-4b85-452a-ba06-ca87b3a6c006","eClass":"EDTProject2:ComponentDefinitionReference","data":{"Syntax":"b4f15fb6-174b-44c1-bcf3-77865f8d13db","name":"svc_reader"}},{"id":"4b4a4630-63ac-4b78-bf8d-9823f89c3ea1","eClass":"EDTProject2:ComponentDefinitionReference","data":{"Syntax":"6928ac7c-abdf-44bc-b1af-22e365b34cbe","name":"svc_finish"}}]}}]}},{"id":"e720ad0c-2afb-45d6-8b96-43c02e2c9a56","eClass":"EDTProject2:Step3","data":{"FolderName":"3-InitialAssembly","InitialAssembly":{"id":"07def44a-dc49-421e-9d0e-686949cc8c2d","eClass":"EDTProject2:Composite","data":{"Components":[{"id":"90bbabf1-4108-40d9-ab6d-a925c74a629d","eClass":"EDTProject2:Component","data":{"ComponentDefinition":"11ca4855-35ad-407f-9ff1-88a6da30f5f0","ComponentReferences":[{"id":"9b1796c5-9d66-4a76-8a3f-8ff09fa00809","eClass":"EDTProject2:ComponentReference","data":{"ComponentDefinitionReference":"5240ad32-4b85-452a-ba06-ca87b3a6c006","Name":"svc_reader"}},{"id":"2efa88cc-84d2-40e4-922a-36d058840616","eClass":"EDTProject2:ComponentReference","data":{"ComponentDefinitionReference":"4b4a4630-63ac-4b78-bf8d-9823f89c3ea1","Name":"svc_finish"}}],"Name":"compReader1","ComponentImplementation":"e35a9dc3-e862-4a52-b154-98657c0f7145"}},{"id":"56eee468-881e-4cfc-a690-c195094e99b2","eClass":"EDTProject2:Component","data":{"ComponentDefinition":"11ca4855-35ad-407f-9ff1-88a6da30f5f0","ComponentReferences":[{"id":"396c60bc-a827-495a-8c55-b7254e777f1a","eClass":"EDTProject2:ComponentReference","data":{"ComponentDefinitionReference":"5240ad32-4b85-452a-ba06-ca87b3a6c006","Name":"svc_reader"}},{"id":"6c37743d-5434-4958-b3df-5f04258f0b16","eClass":"EDTProject2:ComponentReference","data":{"ComponentDefinitionReference":"4b4a4630-63ac-4b78-bf8d-9823f89c3ea1","Name":"svc_finish"}}],"Name":"compReader0","ComponentImplementation":"e35a9dc3-e862-4a52-b154-98657c0f7145"}},{"id":"9978e9d3-76ff-4b20-aefd-bf3e6034e29e","eClass":"EDTProject2:Component","data":{"ComponentDefinition":"347fcbbf-439f-4302-8668-036aae0b3170","ComponentReferences":[{"id":"fe59d34d-8610-4fe1-ac73-b7a9cc528aff","eClass":"EDTProject2:ComponentReference","data":{"ComponentDefinitionReference":"a1d0c5f6-dafc-4d61-b9c2-4c30954724de","Name":"svc_finish"}}],"ComponentServices":[{"id":"d4e2eb46-26af-4422-97d5-26a029cf0150","eClass":"EDTProject2:ComponentService","data":{"ComponentDefinitionService":"f21cf2b9-bff9-4180-b787-9889741bf398","Name":"svc_writer"}}],"Name":"compWriter","ComponentImplementation":"b29e8408-58a1-445c-9af6-69617addc62f"}},{"id":"7b0d8fca-a831-4a3f-b5b8-c25d8cac2710","eClass":"EDTProject2:Component","data":{"ComponentDefinition":"ed4f28b8-de76-4116-8b00-00a1f8de3202","ComponentServices":[{"id":"4264fe9f-d1ed-407b-aa65-4f4088ba59f5","eClass":"EDTProject2:ComponentService","data":{"ComponentDefinitionService":"22ba5823-6227-4347-a579-388072cd66ca","Name":"finish_interface"}}],"Name":"compFinisher","ComponentImplementation":"b6f20d35-66bc-4b26-ac03-94916ea02e83"}}],"Name":"demo","targetNamespace":"http://www.ecoa.technology/sca-extension-2.0","ServiceLinks":[{"id":"d00ce908-81a0-421c-a906-92755f214597","eClass":"EDTProject2:ServiceLink","data":{"source":"9a9151a5-596e-421b-b163-786734267be7","target":"1ac67187-9612-43cb-83a7-fcfee21243ee"}},{"id":"69443dde-f967-44d2-b494-3388c738d929","eClass":"EDTProject2:ServiceLink","data":{"source":"13f5b5bf-400a-482b-bccd-58ef292119df","target":"1ac67187-9612-43cb-83a7-fcfee21243ee"}},{"id":"7f6baae3-fa2c-4091-8083-7cdf4fac7234","eClass":"EDTProject2:ServiceLink","data":{"source":"73cde9f2-618d-430c-8ae3-e8b9f416eab0","target":"85aded12-2f5d-41b1-8c47-4e5121a1dbb3"}},{"id":"eb194618-9eb3-4e4d-9c97-6eeafaee7b9e","eClass":"EDTProject2:ServiceLink","data":{"source":"ecd9c524-2dd3-453d-b429-bd3fb24c5a64","target":"85aded12-2f5d-41b1-8c47-4e5121a1dbb3"}},{"id":"f627579d-6d94-43aa-a103-9f9570ae27dc","eClass":"EDTProject2:ServiceLink","data":{"source":"02b1b0e5-b600-4982-9090-aa0f2a883750","target":"85aded12-2f5d-41b1-8c47-4e5121a1dbb3"}},{"id":"ace844d5-2513-4d10-b2b8-189c22d67f3d","eClass":"EDTProject2:ServiceLink","data":{"source":"9b1796c5-9d66-4a76-8a3f-8ff09fa00809","target":"d4e2eb46-26af-4422-97d5-26a029cf0150"}},{"id":"5ac669c3-c3fa-4411-aaf6-58214e1698b3","eClass":"EDTProject2:ServiceLink","data":{"source":"396c60bc-a827-495a-8c55-b7254e777f1a","target":"d4e2eb46-26af-4422-97d5-26a029cf0150"}},{"id":"25d8e71d-599d-456e-a54e-46a857f55ef6","eClass":"EDTProject2:ServiceLink","data":{"source":"2efa88cc-84d2-40e4-922a-36d058840616","target":"4264fe9f-d1ed-407b-aa65-4f4088ba59f5"}},{"id":"a7fd58dc-f66f-4a47-b8c0-7619e88d059f","eClass":"EDTProject2:ServiceLink","data":{"source":"fe59d34d-8610-4fe1-ac73-b7a9cc528aff","target":"4264fe9f-d1ed-407b-aa65-4f4088ba59f5"}},{"id":"e4acc7d1-a00b-48fa-a81b-acacdd924322","eClass":"EDTProject2:ServiceLink","data":{"source":"6c37743d-5434-4958-b3df-5f04258f0b16","target":"4264fe9f-d1ed-407b-aa65-4f4088ba59f5"}}]}}}},{"id":"0908f10b-dc77-46a1-87d1-ba998f0644d5","eClass":"EDTProject2:Step4","data":{"FolderName":"4-ComponentImplementations","ComponentImplementations":[{"id":"b6f20d35-66bc-4b26-ac03-94916ea02e83","eClass":"edtimplementation:ComponentImplementation","data":{"name":"mycompFinisher","ModuleTypes":[{"id":"b44f7faa-4f0e-4957-83df-4c4de2ecf8fc","eClass":"edtimplementation:ModuleType","data":{"name":"myCompFinisher_mod_t"}}],"moduleImplementations":[{"id":"02d0cc24-a6bd-456a-9a63-a88fe572a760","eClass":"edtimplementation:ModuleImplementation","data":{"name":"myCompFinisher_mod","moduleType":"b44f7faa-4f0e-4957-83df-4c4de2ecf8fc"}}],"instances":[{"id":"86760d50-9352-470c-869c-20f0c86855b2","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompFinisher_mod_inst","relativePriority":"100","moduleType":"b44f7faa-4f0e-4957-83df-4c4de2ecf8fc","moduleImplementation":"02d0cc24-a6bd-456a-9a63-a88fe572a760"}}]}},{"id":"b29e8408-58a1-445c-9af6-69617addc62f","eClass":"edtimplementation:ComponentImplementation","data":{"name":"mycompWriter","ModuleTypes":[{"id":"7da7836d-c0a6-46f8-b540-67f924861004","eClass":"edtimplementation:ModuleType","data":{"name":"myCompWriter_mod_t0"}},{"id":"8d3d8fbb-4f74-495a-b685-4d4fb2cbfe30","eClass":"edtimplementation:ModuleType","data":{"name":"myCompWriter_write_only_t"}}],"moduleImplementations":[{"id":"7de653c2-89a2-4a14-9bd1-a152f6f56fd6","eClass":"edtimplementation:ModuleImplementation","data":{"name":"myCompWriter_mod0","moduleType":"7da7836d-c0a6-46f8-b540-67f924861004"}},{"id":"df79de3b-a359-4a2f-9ecd-4614c4b2ba24","eClass":"edtimplementation:ModuleImplementation","data":{"name":"myCompWriter_write_only","moduleType":"8d3d8fbb-4f74-495a-b685-4d4fb2cbfe30"}}],"instances":[{"id":"5859d081-ce75-4b1f-b6d5-b6834f10a256","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompWriter_mod_inst0","relativePriority":"100","moduleType":"7da7836d-c0a6-46f8-b540-67f924861004","moduleImplementation":"7de653c2-89a2-4a14-9bd1-a152f6f56fd6"}},{"id":"487bfc8a-5ba1-453f-8e57-7a8426f814d6","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompWriter_mod_inst1","relativePriority":"100","moduleType":"7da7836d-c0a6-46f8-b540-67f924861004","moduleImplementation":"7de653c2-89a2-4a14-9bd1-a152f6f56fd6"}},{"id":"980912e4-ecfd-461c-b1fa-6e5d92698c68","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompWriter_write_only_inst","relativePriority":"100","moduleType":"8d3d8fbb-4f74-495a-b685-4d4fb2cbfe30","moduleImplementation":"df79de3b-a359-4a2f-9ecd-4614c4b2ba24"}}]}},{"id":"e35a9dc3-e862-4a52-b154-98657c0f7145","eClass":"edtimplementation:ComponentImplementation","data":{"name":"mycompReader","ModuleTypes":[{"id":"c361953a-0a23-4037-ac9b-26ef1c5e23ac","eClass":"edtimplementation:ModuleType","data":{"name":"myCompReader_mod_t"}}],"moduleImplementations":[{"id":"5b4bb45c-e3dd-47b0-b8b0-53313755387c","eClass":"edtimplementation:ModuleImplementation","data":{"name":"myCompReader_mod","moduleType":"c361953a-0a23-4037-ac9b-26ef1c5e23ac"}}],"instances":[{"id":"dfc04147-e5cc-4fe7-9b06-a6bfc736178b","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompReader_mod_inst0","relativePriority":"100","moduleType":"c361953a-0a23-4037-ac9b-26ef1c5e23ac","moduleImplementation":"5b4bb45c-e3dd-47b0-b8b0-53313755387c"}},{"id":"00e8d427-e35a-4111-b7b1-a3c90f4a1ea6","eClass":"edtimplementation:ModuleInstance","data":{"name":"myCompReader_mod_inst1","relativePriority":"100","moduleType":"c361953a-0a23-4037-ac9b-26ef1c5e23ac","moduleImplementation":"5b4bb45c-e3dd-47b0-b8b0-53313755387c"}}]}}]}},{"id":"ab312a5b-9d2d-49af-bad1-d583f20ddb88","eClass":"EDTProject2:Step5","data":{"FolderName":"5-Integration","LogicalSystem":{"id":"7f3419f4-d2fb-4c9b-bc56-f794af2361f7","eClass":"edtlogical:LogicalSystem","data":{"FileNamePrefix":"cs1","logicalComputingPlatforms":[{"id":"e3885cc1-2c3b-4d4e-b9e8-2039dfc13459","eClass":"edtlogical:LogicalComputingPlatform","data":{"id":"Dassault"}}],"id":"cs1"}},"Deployment":{"id":"e0b24ded-5d01-4b4d-98fc-6bb2dc980eb3","eClass":"edtdeployment:Deployment","data":{"Name":"demo","logicalSystem":"7f3419f4-d2fb-4c9b-bc56-f794af2361f7","finalAssembly":"81a7b2bc-4877-444b-bb90-c1b96f120b53"}},"FinalAssembly":{"id":"81a7b2bc-4877-444b-bb90-c1b96f120b53","eClass":"EDTProject2:FinalAssembly","data":{"FinalAssembly":"07def44a-dc49-421e-9d0e-686949cc8c2d","Name":"Composite"}}}}]}
                """;
    }

    private void createEmptyProject(IEMFEditingContext emfEditingContext, UUID documentId, String projectName) {
        var resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
        var resourceMetadataAdapter = new ResourceMetadataAdapter(projectName);
        resource.eAdapters().add(resourceMetadataAdapter);
        emfEditingContext.getDomain().getResourceSet().getResources().add(resource);

        // Create empty EDT project structure
        var einstance = EDTProjectFactory.eINSTANCE;
        var steps = einstance.createSteps();

        // Step0 - Types
        var typesStep = einstance.createStep0();
        typesStep.setFolderName("0-Types");
        typesStep.getBasicTypes().addAll(EDTTypeDefaultCreator.createBasicTypes());
        typesStep.getEcoaPredefinedTypes().addAll(EDTTypeDefaultCreator.createPredefinedTypes(typesStep));

        // Step1 - Services
        var serviceDefinitionStep = einstance.createStep1();
        serviceDefinitionStep.setFolderName("1-Services");

        // Step2 - Component Definitions
        var componentDefinitionStep = einstance.createStep2();
        componentDefinitionStep.setFolderName("2-ComponentDefinitions");

        // Step3 - Initial Assembly
        var initialAssemblyStep = einstance.createStep3();
        initialAssemblyStep.setFolderName("3-InitialAssembly");
        var composite = einstance.createComposite();
        composite.setName("Composite");
        composite.setTargetNamespace("http://www.ecoa.technology/default");
        initialAssemblyStep.setInitialAssembly(composite);

        // Step4 - Component Implementations
        var componentImplementationStep = einstance.createStep4();
        componentImplementationStep.setFolderName("4-ComponentImplementations");

        // Step5 - Integration
        var integrationStep = einstance.createStep5();
        integrationStep.setFolderName("5-Integration");
        var finalAssembly = einstance.createFinalAssembly();
        finalAssembly.setName("Composite");
        finalAssembly.setFinalAssembly(composite);
        integrationStep.setFinalAssembly(finalAssembly);

        // Add the steps in order
        steps.getStep().add(typesStep);
        steps.getStep().add(serviceDefinitionStep);
        steps.getStep().add(componentDefinitionStep);
        steps.getStep().add(initialAssemblyStep);
        steps.getStep().add(componentImplementationStep);
        steps.getStep().add(integrationStep);

        resource.getContents().add(steps);
    }
}
