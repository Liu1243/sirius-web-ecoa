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

import org.eclipse.sirius.components.core.api.ILabelServiceDelegate;
import org.eclipse.sirius.components.core.api.labels.StyledString;
import org.springframework.stereotype.Service;

/**
 * Label service for reference wrapper nodes used in alternate explorer branches.
 */
@Service
public class EdtReferenceTreeNodeLabelService implements ILabelServiceDelegate {

    @Override
    public boolean canHandle(Object object) {
        return object instanceof EdtReferenceTreeNode;
    }

    @Override
    public StyledString getStyledLabel(Object object) {
        if (object instanceof EdtReferenceTreeNode referenceTreeNode) {
            return StyledString.of(referenceTreeNode.label());
        }
        return StyledString.of("");
    }

    @Override
    public List<String> getImagePaths(Object object) {
        if (object instanceof EdtReferenceTreeNode referenceTreeNode) {
            return referenceTreeNode.imagePaths();
        }
        return List.of();
    }
}
