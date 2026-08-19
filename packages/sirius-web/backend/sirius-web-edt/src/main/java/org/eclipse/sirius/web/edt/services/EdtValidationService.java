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
package org.eclipse.sirius.web.edt.services;

import edtimplementation.ComponentImplementation;
import edtimplementation.ModuleInstance;
import edtproject.Component;
import edtproject.ComponentReference;
import edtproject.ComponentService;
import edtproject.Composite;
import org.eclipse.emf.ecore.EObject;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Service for validating EDT model elements.
 *
 * @author EDT Team
 */
@Service
public class EdtValidationService {

    /**
     * Verifies that ComponentReference and ComponentService can be linked.
     *
     * @param reference the ComponentReference
     * @param service the ComponentService
     * @return true if link is valid
     */
    public boolean isServiceLinkCorrectFromRef(ComponentReference reference, ComponentService service) {
        boolean valid = false;
        if (!Objects.equals(reference.eContainer(), service.eContainer())
                && reference.getComponentDefinitionReference() != null
                && service.getComponentDefinitionService() != null
                && Objects.equals(service.getComponentDefinitionService().getSyntax(),
                        reference.getComponentDefinitionReference().getSyntax())) {
            valid = true;
        }
        return valid;
    }

    /**
     * Check serviceLink coherence.
     *
     * @param source source of link
     * @param target target of link
     * @return true if link is correct
     */
    public boolean isServiceLinkCorrect(EObject source, EObject target) {
        boolean correct = false;
        if (source instanceof ComponentReference reference && target instanceof ComponentService service) {
            correct = this.isServiceLinkCorrectFromRef(reference, service) && reference.getServiceLink().size() <= 1;
        } else if (target instanceof ComponentReference ref && source instanceof ComponentService service) {
            correct = this.isServiceLinkCorrectFromRef(ref, service) && ref.getServiceLink().size() <= 1;
        }
        return correct;
    }

    /**
     * Check if ModuleInstance has an implementation.
     *
     * @param moduleInstance the ModuleInstance to verify
     * @return true if there is an error
     */
    public boolean moduleInstanceWithError(ModuleInstance moduleInstance) {
        return (moduleInstance.getModuleImplementation() == null)
                || (moduleInstance.getModuleImplementation().getModuleType() == null);
    }

    /**
     * Check if ComponentReference has an error.
     *
     * @param reference the ComponentReference
     * @return true if there is an error
     */
    public boolean referenceWithError(ComponentReference reference) {
        return reference.getComponentDefinitionReference() == null 
                || reference.getComponentDefinitionReference().getSyntax() == null;
    }

    /**
     * Check if ComponentService has an error.
     *
     * @param service the ComponentService
     * @return true if there is an error
     */
    public boolean serviceWithError(ComponentService service) {
        return service.getComponentDefinitionService() == null 
                || service.getComponentDefinitionService().getSyntax() == null;
    }

    /**
     * Check if ComponentImplementation has an error.
     *
     * @param componentImplementation the ComponentImplementation
     * @return true if there is an error
     */
    public boolean componentImplementationWithError(ComponentImplementation componentImplementation) {
        return componentImplementation.getComponentDefinition() == null;
    }

    /**
     * Verify Component has valid ComponentDefinition and ComponentImplementation.
     *
     * @param component the Component to verify
     * @return true if there is an error
     */
    public boolean componentWithError(Component component) {
        boolean error;
        if (component.getComponentDefinition() == null) {
            error = true;
        } else if (component.getComponentImplementation() != null) {
            error = !Objects.equals(component.getComponentImplementation().getComponentDefinition(),
                    component.getComponentDefinition());
        } else {
            error = false;
        }
        return error;
    }

    /**
     * Verify if service link can be created.
     *
     * @param source source of link
     * @param target target of link
     * @return true if link can be created
     */
    public boolean serviceLinkCanBeCreated(Object source, Object target) {
        boolean canCreate = false;
        if (source instanceof ComponentReference reference && target instanceof ComponentService service) {
            Composite composite = (Composite) reference.eContainer().eContainer();
            canCreate = composite.findServiceLink(reference, service) == null
                    && this.isServiceLinkCorrectFromRef(reference, service);
        } else if (target instanceof ComponentReference ref && source instanceof ComponentService service) {
            Composite composite = (Composite) ref.eContainer().eContainer();
            canCreate = composite.findServiceLink(ref, service) == null
                    && this.isServiceLinkCorrectFromRef(ref, service);
        }
        return canCreate;
    }
}
