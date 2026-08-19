/*******************************************************************************
 * Copyright (c) 2025 Obeo.
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

import edtproject.EDTProjectPackage;
import org.eclipse.sirius.web.application.UUIDParser;
import org.eclipse.sirius.web.domain.boundedcontexts.project.Nature;
import org.eclipse.sirius.web.domain.boundedcontexts.project.Project;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.ProjectSemanticData;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.services.api.IProjectSemanticDataSearchService;
import org.eclipse.sirius.web.domain.boundedcontexts.semanticdata.services.api.ISemanticDataSearchService;
import org.eclipse.sirius.web.edt.projecttemplates.EdtProjectTemplateProvider;
import org.eclipse.sirius.web.edt.services.api.IEdtCapableEditingContextPredicate;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Used to test if an editing context is capable of supporting an edt project.
 *
 * @author managerial
 */
@Service
public class EdtCapableEditingContextPredicate implements IEdtCapableEditingContextPredicate {

    private final IProjectSearchService projectSearchService;

    private final IProjectSemanticDataSearchService projectSemanticDataSearchService;

    private final ISemanticDataSearchService semanticDataSearchService;

    public EdtCapableEditingContextPredicate(IProjectSearchService projectSearchService, IProjectSemanticDataSearchService projectSemanticDataSearchService, ISemanticDataSearchService semanticDataSearchService) {
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectSemanticDataSearchService = Objects.requireNonNull(projectSemanticDataSearchService);
        this.semanticDataSearchService = Objects.requireNonNull(semanticDataSearchService);
    }

    @Override
    public boolean test(String editingContextId) {
        return this.isEdtProject(editingContextId) || this.isEdtSemanticData(editingContextId);
    }

    private boolean isEdtProject(String editingContextId) {
        return new UUIDParser().parse(editingContextId)
                .flatMap(semanticDataId -> this.projectSemanticDataSearchService.findBySemanticDataId(AggregateReference.to(semanticDataId)))
                .map(ProjectSemanticData::getProject)
                .map(AggregateReference::getId)
                .flatMap(this.projectSearchService::findById)
                .filter(this::isEdt)
                .isPresent();
    }

    private boolean isEdt(Project project) {
        return project.getNatures().stream()
                .map(Nature::name)
                .anyMatch(EdtProjectTemplateProvider.EDT_NATURE::equals);
    }

    private boolean isEdtSemanticData(String editingContextId) {
        return new UUIDParser().parse(editingContextId)
                .map((UUID id) -> this.semanticDataSearchService.isUsingDomains(id, List.of(EDTProjectPackage.eNS_URI)))
                .orElse(Boolean.FALSE)
                .booleanValue();
    }
}
