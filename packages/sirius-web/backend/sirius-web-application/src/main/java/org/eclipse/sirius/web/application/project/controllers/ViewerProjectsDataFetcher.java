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
package org.eclipse.sirius.web.application.project.controllers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import graphql.execution.DataFetcherResult;
import graphql.relay.Connection;
import graphql.relay.ConnectionCursor;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.Edge;
import graphql.relay.Relay;
import graphql.schema.DataFetchingEnvironment;
import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.core.graphql.dto.PageInfoWithCount;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.SiriusWebLocalContextConstants;
import org.eclipse.sirius.web.application.capability.SiriusWebCapabilities;
import org.eclipse.sirius.web.application.capability.services.api.ICapabilityEvaluator;
import org.eclipse.sirius.web.application.pagination.services.api.ILimitProvider;
import org.eclipse.sirius.web.application.project.dto.ProjectDTO;
import org.eclipse.sirius.web.application.project.dto.ProjectSortDTO;
import org.eclipse.sirius.web.application.project.dto.ProjectSortDirection;
import org.eclipse.sirius.web.application.project.dto.ProjectSortField;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.eclipse.sirius.web.domain.pagination.Window;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

/**
 * Data fetcher for the field Viewer#projects.
 *
 * @author sbegaudeau
 */
@QueryDataFetcher(type = "Viewer", field = "projects")
public class ViewerProjectsDataFetcher implements IDataFetcherWithFieldCoordinates<Connection<DataFetcherResult<ProjectDTO>>> {

    private static final String FIRST_ARGUMENT = "first";

    private static final String LAST_ARGUMENT = "last";

    private static final String AFTER_ARGUMENT = "after";

    private static final String BEFORE_ARGUMENT = "before";

    private static final String FILTER_ARGUMENT = "filter";

    private static final String SORT_ARGUMENT = "sort";

    private final ICapabilityEvaluator capabilityEvaluator;

    private final IProjectApplicationService projectApplicationService;

    private final ILimitProvider limitProvider;

    public ViewerProjectsDataFetcher(ICapabilityEvaluator capabilityEvaluator, IProjectApplicationService projectApplicationService, ILimitProvider limitProvider) {
        this.capabilityEvaluator = Objects.requireNonNull(capabilityEvaluator);
        this.projectApplicationService = Objects.requireNonNull(projectApplicationService);
        this.limitProvider = Objects.requireNonNull(limitProvider);
    }

    @Override
    public Connection<DataFetcherResult<ProjectDTO>> get(DataFetchingEnvironment environment) throws Exception {
        var hasCapability = this.capabilityEvaluator.hasCapability(SiriusWebCapabilities.PROJECT, null, SiriusWebCapabilities.Project.LIST);
        if (!hasCapability) {
            return new DefaultConnection<>(List.of(), new PageInfoWithCount(null, null, false, false, 0));
        }

        Optional<Integer> first = Optional.ofNullable(environment.getArgument(FIRST_ARGUMENT));
        Optional<Integer> last = Optional.ofNullable(environment.getArgument(LAST_ARGUMENT));
        Optional<String> after = Optional.ofNullable(environment.getArgument(AFTER_ARGUMENT));
        Optional<String> before = Optional.ofNullable(environment.getArgument(BEFORE_ARGUMENT));
        Map<String, Object> filter = Optional.ofNullable(environment.<Map<String, Object>>getArgument(FILTER_ARGUMENT)).orElseGet(Map::of);
        ProjectSortDTO sort = this.getSort(environment);

        KeysetScrollPosition position = this.getPosition(after, before);
        int limit = this.limitProvider.getLimit(20, first, last, after, before);

        var projectPage = this.projectApplicationService.findAll(position, limit, filter, sort);
        return this.toConnection(projectPage, sort);
    }

    public KeysetScrollPosition getPosition(Optional<String> after, Optional<String> before) {
        KeysetScrollPosition position = ScrollPosition.keyset();
        if (after.isPresent() && before.isEmpty()) {
            var cursor = this.decodeCursor(after.get());
            position = ScrollPosition.forward(Map.of("id", cursor.projectId(), "sortValue", cursor.sortValue()));
        } else if (before.isPresent() && after.isEmpty()) {
            var cursor = this.decodeCursor(before.get());
            position = ScrollPosition.backward(Map.of("id", cursor.projectId(), "sortValue", cursor.sortValue()));
        }
        return position;
    }

    private Connection<DataFetcherResult<ProjectDTO>> toConnection(Window<ProjectDTO> window, ProjectSortDTO sort) {
        List<Edge<DataFetcherResult<ProjectDTO>>> edges = window.stream().map(projectDTO -> {
            var cursor = new DefaultConnectionCursor(this.encodeCursor(projectDTO, sort));

            Map<String, Object> localContext = new HashMap<>();
            localContext.put(SiriusWebLocalContextConstants.PROJECT_ID, projectDTO.id());

            return (Edge<DataFetcherResult<ProjectDTO>>) new DefaultEdge<>(DataFetcherResult.<ProjectDTO>newResult()
                    .data(projectDTO)
                    .localContext(localContext)
                    .build(), cursor);
        }).toList();

        ConnectionCursor startCursor = edges.stream().findFirst()
                .map(Edge::getCursor)
                .orElse(null);
        ConnectionCursor endCursor = null;
        if (!edges.isEmpty()) {
            endCursor = edges.get(edges.size() - 1).getCursor();
        }

        var pageInfo = new PageInfoWithCount(startCursor, endCursor, window.hasPrevious(), window.hasNext(), window.size());
        return new DefaultConnection<>(edges, pageInfo);
    }

    private ProjectSortDTO getSort(DataFetchingEnvironment environment) {
        Map<String, Object> sortArgument = Optional.ofNullable(environment.<Map<String, Object>>getArgument(SORT_ARGUMENT)).orElse(null);
        if (sortArgument == null) {
            return ProjectSortDTO.defaultSort();
        }
        String field = Optional.ofNullable(sortArgument.get("field")).map(Object::toString).orElse(ProjectSortField.NAME.name());
        String direction = Optional.ofNullable(sortArgument.get("direction")).map(Object::toString).orElse(ProjectSortDirection.ASC.name());
        return new ProjectSortDTO(ProjectSortField.valueOf(field), ProjectSortDirection.valueOf(direction));
    }

    private String encodeCursor(ProjectDTO projectDTO, ProjectSortDTO sort) {
        String payload = this.cursorSortValue(projectDTO, sort.field()) + '\u0000' + projectDTO.id();
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String rawCursor) {
        String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
        int separatorIndex = decoded.indexOf('\u0000');
        if (separatorIndex < 0) {
            return new Cursor(decoded, decoded);
        }
        return new Cursor(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }

    private String cursorSortValue(ProjectDTO projectDTO, ProjectSortField field) {
        return switch (field) {
            case CREATED_ON -> projectDTO.createdOn().toString();
            case LAST_MODIFIED_ON -> projectDTO.lastModifiedOn().toString();
            case NAME -> projectDTO.name().toLowerCase();
        };
    }

    private record Cursor(String sortValue, String projectId) {
    }
}
