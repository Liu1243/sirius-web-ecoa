/*******************************************************************************
 * Copyright (c) 2024 Obeo.
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
package org.eclipse.sirius.web.application.images.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

import org.eclipse.sirius.components.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.web.application.capability.SiriusWebCapabilities;
import org.eclipse.sirius.web.application.capability.services.api.ICapabilityEvaluator;
import org.eclipse.sirius.web.application.images.dto.DeleteImageInput;
import org.eclipse.sirius.web.application.images.services.api.IProjectImageApplicationService;
import org.eclipse.sirius.web.domain.boundedcontexts.projectimage.services.api.IProjectImageSearchService;
import org.eclipse.sirius.web.domain.services.api.IMessageService;

import graphql.schema.DataFetchingEnvironment;

/**
 * Data fetcher for the field Mutation#deleteImage.
 *
 * @author sbegaudeau
 */
@MutationDataFetcher(type = "Mutation", field = "deleteImage")
public class MutationDeleteImageDataFetcher implements IDataFetcherWithFieldCoordinates<IPayload> {

    private static final String INPUT_ARGUMENT = "input";

    private final ObjectMapper objectMapper;

    private final IProjectImageApplicationService imageApplicationService;

    private final IProjectImageSearchService projectImageSearchService;

    private final ICapabilityEvaluator capabilityEvaluator;

    private final IMessageService messageService;

    public MutationDeleteImageDataFetcher(ObjectMapper objectMapper, IProjectImageApplicationService imageApplicationService, IProjectImageSearchService projectImageSearchService, ICapabilityEvaluator capabilityEvaluator, IMessageService messageService) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.imageApplicationService = Objects.requireNonNull(imageApplicationService);
        this.projectImageSearchService = Objects.requireNonNull(projectImageSearchService);
        this.capabilityEvaluator = Objects.requireNonNull(capabilityEvaluator);
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public IPayload get(DataFetchingEnvironment environment) throws Exception {
        Object argument = environment.getArgument(INPUT_ARGUMENT);
        var input = this.objectMapper.convertValue(argument, DeleteImageInput.class);
        var hasCapability = this.projectImageSearchService.findById(input.imageId())
                .map(projectImage -> this.capabilityEvaluator.hasCapability(SiriusWebCapabilities.PROJECT, projectImage.getProject().getId(), SiriusWebCapabilities.Project.EDIT))
                .orElse(false);
        if (!hasCapability) {
            return new ErrorPayload(input.id(), this.messageService.unauthorized());
        }
        return this.imageApplicationService.deleteImage(input);
    }
}
