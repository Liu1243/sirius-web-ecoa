package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeHistoryDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeHistoryPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;

import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;
import java.util.UUID;

@QueryDataFetcher(type = "Query", field = "componentCodeHistory")
public class ComponentCodeHistoryDataFetcher implements IDataFetcherWithFieldCoordinates<ComponentCodeHistoryPayload> {

    private final IComponentCodeVersionService versionService;

    private final IProjectEditingContextService projectEditingContextService;

    public ComponentCodeHistoryDataFetcher(IComponentCodeVersionService versionService, IProjectEditingContextService projectEditingContextService) {
        this.versionService = Objects.requireNonNull(versionService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
    }

    @Override
    public ComponentCodeHistoryPayload get(DataFetchingEnvironment environment) throws Exception {
        var input = environment.getArgument("input");
        String editingContextId = ((java.util.Map<String, String>) input).get("projectId");

        // Convert editingContextId (semantic data id) to projectId
        String projectId = this.projectEditingContextService.getProjectId(editingContextId)
            .orElse(editingContextId); // Fallback to original value if conversion fails

        ComponentCodeHistoryDTO history = versionService.getComponentCodeHistory(UUID.fromString(projectId));
        return new ComponentCodeHistoryPayload(history);
    }
}
