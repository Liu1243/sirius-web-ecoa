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
package org.eclipse.sirius.web.application.views.explorer.services;

import java.util.Objects;

import org.eclipse.sirius.components.collaborative.trees.api.IDropTreeItemHandler;
import org.eclipse.sirius.components.collaborative.trees.dto.DropTreeItemInput;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.trees.Tree;
import org.eclipse.sirius.web.application.views.explorer.services.api.IExplorerDropTreeItemExecutor;
import org.springframework.stereotype.Service;

/**
 * This class is used to provide the drop tree item of the explorer.
 *
 * @author frouene
 */
@Service
public class ExplorerDropTreeItemHandler implements IDropTreeItemHandler {

    private final IExplorerDropTreeItemExecutor explorerDropTreeItemExecutor;

    public ExplorerDropTreeItemHandler(IExplorerDropTreeItemExecutor explorerDropTreeItemExecutor) {
        this.explorerDropTreeItemExecutor = Objects.requireNonNull(explorerDropTreeItemExecutor);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, Tree tree) {
        return tree.getId().startsWith(ExplorerDescriptionProvider.PREFIX)
                && Objects.equals(tree.getDescriptionId(), ExplorerDescriptionProvider.DESCRIPTION_ID);
    }

    @Override
    public IStatus handle(IEditingContext editingContext, Tree tree, DropTreeItemInput input) {
        return this.explorerDropTreeItemExecutor.drop(editingContext, tree, input.droppedElementIds(), input.targetElementId(), input.index());
    }

}
