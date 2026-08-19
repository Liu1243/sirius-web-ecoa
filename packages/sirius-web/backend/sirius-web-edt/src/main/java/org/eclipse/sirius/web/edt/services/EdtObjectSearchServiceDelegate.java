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
package org.eclipse.sirius.web.edt.services;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchServiceDelegate;
import org.eclipse.sirius.web.edt.views.explorer.EdtExplorerServices;
import org.eclipse.sirius.web.edt.views.explorer.EdtVirtualGroupNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Object search service delegate that can find virtual group nodes.
 * This allows the framework to resolve virtual node IDs to EdtVirtualGroupNode objects,
 * which is required for context menu and child creation operations.
 *
 * @author EDT Team
 */
@Service
public class EdtObjectSearchServiceDelegate implements IObjectSearchServiceDelegate {

    private final EdtExplorerServices edtExplorerServices;

    public EdtObjectSearchServiceDelegate(@Lazy EdtExplorerServices edtExplorerServices) {
        this.edtExplorerServices = Objects.requireNonNull(edtExplorerServices);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, String objectId) {
        return objectId != null && objectId.contains("#") && this.edtExplorerServices.getVirtualNode(objectId) != null;
    }

    @Override
    public Optional<Object> getObject(IEditingContext editingContext, String objectId) {
        EdtVirtualGroupNode virtualNode = this.edtExplorerServices.getVirtualNode(objectId);
        if (virtualNode != null) {
            return Optional.of(virtualNode);
        }
        return Optional.empty();
    }
}
