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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.core.api.ChildCreationDescription;
import org.eclipse.sirius.components.core.api.IDefaultEditService;
import org.eclipse.sirius.components.core.api.IEditServiceDelegate;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.web.edt.views.explorer.EdtExplorerServices;
import org.eclipse.sirius.web.edt.views.explorer.EdtVirtualGroupNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Edit service delegate that handles child creation for virtual group nodes.
 * When a virtual folder supports child creation, this delegate provides the
 * corresponding ChildCreationDescription(s) and delegates the actual creation
 * to EdtCreationService using the virtual node's parent (real EMF object).
 *
 * @author EDT Team
 */
@Service
public class EdtEditServiceDelegate implements IEditServiceDelegate {

    private final EdtExplorerServices edtExplorerServices;

    private final EdtCreationService edtCreationService;

    private final IDefaultEditService defaultEditService;

    public EdtEditServiceDelegate(@Lazy EdtExplorerServices edtExplorerServices, EdtCreationService edtCreationService, IDefaultEditService defaultEditService) {
        this.edtExplorerServices = Objects.requireNonNull(edtExplorerServices);
        this.edtCreationService = Objects.requireNonNull(edtCreationService);
        this.defaultEditService = Objects.requireNonNull(defaultEditService);
    }

    @Override
    public boolean canHandle(Object object) {
        return object instanceof EdtVirtualGroupNode;
    }

    @Override
    public boolean canHandle(IEditingContext editingContext) {
        return true;
    }

    @Override
    public List<ChildCreationDescription> getRootCreationDescriptions(IEditingContext editingContext, String domainId, boolean suggested, String referenceKind) {
        return this.defaultEditService.getRootCreationDescriptions(editingContext, domainId, suggested, referenceKind);
    }

    @Override
    public List<ChildCreationDescription> getChildCreationDescriptions(IEditingContext editingContext, String containerId, String referenceKind) {
        EdtVirtualGroupNode virtualNode = this.edtExplorerServices.getVirtualNode(containerId);
        if (virtualNode != null) {
            // Check for single child type
            if (virtualNode.supportsChildCreation()) {
                ChildCreationDescription description = virtualNode.getChildCreationDescription();
                if (description != null) {
                    return List.of(description);
                }
            }
            // Check for multi-type folders (Operations, Pinfo)
            if (containerId != null && containerId.endsWith(EdtVirtualGroupNode.MODULE_TYPE_OPERATIONS_ID_SUFFIX)) {
                return listDescriptions(EdtVirtualGroupNode.MODULE_OPERATION_CHILD_TYPES);
            }
            if (containerId != null && containerId.endsWith(EdtVirtualGroupNode.MODULE_TYPE_PINFO_ID_SUFFIX)) {
                return listDescriptions(EdtVirtualGroupNode.MODULE_PINFO_CHILD_TYPES);
            }
        }
        return this.defaultEditService.getChildCreationDescriptions(editingContext, containerId, referenceKind);
    }

    private static List<ChildCreationDescription> listDescriptions(String[] typeIds) {
        return java.util.Arrays.stream(typeIds)
                .map(id -> new ChildCreationDescription(id, id, List.of()))
                .toList();
    }

    @Override
    public Optional<Object> createChild(IEditingContext editingContext, Object object, String childCreationDescriptionId) {
        if (object instanceof EdtVirtualGroupNode virtualNode) {
            Object parent = virtualNode.parent();
            if (parent instanceof EObject) {
                return Optional.ofNullable(this.edtCreationService.createChild(parent, childCreationDescriptionId));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Object> createRootObject(IEditingContext editingContext, UUID documentId, String domainId, String rootObjectCreationDescriptionId) {
        return this.defaultEditService.createRootObject(editingContext, documentId, domainId, rootObjectCreationDescriptionId);
    }

    @Override
    public void delete(Object object) {
        this.defaultEditService.delete(object);
    }
}
