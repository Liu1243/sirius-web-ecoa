package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.DeleteComponentCodeVersionPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;
import org.springframework.security.access.prepost.PreAuthorize;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@MutationDataFetcher(type = "Mutation", field = "deleteComponentCodeVersion")
@PreAuthorize("hasRole('ADMIN')")
public class DeleteComponentCodeVersionMutation implements IDataFetcherWithFieldCoordinates<DeleteComponentCodeVersionPayload> {

    private final IComponentCodeVersionService versionService;

    public DeleteComponentCodeVersionMutation(IComponentCodeVersionService versionService) {
        this.versionService = Objects.requireNonNull(versionService);
    }

    @Override
    public DeleteComponentCodeVersionPayload get(DataFetchingEnvironment environment) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> input = environment.getArgument("input");
        String versionId = input.get("versionId");

        UUID versionUuid = UUID.fromString(versionId);
        versionService.deleteComponentCodeVersion(versionUuid);
        return new DeleteComponentCodeVersionPayload(versionUuid);
    }
}
