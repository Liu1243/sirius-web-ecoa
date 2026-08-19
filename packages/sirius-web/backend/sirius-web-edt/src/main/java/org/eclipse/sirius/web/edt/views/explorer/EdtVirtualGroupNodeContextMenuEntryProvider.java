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
package org.eclipse.sirius.web.edt.views.explorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.collaborative.trees.api.ITreeItemContextMenuEntryProvider;
import org.eclipse.sirius.components.collaborative.trees.dto.ITreeItemContextMenuEntry;
import org.eclipse.sirius.components.collaborative.trees.dto.SingleClickTreeItemContextMenuEntry;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.trees.Tree;
import org.eclipse.sirius.components.trees.TreeItem;
import org.eclipse.sirius.components.trees.description.TreeDescription;
import org.eclipse.sirius.web.application.views.explorer.services.ExplorerDescriptionProvider;
import org.eclipse.sirius.web.application.views.explorer.services.ExplorerTreeItemContextMenuEntryProvider;
import org.springframework.stereotype.Service;

/**
 * Provides context menu entries for virtual group nodes in the EDT explorer.
 * Adds "New Object" entry for virtual folders that support child creation.
 *
 * @author EDT Team
 */
@Service
public class EdtVirtualGroupNodeContextMenuEntryProvider implements ITreeItemContextMenuEntryProvider {

    private final IObjectSearchService objectSearchService;

    public EdtVirtualGroupNodeContextMenuEntryProvider(IObjectSearchService objectSearchService) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem) {
        return tree.getId().startsWith(ExplorerDescriptionProvider.PREFIX)
                && Objects.equals(tree.getDescriptionId(), ExplorerDescriptionProvider.DESCRIPTION_ID);
    }

    @Override
    public List<ITreeItemContextMenuEntry> getTreeItemContextMenuEntries(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem) {
        List<ITreeItemContextMenuEntry> result = new ArrayList<>();

        var optionalObject = this.objectSearchService.getObject(editingContext, treeItem.getId());
        if (optionalObject.isPresent() && optionalObject.get() instanceof EdtVirtualGroupNode virtualNode) {
            if (virtualNode.supportsChildCreation()) {
                result.add(new SingleClickTreeItemContextMenuEntry(
                        ExplorerTreeItemContextMenuEntryProvider.NEW_OBJECT, "", List.of(), false));
            }
        }

        return result;
    }
}
