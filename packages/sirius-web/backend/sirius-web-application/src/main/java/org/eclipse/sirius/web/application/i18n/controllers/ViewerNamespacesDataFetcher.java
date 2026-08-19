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
package org.eclipse.sirius.web.application.i18n.controllers;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import graphql.schema.DataFetchingEnvironment;

/**
 * Data fetcher for the field Viewer#namespaces.
 *
 * <p>
 * It loads the resources i18n/{language}/{namespace}.json to compute namespaces.
 * Thus, adding new JSON files in /i18n/{language}/ will add new namespaces that will be handled by the frontend.
 * </p>
 *
 * @author gcoutable
 */
@QueryDataFetcher(type = "Viewer", field = "namespaces")
public class ViewerNamespacesDataFetcher implements IDataFetcherWithFieldCoordinates<List<String>> {

    private static final String LOG_PREFIX = "[ECOA-TRACE]";

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewerNamespacesDataFetcher.class);

    private static final String I18N_RESOURCE_PATTERN = "classpath*:/i18n/*/*.json";

    @Override
    public List<String> get(DataFetchingEnvironment environment) throws Exception {
        var classLoader = this.getClass().getClassLoader();
        LOGGER.info("{} Resolving viewer namespaces with pattern={} classLoader={}", LOG_PREFIX, I18N_RESOURCE_PATTERN, classLoader);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            var resources = Arrays.asList(resolver.getResources(I18N_RESOURCE_PATTERN));
            LOGGER.info("{} Found {} i18n resources for viewer namespaces", LOG_PREFIX, resources.size());

            var namespaces = resources.stream().map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .map(filename -> filename.substring(0, filename.lastIndexOf('.')))
                    .distinct()
                    .toList();

            LOGGER.info("{} Resolved {} viewer namespaces: {}", LOG_PREFIX, namespaces.size(), namespaces);
            return namespaces;
        } catch (Exception exception) {
            LOGGER.error("{} Failed to resolve viewer namespaces with pattern={}", LOG_PREFIX, I18N_RESOURCE_PATTERN, exception);
            throw exception;
        }
    }
}
