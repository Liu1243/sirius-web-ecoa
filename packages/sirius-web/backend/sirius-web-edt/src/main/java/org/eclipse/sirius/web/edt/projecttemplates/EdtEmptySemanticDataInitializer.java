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

import edtproject.EDTProjectFactory;
import edtproject.Steps;
import edttype.util.EDTTypeDefaultCreator;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextPersistenceService;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.events.ICause;
import org.eclipse.sirius.web.application.project.services.api.ISemanticDataInitializer;
import org.eclipse.sirius.web.domain.boundedcontexts.project.events.ProjectCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.events.ProjectSemanticDataCreatedEvent;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.events.SemanticDataCreatedEvent;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Used to create the empty EDT project.
 *
 * @author EDT Team
 */
@Service
public class EdtEmptySemanticDataInitializer implements ISemanticDataInitializer {

    private final IEditingContextPersistenceService editingContextPersistenceService;

    public EdtEmptySemanticDataInitializer(IEditingContextPersistenceService editingContextPersistenceService) {
        this.editingContextPersistenceService = Objects.requireNonNull(editingContextPersistenceService);
    }

    @Override
    public boolean canHandle(String projectTemplateId) {
        return EdtProjectTemplateProvider.EMPTY_PROJECT_TEMPLATE_ID.equals(projectTemplateId);
    }

    @Override
    public void handle(ICause cause, IEditingContext editingContext, String projectTemplateId) {
        if (editingContext instanceof IEMFEditingContext emfEditingContext) {
            String projectName = "EDT Project";
            if (cause instanceof ProjectSemanticDataCreatedEvent projectSemanticDataCreatedEvent
                    && projectSemanticDataCreatedEvent.causedBy() instanceof SemanticDataCreatedEvent semanticDataCreatedEvent
                    && semanticDataCreatedEvent.causedBy() instanceof ProjectCreatedEvent projectCreatedEvent) {
                projectName = projectCreatedEvent.project().getName();
            }

            var documentId = UUID.randomUUID();
            var resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
            var resourceMetadataAdapter = new ResourceMetadataAdapter(projectName);
            resource.eAdapters().add(resourceMetadataAdapter);
            emfEditingContext.getDomain().getResourceSet().getResources().add(resource);

            // Create the EDT project structure following sirius-desktop's EcoadtServices.createEDTProject()
            EDTProjectFactory einstance = EDTProjectFactory.eINSTANCE;

            // Instantiate the steps
            Steps steps = einstance.createSteps();

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
            // Set Composite
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
            // Set Final Assembly
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

            this.editingContextPersistenceService.persist(cause, editingContext);
        }
    }
}
