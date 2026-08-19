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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.core.api.IContentService;
import org.eclipse.sirius.components.core.api.IDefaultObjectSearchService;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.core.api.ILabelService;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.core.api.IReadOnlyObjectPredicate;
import org.eclipse.sirius.web.application.views.explorer.services.ExplorerServices;
import org.eclipse.sirius.web.application.views.explorer.services.api.IExplorerServices;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationMetadataSearchService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Extended explorer services for EDT projects.
 * This service decorates the default ExplorerServices and directly uses EMF objects.
 *
 * @author EDT Team
 */
@Service
@Primary
public class EdtExplorerServices implements IExplorerServices {

    private final ExplorerServices delegateServices;

    /**
     * Cache for virtual group nodes, keyed by their ID.
     * This allows getTreeItemObject to find virtual nodes by ID.
     */
    private final Map<String, EdtVirtualGroupNode> virtualGroupNodeCache = new ConcurrentHashMap<>();

    /**
     * Cache for reference wrapper nodes, keyed by their unique tree item ID.
     */
    private final Map<String, EdtReferenceTreeNode> referenceTreeNodeCache = new ConcurrentHashMap<>();

    public EdtExplorerServices(
            IIdentityService identityService,
            IObjectSearchService objectSearchService,
            ILabelService labelService,
            IContentService contentService,
            IRepresentationMetadataSearchService representationMetadataSearchService,
            IReadOnlyObjectPredicate readOnlyObjectPredicate,
            IDefaultObjectSearchService defaultObjectSearchService
    ) {
        this.delegateServices = new ExplorerServices(
                identityService,
                objectSearchService,
                labelService,
                contentService,
                representationMetadataSearchService,
                readOnlyObjectPredicate,
                defaultObjectSearchService
        );
    }

    /**
     * Registers a virtual group node in the cache so it can be retrieved by ID.
     */
    public void registerVirtualNode(EdtVirtualGroupNode node) {
        this.virtualGroupNodeCache.put(node.id(), node);
    }

    /**
     * Retrieves a virtual group node by its ID.
     */
    public EdtVirtualGroupNode getVirtualNode(String id) {
        return this.virtualGroupNodeCache.get(id);
    }

    /**
     * Registers a reference wrapper node in the cache so it can be retrieved by ID.
     */
    public void registerReferenceNode(EdtReferenceTreeNode node) {
        this.referenceTreeNodeCache.put(node.id(), node);
    }

    @Override
    public String getTreeItemId(Object self) {
        if (self instanceof EdtVirtualGroupNode virtualNode) {
            return virtualNode.id();
        }
        if (self instanceof EdtReferenceTreeNode referenceNode) {
            return referenceNode.id();
        }
        return this.delegateServices.getTreeItemId(self);
    }

    @Override
    public String getKind(Object self) {
        if (self instanceof EdtVirtualGroupNode) {
            return EdtVirtualGroupNode.KIND;
        }
        if (self instanceof EdtReferenceTreeNode referenceNode) {
            return this.delegateServices.getKind(referenceNode.target());
        }
        return this.delegateServices.getKind(self);
    }

    @Override
    public boolean isDeletable(Object self) {
        if (self instanceof EdtVirtualGroupNode) {
            return false;
        }
        if (self instanceof EdtReferenceTreeNode) {
            return false;
        }
        return this.delegateServices.isDeletable(self);
    }

    @Override
    public boolean isSelectable(Object self) {
        if (self instanceof EdtVirtualGroupNode) {
            return false;
        }
        if (self instanceof EdtReferenceTreeNode) {
            return true;
        }
        return this.delegateServices.isSelectable(self);
    }

    @Override
    public Object getTreeItemObject(String treeItemId, IEditingContext editingContext) {
        // Check if it's a virtual node first
        EdtVirtualGroupNode virtualNode = this.virtualGroupNodeCache.get(treeItemId);
        if (virtualNode != null) {
            return virtualNode;
        }
        EdtReferenceTreeNode referenceNode = this.referenceTreeNodeCache.get(treeItemId);
        if (referenceNode != null) {
            return referenceNode.target();
        }
        return this.delegateServices.getTreeItemObject(treeItemId, editingContext);
    }

    @Override
    public Object getParent(Object self, String treeItemId, IEditingContext editingContext) {
        EdtReferenceTreeNode referenceNode = this.referenceTreeNodeCache.get(treeItemId);
        if (referenceNode != null) {
            return referenceNode.parent();
        }
        if (self instanceof EdtVirtualGroupNode virtualNode) {
            return virtualNode.parent();
        }
        return this.delegateServices.getParent(self, treeItemId, editingContext);
    }

    @Override
    public boolean hasChildren(Object self, IEditingContext editingContext, List<RepresentationMetadata> existingRepresentations) {
        if (self instanceof EdtVirtualGroupNode virtualNode) {
            return virtualNode.hasChildren();
        }
        if (self instanceof EdtReferenceTreeNode) {
            return false;
        }
        return this.delegateServices.hasChildren(self, editingContext, existingRepresentations);
    }

    @Override
    public List<Object> getDefaultChildren(Object self, IEditingContext editingContext, List<String> expandedIds, List<RepresentationMetadata> existingRepresentations) {
        if (self instanceof EdtVirtualGroupNode virtualNode) {
            if (expandedIds.contains(virtualNode.id())) {
                return new java.util.ArrayList<>(virtualNode.children());
            }
            return new java.util.ArrayList<>();
        }
        if (self instanceof EdtReferenceTreeNode) {
            return new java.util.ArrayList<>();
        }
        return this.delegateServices.getDefaultChildren(self, editingContext, expandedIds, existingRepresentations);
    }

    @Override
    public List<Resource> getDefaultElements(IEditingContext editingContext) {
        return this.delegateServices.getDefaultElements(editingContext);
    }
}
