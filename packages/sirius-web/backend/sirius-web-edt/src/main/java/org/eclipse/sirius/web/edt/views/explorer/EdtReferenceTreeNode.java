/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.views.explorer;

import java.util.List;
import java.util.Objects;

/**
 * Wrapper node used to show the same semantic element in an alternate tree branch
 * without reusing the same tree item identifier.
 */
public record EdtReferenceTreeNode(
        String id,
        Object parent,
        Object target,
        String label,
        List<String> imagePaths
) {
    public static final String KIND = "siriusWeb://referenceNode";

    public EdtReferenceTreeNode {
        Objects.requireNonNull(id);
        Objects.requireNonNull(parent);
        Objects.requireNonNull(target);
        Objects.requireNonNull(label);
        Objects.requireNonNull(imagePaths);
    }
}
