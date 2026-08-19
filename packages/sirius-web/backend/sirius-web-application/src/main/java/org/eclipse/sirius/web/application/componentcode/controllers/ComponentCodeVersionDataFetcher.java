package org.eclipse.sirius.web.application.componentcode.controllers;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionDTO;
import org.eclipse.sirius.web.application.componentcode.dto.ComponentCodeVersionPayload;
import org.eclipse.sirius.web.application.componentcode.services.api.IComponentCodeVersionService;

import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;
import java.util.UUID;

@QueryDataFetcher(type = "Query", field = "componentCodeVersion")
public class ComponentCodeVersionDataFetcher implements IDataFetcherWithFieldCoordinates<ComponentCodeVersionPayload> {

    private final IComponentCodeVersionService versionService;

    public ComponentCodeVersionDataFetcher(IComponentCodeVersionService versionService) {
        this.versionService = Objects.requireNonNull(versionService);
    }

    @Override
    public ComponentCodeVersionPayload get(DataFetchingEnvironment environment) throws Exception {
        var input = environment.getArgument("input");
        String versionId = ((java.util.Map<String, String>) input).get("versionId");

        return versionService.getComponentCodeVersion(UUID.fromString(versionId))
            .map(ComponentCodeVersionPayload::new)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
    }
}
