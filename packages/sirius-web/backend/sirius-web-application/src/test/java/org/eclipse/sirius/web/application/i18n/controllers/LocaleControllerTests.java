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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

/**
 * Tests of the locale controller.
 *
 * @author Codex
 */
public class LocaleControllerTests {

    @Test
    public void givenMissingChineseLocaleWhenGettingLocaleThenFallbackToEnglish() throws IOException {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource missingChineseResource = new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        };
        Resource englishResource = new ByteArrayResource("{\"toolbar\":{\"zoomLevel\":\"Zoom level\"}}".getBytes(StandardCharsets.UTF_8));

        when(resourceLoader.getResource("classpath:i18n/zh/sirius-components-gantt.json")).thenReturn(missingChineseResource);
        when(resourceLoader.getResource("classpath:i18n/en/sirius-components-gantt.json")).thenReturn(englishResource);

        var localeController = new LocaleController(resourceLoader);
        var response = localeController.getLocale("zh", "sirius-components-gantt", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(this.asString(response.getBody())).contains("\"zoomLevel\":\"Zoom level\"");
        verify(resourceLoader).getResource("classpath:i18n/zh/sirius-components-gantt.json");
        verify(resourceLoader).getResource("classpath:i18n/en/sirius-components-gantt.json");
    }

    @Test
    public void givenMissingEverywhereLocaleWhenGettingLocaleThenReturnNotFound() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource missingResource = new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        };

        when(resourceLoader.getResource("classpath:i18n/zh/missing-namespace-for-tests.json")).thenReturn(missingResource);
        when(resourceLoader.getResource("classpath:i18n/en/missing-namespace-for-tests.json")).thenReturn(missingResource);

        var localeController = new LocaleController(resourceLoader);
        var response = localeController.getLocale("zh", "missing-namespace-for-tests", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    private String asString(org.springframework.core.io.Resource resource) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }
}
