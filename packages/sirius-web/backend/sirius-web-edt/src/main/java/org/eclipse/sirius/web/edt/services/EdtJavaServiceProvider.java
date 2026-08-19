/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.services;

import java.util.List;

import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.IJavaServiceProvider;
import org.springframework.stereotype.Service;

/**
 * Registers the Java services used by EDT view-based representations.
 */
@Service
public class EdtJavaServiceProvider implements IJavaServiceProvider {

    @Override
    public List<Class<?>> getServiceClasses(View view) {
        boolean isEdtView = view.getDescriptions().stream()
                .map(representationDescription -> representationDescription.getDomainType())
                .anyMatch(domainType -> domainType.startsWith("edt"));

        if (isEdtView) {
            return List.of(EdtValidationService.class, EdtDeletionService.class);
        }

        return List.of();
    }
}
