package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeTagsPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;

import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;
import java.util.UUID;

@QueryDataFetcher(type = "Query", field = "componentCodeTags")
public class ComponentCodeTagsDataFetcher implements IDataFetcherWithFieldCoordinates<ComponentCodeTagsPayload> {

    private final IComponentCodeTagService tagService;

    private final IProjectEditingContextService projectEditingContextService;

    public ComponentCodeTagsDataFetcher(IComponentCodeTagService tagService, IProjectEditingContextService projectEditingContextService) {
        this.tagService = Objects.requireNonNull(tagService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
    }

    @Override
    public ComponentCodeTagsPayload get(DataFetchingEnvironment environment) throws Exception {
        var input = environment.getArgument("input");
        String editingContextId = ((java.util.Map<String, String>) input).get("projectId");

        // Convert editingContextId (semantic data id) to projectId
        String projectId = this.projectEditingContextService.getProjectId(editingContextId)
            .orElse(editingContextId); // Fallback to original value if conversion fails

        var tags = tagService.getComponentCodeTags(UUID.fromString(projectId));
        return new ComponentCodeTagsPayload(tags);
    }
}
