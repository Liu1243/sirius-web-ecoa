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

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import edtimplementation.ComponentImplementation;
import edtinterface.ServiceDefinition;
import edtproject.ComponentDefinition;
import edtproject.Composite;
import edtproject.Steps;
import edttype.Library;

import org.eclipse.sirius.web.edt.factories.services.api.IEdtStepsIndexer;

/**
 * Used to index EDT Steps elements.
 *
 * @author EDT Team
 */
public class EdtStepsIndexer implements IEdtStepsIndexer {

    private Steps steps;

    private final Map<String, Library> nameToLibrary = new LinkedHashMap<>();

    private final Map<String, ServiceDefinition> nameToServiceDefinition = new LinkedHashMap<>();

    private final Map<String, ComponentDefinition> nameToComponentDefinition = new LinkedHashMap<>();

    private final Map<String, ComponentImplementation> nameToComponentImplementation = new LinkedHashMap<>();

    private final Map<String, Composite> nameToComposite = new LinkedHashMap<>();

    public void index(ResourceSet resourceSet) {
        resourceSet.getResources().forEach(this::index);
    }

    private void index(Resource resource) {
        resource.getContents().forEach(this::index);
    }

    private void index(EObject eObject) {
        new EdtSwitchIndexer(this).doSwitch(eObject);
    }

    public void setSteps(Steps steps) {
        this.steps = steps;
    }

    @Override
    public Steps getSteps() {
        return this.steps;
    }

    public Map<String, Library> getNameToLibrary() {
        return this.nameToLibrary;
    }

    public Map<String, ServiceDefinition> getNameToServiceDefinition() {
        return this.nameToServiceDefinition;
    }

    public Map<String, ComponentDefinition> getNameToComponentDefinition() {
        return this.nameToComponentDefinition;
    }

    public Map<String, ComponentImplementation> getNameToComponentImplementation() {
        return this.nameToComponentImplementation;
    }

    public Map<String, Composite> getNameToComposite() {
        return this.nameToComposite;
    }

    @Override
    public Library getLibrary(String name) {
        return this.nameToLibrary.get(name);
    }

    @Override
    public ServiceDefinition getServiceDefinition(String name) {
        return this.nameToServiceDefinition.get(name);
    }

    @Override
    public ComponentDefinition getComponentDefinition(String name) {
        return this.nameToComponentDefinition.get(name);
    }

    @Override
    public ComponentImplementation getComponentImplementation(String name) {
        return this.nameToComponentImplementation.get(name);
    }

    @Override
    public Composite getComposite(String name) {
        return this.nameToComposite.get(name);
    }
}
