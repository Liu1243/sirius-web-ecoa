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
package org.eclipse.sirius.web.edt.factories.services;

import edtimplementation.ComponentImplementation;
import edtinterface.ServiceDefinition;
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
import edtproject.util.EDTProjectSwitch;
import edttype.Library;

/**
 * Switch-based indexer for EDT model elements.
 *
 * @author EDT Team
 */
public class EdtSwitchIndexer extends EDTProjectSwitch<Boolean> {

    private final EdtStepsIndexer edtStepsIndexer;

    public EdtSwitchIndexer(EdtStepsIndexer edtStepsIndexer) {
        this.edtStepsIndexer = edtStepsIndexer;
    }

    @Override
    public Boolean caseSteps(Steps steps) {
        this.edtStepsIndexer.setSteps(steps);
        steps.getStep().forEach(this::doSwitch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep0(Step0 step0) {
        step0.getTypes().forEach(this::indexLibrary);
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep1(Step1 step1) {
        step1.getServices().forEach(this::indexServiceDefinition);
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep2(Step2 step2) {
        step2.getComponentDefinitions().forEach(this::indexComponentDefinition);
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep3(Step3 step3) {
        if (step3.getInitialAssembly() != null) {
            indexComposite(step3.getInitialAssembly());
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep4(Step4 step4) {
        step4.getComponentImplementations().forEach(this::indexComponentImplementation);
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep5(Step5 step5) {
        // Step5 contains deployment information, not currently indexed
        return Boolean.TRUE;
    }

    @Override
    public Boolean caseStep(Step step) {
        // Fallback for generic Step
        return Boolean.TRUE;
    }

    private void indexLibrary(Library library) {
        if (library.getName() != null) {
            this.edtStepsIndexer.getNameToLibrary().put(library.getName(), library);
        }
    }

    private void indexServiceDefinition(ServiceDefinition serviceDefinition) {
        if (serviceDefinition.getName() != null) {
            this.edtStepsIndexer.getNameToServiceDefinition().put(serviceDefinition.getName(), serviceDefinition);
        }
    }

    private void indexComponentDefinition(ComponentDefinition componentDefinition) {
        if (componentDefinition.getName() != null) {
            this.edtStepsIndexer.getNameToComponentDefinition().put(componentDefinition.getName(), componentDefinition);
        }
    }

    private void indexComponentImplementation(ComponentImplementation componentImplementation) {
        if (componentImplementation.getName() != null) {
            this.edtStepsIndexer.getNameToComponentImplementation().put(componentImplementation.getName(), componentImplementation);
        }
    }

    private void indexComposite(Composite composite) {
        if (composite.getName() != null) {
            this.edtStepsIndexer.getNameToComposite().put(composite.getName(), composite);
        }
    }
}
