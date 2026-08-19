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
package org.eclipse.sirius.web.application.controllers.componentcode;

import org.eclipse.sirius.web.AbstractIntegrationTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ComponentCodeVersionControllerSecurityTests extends AbstractIntegrationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Given a regular user, when calling createComponentCodeVersion, then it should return 403")
    @WithMockUser(username = "user", roles = "USER")
    public void givenRegularUser_whenCreateVersion_thenForbidden() {
        String mutation = """
            mutation {
              createComponentCodeVersion(input: {
                projectId: "test-project-id",
                componentId: "comp-1",
                componentName: "Component1",
                versionName: "v1.0",
                codeContent: "test code"
              }) {
                version { id }
              }
            }
            """;

        webTestClient.post()
            .uri("/api/graphql")
            .bodyValue("{ \"query\": \"" + mutation.replace("\"", "\\\"") + "\" }")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Given a regular user, when calling deleteComponentCodeVersion, then it should return 403")
    @WithMockUser(username = "user", roles = "USER")
    public void givenRegularUser_whenDeleteVersion_thenForbidden() {
        String mutation = """
            mutation {
              deleteComponentCodeVersion(input: {
                versionId: "test-version-id"
              }) {
                versionId
              }
            }
            """;

        webTestClient.post()
            .uri("/api/graphql")
            .bodyValue("{ \"query\": \"" + mutation.replace("\"", "\\\"") + "\" }")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Given a regular user, when calling createComponentCodeTag, then it should return 403")
    @WithMockUser(username = "user", roles = "USER")
    public void givenRegularUser_whenCreateTag_thenForbidden() {
        String mutation = """
            mutation {
              createComponentCodeTag(input: {
                projectId: "test-project-id",
                name: "stable",
                color: "#4CAF50"
              }) {
                tag { id }
              }
            }
            """;

        webTestClient.post()
            .uri("/api/graphql")
            .bodyValue("{ \"query\": \"" + mutation.replace("\"", "\\\"") + "\" }")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Given a regular user, when calling addTagToVersion, then it should return 403")
    @WithMockUser(username = "user", roles = "USER")
    public void givenRegularUser_whenAddTagToVersion_thenForbidden() {
        String mutation = """
            mutation {
              addTagToVersion(input: {
                versionId: "test-version-id",
                tagId: "test-tag-id"
              }) {
                version { id }
              }
            }
            """;

        webTestClient.post()
            .uri("/api/graphql")
            .bodyValue("{ \"query\": \"" + mutation.replace("\"", "\\\"") + "\" }")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Given a regular user, when calling removeTagFromVersion, then it should return 403")
    @WithMockUser(username = "user", roles = "USER")
    public void givenRegularUser_whenRemoveTagFromVersion_thenForbidden() {
        String mutation = """
            mutation {
              removeTagFromVersion(input: {
                versionId: "test-version-id",
                tagId: "test-tag-id"
              }) {
                version { id }
              }
            }
            """;

        webTestClient.post()
            .uri("/api/graphql")
            .bodyValue("{ \"query\": \"" + mutation.replace("\"", "\\\"") + "\" }")
            .exchange()
            .expectStatus().isForbidden();
    }
}
