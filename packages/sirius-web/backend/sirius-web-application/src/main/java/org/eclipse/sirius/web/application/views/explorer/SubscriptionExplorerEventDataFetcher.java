/*******************************************************************************
 * Copyright (c) 2022, 2024 Obeo.
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
package org.eclipse.sirius.web.application.views.explorer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.sirius.components.annotations.spring.graphql.SubscriptionDataFetcher;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.components.graphql.api.IEventProcessorSubscriptionProvider;
import org.eclipse.sirius.components.graphql.api.IExceptionWrapper;
import org.eclipse.sirius.components.graphql.api.LocalContextConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivestreams.Publisher;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;

/**
 * The data fetcher used to send the refreshed tree to a subscription.
 *
 * @author hmarchadour
 * @author pcdavid
 */
@SubscriptionDataFetcher(type = "Subscription", field = "explorerEvent")
public class SubscriptionExplorerEventDataFetcher implements IDataFetcherWithFieldCoordinates<Publisher<DataFetcherResult<IPayload>>> {

    private static final String INPUT_ARGUMENT = "input";
    private static final String LOG_PREFIX = "[ECOA-TRACE]";

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionExplorerEventDataFetcher.class);

    private final ObjectMapper objectMapper;

    private final IExceptionWrapper exceptionWrapper;

    private final IEventProcessorSubscriptionProvider eventProcessorSubscriptionProvider;

    public SubscriptionExplorerEventDataFetcher(ObjectMapper objectMapper, IExceptionWrapper exceptionWrapper, IEventProcessorSubscriptionProvider eventProcessorSubscriptionProvider) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.exceptionWrapper = Objects.requireNonNull(exceptionWrapper);
        this.eventProcessorSubscriptionProvider = Objects.requireNonNull(eventProcessorSubscriptionProvider);
    }

    @Override
    public Publisher<DataFetcherResult<IPayload>> get(DataFetchingEnvironment environment) throws Exception {
        Object argument = environment.getArgument(INPUT_ARGUMENT);
        var input = this.objectMapper.convertValue(argument, ExplorerEventInput.class);
        LOGGER.info("{} explorerEvent subscription requested editingContextId={} representationId={} inputId={}", LOG_PREFIX, input.editingContextId(), input.representationId(), input.id());

        Map<String, Object> localContext = new HashMap<>();
        localContext.put(LocalContextConstants.EDITING_CONTEXT_ID, input.editingContextId());
        localContext.put(LocalContextConstants.REPRESENTATION_ID, input.representationId());

        return this.exceptionWrapper.wrapFlux(() -> this.eventProcessorSubscriptionProvider.getSubscription(input.editingContextId(), input.representationId(), input), input)
                .map(payload -> {
                    LOGGER.info("{} explorerEvent payload editingContextId={} representationId={} payloadType={} treeId={} rootCount={}",
                            LOG_PREFIX,
                            input.editingContextId(),
                            input.representationId(),
                            payload != null ? payload.getClass().getSimpleName() : null,
                            this.extractTreeId(payload),
                            this.extractRootCount(payload));
                    LOGGER.info("{} explorerEvent payload details editingContextId={} representationId={} message={} messages={}",
                            LOG_PREFIX,
                            input.editingContextId(),
                            input.representationId(),
                            this.extractMessage(payload),
                            this.extractMessages(payload));

                    return DataFetcherResult.<IPayload>newResult()
                        .data(payload)
                        .localContext(localContext)
                        .build();
                });
    }

    private String extractTreeId(IPayload payload) {
        Object tree = this.extractTree(payload);
        if (tree == null) {
            return null;
        }
        try {
            Method idMethod = tree.getClass().getMethod("getId");
            Object value = idMethod.invoke(tree);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException exception) {
            try {
                Method idMethod = tree.getClass().getMethod("id");
                Object value = idMethod.invoke(tree);
                return value != null ? value.toString() : null;
            } catch (ReflectiveOperationException nestedException) {
                return null;
            }
        }
    }

    private int extractRootCount(IPayload payload) {
        Object tree = this.extractTree(payload);
        if (tree == null) {
            return 0;
        }
        for (String methodName : List.of("getChildren", "children", "getRoots", "roots")) {
            try {
                Method method = tree.getClass().getMethod(methodName);
                Object value = method.invoke(tree);
                if (value instanceof List<?> list) {
                    return list.size();
                }
            } catch (ReflectiveOperationException exception) {
                // Ignore and try the next accessor.
            }
        }
        for (String methodName : List.of("getRoot", "root")) {
            try {
                Method method = tree.getClass().getMethod(methodName);
                Object value = method.invoke(tree);
                return value != null ? 1 : 0;
            } catch (ReflectiveOperationException exception) {
                // Ignore and return 0 below.
            }
        }
        return 0;
    }

    private Object extractTree(IPayload payload) {
        if (payload == null) {
            return null;
        }
        try {
            Method treeMethod = payload.getClass().getMethod("tree");
            return treeMethod.invoke(payload);
        } catch (ReflectiveOperationException exception) {
            try {
                Method treeMethod = payload.getClass().getMethod("getTree");
                return treeMethod.invoke(payload);
            } catch (ReflectiveOperationException nestedException) {
                return null;
            }
        }
    }

    private String extractMessage(IPayload payload) {
        if (payload == null) {
            return null;
        }
        for (String methodName : List.of("message", "getMessage")) {
            try {
                Method method = payload.getClass().getMethod(methodName);
                Object value = method.invoke(payload);
                return value != null ? value.toString() : null;
            } catch (ReflectiveOperationException exception) {
                // Ignore and try the next accessor.
            }
        }
        return null;
    }

    private String extractMessages(IPayload payload) {
        if (payload == null) {
            return null;
        }
        for (String methodName : List.of("messages", "getMessages")) {
            try {
                Method method = payload.getClass().getMethod(methodName);
                Object value = method.invoke(payload);
                return value != null ? value.toString() : null;
            } catch (ReflectiveOperationException exception) {
                // Ignore and try the next accessor.
            }
        }
        return null;
    }
}
