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
package org.eclipse.sirius.web.edt.factories;

import edtinterface.EDTInterfaceFactory;
import edtproject.EDTProjectFactory;
import edtproject.Steps;
import edttype.EDTTypeFactory;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.edt.factories.services.api.IEdtObjectFactory;
import org.eclipse.sirius.web.edt.factories.services.api.IEdtStepsIndexer;

import java.util.UUID;

/**
 * Used to create a default EDT project with sample data.
 *
 * @author EDT Team
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class EdtDefaultProjectFactory implements IEdtObjectFactory {

    private Steps steps;

    @Override
    public void create(IEMFEditingContext editingContext) {
        var documentId = UUID.randomUUID();
        var resource = new JSONResourceFactory().createResourceFromPath(documentId.toString());
        var resourceMetadataAdapter = new ResourceMetadataAdapter("EDT Sample Project");
        resource.eAdapters().add(resourceMetadataAdapter);
        editingContext.getDomain().getResourceSet().getResources().add(resource);

        this.steps = this.createSteps();
        resource.getContents().add(this.steps);
    }

    private Steps createSteps() {
        var stepsObj = EDTProjectFactory.eINSTANCE.createSteps();

        // Step0 - Types
        var step0 = EDTProjectFactory.eINSTANCE.createStep0();
        stepsObj.getStep().add(step0);

        // Create a sample library with a simple type
        var sampleLibrary = EDTTypeFactory.eINSTANCE.createLibrary();
        sampleLibrary.setName("SampleLibrary");
        step0.getTypes().add(sampleLibrary);

        // Step1 - Interfaces
        var step1 = EDTProjectFactory.eINSTANCE.createStep1();
        stepsObj.getStep().add(step1);

        // Create a sample service definition
        var sampleService = EDTInterfaceFactory.eINSTANCE.createServiceDefinition();
        sampleService.setName("SampleService");
        step1.getServices().add(sampleService);

        // Step2 - Component Definitions
        var step2 = EDTProjectFactory.eINSTANCE.createStep2();
        stepsObj.getStep().add(step2);

        // Create a sample component definition
        var sampleComponentDef = EDTProjectFactory.eINSTANCE.createComponentDefinition();
        sampleComponentDef.setName("SampleComponentDefinition");
        step2.getComponentDefinitions().add(sampleComponentDef);

        // Step3 - Assembly (Composite)
        var step3 = EDTProjectFactory.eINSTANCE.createStep3();
        stepsObj.getStep().add(step3);

        // Create a sample composite
        var sampleComposite = EDTProjectFactory.eINSTANCE.createComposite();
        sampleComposite.setName("SampleComposite");
        step3.setInitialAssembly(sampleComposite);

        // Step4 - Component Implementations
        var step4 = EDTProjectFactory.eINSTANCE.createStep4();
        stepsObj.getStep().add(step4);

        // Step5 - Deployment
        var step5 = EDTProjectFactory.eINSTANCE.createStep5();
        stepsObj.getStep().add(step5);

        // Create output directory
        var outputDirectory = EDTProjectFactory.eINSTANCE.createOutputDirectory();
        stepsObj.setOutputDirectory(outputDirectory);

        return stepsObj;
    }

    @Override
    public void link(IEdtStepsIndexer stepsIndexer) {
        // Link sample component definition service to the sample service definition
        var sampleService = stepsIndexer.getServiceDefinition("SampleService");
        var sampleComponentDef = stepsIndexer.getComponentDefinition("SampleComponentDefinition");

        if (sampleService != null && sampleComponentDef != null) {
            var componentDefService = EDTProjectFactory.eINSTANCE.createComponentDefinitionService();
            componentDefService.setName("SampleServicePort");
            componentDefService.setSyntax(sampleService);
            sampleComponentDef.getServices().add(componentDefService);
        }
    }
}
