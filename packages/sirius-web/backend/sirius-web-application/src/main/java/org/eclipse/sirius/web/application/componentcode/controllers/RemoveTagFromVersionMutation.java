package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.RemoveTagFromVersionPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.springframework.security.access.prepost.PreAuthorize;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@MutationDataFetcher(type = "Mutation", field = "removeTagFromVersion")
@PreAuthorize("hasRole('ADMIN')")
public class RemoveTagFromVersionMutation implements IDataFetcherWithFieldCoordinates<RemoveTagFromVersionPayload> {

    private final IComponentCodeTagService tagService;
    private final IComponentCodeVersionService versionService;

    public RemoveTagFromVersionMutation(IComponentCodeTagService tagService, IComponentCodeVersionService versionService) {
        this.tagService = Objects.requireNonNull(tagService);
        this.versionService = Objects.requireNonNull(versionService);
    }

    @Override
    public RemoveTagFromVersionPayload get(DataFetchingEnvironment environment) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> input = environment.getArgument("input");

        tagService.removeTagFromVersion(
            UUID.fromString(input.get("versionId")),
            UUID.fromString(input.get("tagId"))
        );

        return versionService.getComponentCodeVersion(UUID.fromString(input.get("versionId")))
            .map(RemoveTagFromVersionPayload::new)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
    }
}
