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
package org.eclipse.sirius.web.application.viewer.controllers;

import java.util.Objects;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.auth.CurrentUserService;

import graphql.schema.DataFetchingEnvironment;

/**
 * Data fetcher for the field Viewer#id.
 *
 * @author sbegaudeau
 */
@QueryDataFetcher(type = "Viewer", field = "id")
public class ViewerIdDataFetcher implements IDataFetcherWithFieldCoordinates<String> {

    private final CurrentUserService currentUserService;

    public ViewerIdDataFetcher(CurrentUserService currentUserService) {
        this.currentUserService = Objects.requireNonNull(currentUserService);
    }

    @Override
    public String get(DataFetchingEnvironment environment) throws Exception {
        return this.currentUserService.getCurrentUser()
                .map(user -> user.id())
                .orElse("");
    }
}
