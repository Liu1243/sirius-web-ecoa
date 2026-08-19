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

import edtimplementation.OperationInstance;
import edtimplementation.OperationLink;
import edtproject.Component;
import edtproject.Composite;
import edtproject.ServiceLink;
import edtproject.Steps;
import edttype.Library;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Service for deletion verification of EDT model elements.
 * Replaces EcoaDtServicesDeletionVerification from Sirius Desktop.
 *
 * @author EDT Team
 */
@Service
public class EdtDeletionService {

    private final EdtNamingService namingService;

    public EdtDeletionService(EdtNamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * Verify if the Library is empty and can be deleted.
     *
     * @param eObject the object to delete
     * @return true if the object can be deleted
     */
    public boolean isEmptyToDelete(EObject eObject) {
        if (eObject instanceof Library library) {
            return library.getDataTypes().isEmpty();
        }
        return true;
    }

    /**
     * Check if an object has no cross references and can be safely deleted.
     *
     * @param objectToDelete the object to be verified before deletion
     * @return true if object can be deleted (no cross references)
     */
    public boolean hasNoCrossReferences(EObject objectToDelete) {
        EList<EObject> crossReferences = this.findCrossReferences(objectToDelete);
        return crossReferences.isEmpty();
    }

    /**
     * Find all objects that reference the given object.
     *
     * @param eObject the object to find references for
     * @return list of referencing objects
     */
    public EList<EObject> findCrossReferences(EObject eObject) {
        EList<EObject> result = new BasicEList<>();
        Steps steps = this.findStepsContainer(eObject);
        if (steps != null) {
            Collection<Setting> crossReferences = EcoreUtil.UsageCrossReferencer.find(eObject, steps);
            crossReferences.forEach(setting -> result.add(setting.getEObject()));
        }
        return result;
    }

    /**
     * Find the Steps container for an EObject.
     *
     * @param eObject the object to find the container for
     * @return the Steps container or null
     */
    private Steps findStepsContainer(EObject eObject) {
        EObject current = eObject;
        while (current != null) {
            if (current instanceof Steps steps) {
                return steps;
            }
            current = current.eContainer();
        }
        return null;
    }

    /**
     * Check if an OperationInstance has any links.
     *
     * @param operationInstance the operation to verify
     * @return true if no links exist
     */
    public boolean hasNoLinks(OperationInstance operationInstance) {
        EList<EObject> operationLinks = this.findCrossReferences(operationInstance);
        for (EObject operationLink : operationLinks) {
            if (operationLink instanceof OperationLink) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a Component has no service or operation links and can be deleted.
     *
     * @param eObject the object to check
     * @return true if can be deleted
     */
    public boolean hasNoServiceOrOperationLink(EObject eObject) {
        // Check for service links references
        EList<EObject> crossReferences = this.findCrossReferences(eObject);
        return crossReferences.isEmpty();
    }

    /**
     * Deletes a component and every composite service link connected to one of its ports.
     *
     * @param component the component to delete
     * @return the deleted component
     */
    public Component deleteComponentAndRelatedLinks(Component component) {
        if (component == null) {
            return null;
        }

        EObject container = component.eContainer();
        if (container instanceof Composite composite) {
            List<ServiceLink> relatedLinks = composite.getServiceLinks().stream()
                    .filter(serviceLink -> this.isConnectedToComponent(serviceLink, component))
                    .toList();
            relatedLinks.forEach(serviceLink -> EcoreUtil.delete(serviceLink, true));
        }

        EcoreUtil.delete(component, true);
        return component;
    }

    private boolean isConnectedToComponent(ServiceLink serviceLink, Component component) {
        return (serviceLink.getSource() != null && serviceLink.getSource().eContainer() == component)
                || (serviceLink.getTarget() != null && serviceLink.getTarget().eContainer() == component);
    }

    /**
     * Get deletion error message for cross references.
     *
     * @param objectToDelete the object being deleted
     * @return error message or empty string if no errors
     */
    public String getDeletionErrorMessage(EObject objectToDelete) {
        String nameObject = this.namingService.getObjectName(objectToDelete);
        EList<EObject> findCrossReferences = this.findCrossReferences(objectToDelete);
        
        if (!findCrossReferences.isEmpty()) {
            StringBuilder infoMessage = new StringBuilder();
            for (EObject referingObject : findCrossReferences) {
                String refName = this.namingService.getObjectName(referingObject);
                infoMessage.append("The ")
                        .append(referingObject.eClass().getName())
                        .append(" '").append(refName).append("'")
                        .append(" is referencing ").append(nameObject)
                        .append(System.lineSeparator());
            }
            return infoMessage.toString();
        }
        return "";
    }
}
