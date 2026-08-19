package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.CreateComponentCodeTagPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeTagService;
import org.springframework.security.access.prepost.PreAuthorize;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@MutationDataFetcher(type = "Mutation", field = "createComponentCodeTag")
@PreAuthorize("hasRole('ADMIN')")
public class CreateComponentCodeTagMutation implements IDataFetcherWithFieldCoordinates<CreateComponentCodeTagPayload> {

    private final IComponentCodeTagService tagService;

    public CreateComponentCodeTagMutation(IComponentCodeTagService tagService) {
        this.tagService = Objects.requireNonNull(tagService);
    }

    @Override
    public CreateComponentCodeTagPayload get(DataFetchingEnvironment environment) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> input = environment.getArgument("input");

        var tag = tagService.createComponentCodeTag(
            UUID.fromString(input.get("projectId")),
            input.get("name"),
            input.get("color")
        );

        return new CreateComponentCodeTagPayload(tag);
    }
}
