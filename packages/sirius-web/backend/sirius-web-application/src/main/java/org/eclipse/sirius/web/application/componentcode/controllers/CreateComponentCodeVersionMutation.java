package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.CreateComponentCodeVersionPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.springframework.security.access.prepost.PreAuthorize;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@MutationDataFetcher(type = "Mutation", field = "createComponentCodeVersion")
@PreAuthorize("hasRole('ADMIN')")
public class CreateComponentCodeVersionMutation implements IDataFetcherWithFieldCoordinates<CreateComponentCodeVersionPayload> {

    private final IComponentCodeVersionService versionService;

    public CreateComponentCodeVersionMutation(IComponentCodeVersionService versionService) {
        this.versionService = Objects.requireNonNull(versionService);
    }

    @Override
    public CreateComponentCodeVersionPayload get(DataFetchingEnvironment environment) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> input = environment.getArgument("input");

        var version = versionService.createComponentCodeVersion(
            UUID.fromString((String) input.get("projectId")),
            (String) input.get("componentId"),
            (String) input.get("componentName"),
            (String) input.get("versionName"),
            (String) input.get("codeContent"),
            (String) input.get("commitMessage"),
            "admin", // TODO: Get from security context
            (String) input.get("modelVersionId")
        );

        return new CreateComponentCodeVersionPayload(version);
    }
}
