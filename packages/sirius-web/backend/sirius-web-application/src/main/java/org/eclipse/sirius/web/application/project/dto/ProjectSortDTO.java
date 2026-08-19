/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.application.project.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The sorting configuration for the project browser.
 *
 * @author codex
 */
public record ProjectSortDTO(
        @NotNull ProjectSortField field,
        @NotNull ProjectSortDirection direction) {

    public static ProjectSortDTO defaultSort() {
        return new ProjectSortDTO(ProjectSortField.NAME, ProjectSortDirection.ASC);
    }
}
