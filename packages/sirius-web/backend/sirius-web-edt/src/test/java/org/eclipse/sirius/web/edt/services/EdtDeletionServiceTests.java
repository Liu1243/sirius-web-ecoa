/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import edtproject.Component;
import edtproject.ComponentReference;
import edtproject.ComponentService;
import edtproject.Composite;
import edtproject.EDTProjectFactory;
import edtproject.ServiceLink;

public class EdtDeletionServiceTests {

    @Test
    public void givenComponentWithConnectedServiceLinksWhenDeletedThenComponentAndLinksAreRemoved() {
        var deletionService = new EdtDeletionService(new EdtNamingService());
        var composite = EDTProjectFactory.eINSTANCE.createComposite();

        var componentToDelete = this.newComponent("componentToDelete");
        var componentToKeep = this.newComponent("componentToKeep");
        composite.getComponents().add(componentToDelete);
        composite.getComponents().add(componentToKeep);

        var outgoingReference = this.newReference("outgoingReference");
        var outgoingService = this.newService("outgoingService");
        componentToDelete.getComponentReferences().add(outgoingReference);
        componentToDelete.getComponentServices().add(outgoingService);

        var keepReference = this.newReference("keepReference");
        var keepService = this.newService("keepService");
        componentToKeep.getComponentReferences().add(keepReference);
        componentToKeep.getComponentServices().add(keepService);

        var outgoingLink = this.newServiceLink(outgoingReference, keepService);
        var incomingLink = this.newServiceLink(keepReference, outgoingService);
        var untouchedLink = this.newServiceLink(keepReference, keepService);
        composite.getServiceLinks().add(outgoingLink);
        composite.getServiceLinks().add(incomingLink);
        composite.getServiceLinks().add(untouchedLink);

        deletionService.deleteComponentAndRelatedLinks(componentToDelete);

        assertThat(composite.getComponents()).containsExactly(componentToKeep);
        assertThat(composite.getServiceLinks()).containsExactly(untouchedLink);
        assertThat(componentToDelete.eContainer()).isNull();
        assertThat(outgoingLink.eContainer()).isNull();
        assertThat(incomingLink.eContainer()).isNull();
    }

    private Component newComponent(String name) {
        var component = EDTProjectFactory.eINSTANCE.createComponent();
        component.setName(name);
        return component;
    }

    private ComponentReference newReference(String name) {
        var reference = EDTProjectFactory.eINSTANCE.createComponentReference();
        reference.setName(name);
        return reference;
    }

    private ComponentService newService(String name) {
        var service = EDTProjectFactory.eINSTANCE.createComponentService();
        service.setName(name);
        return service;
    }

    private ServiceLink newServiceLink(ComponentReference source, ComponentService target) {
        var serviceLink = EDTProjectFactory.eINSTANCE.createServiceLink();
        serviceLink.setSource(source);
        serviceLink.setTarget(target);
        return serviceLink;
    }
}
