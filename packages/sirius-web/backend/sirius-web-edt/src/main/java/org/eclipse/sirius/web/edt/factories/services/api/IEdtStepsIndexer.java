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
package org.eclipse.sirius.web.edt.factories.services.api;

import edtimplementation.ComponentImplementation;
import edtinterface.ServiceDefinition;
import edtproject.ComponentDefinition;
import edtproject.Composite;
import edtproject.Steps;
import edttype.Library;

/**
 * Used to index EDT Steps elements.
 *
 * @author EDT Team
 */
public interface IEdtStepsIndexer {

    Steps getSteps();

    Library getLibrary(String name);

    ServiceDefinition getServiceDefinition(String name);

    ComponentDefinition getComponentDefinition(String name);

    ComponentImplementation getComponentImplementation(String name);

    Composite getComposite(String name);
}
