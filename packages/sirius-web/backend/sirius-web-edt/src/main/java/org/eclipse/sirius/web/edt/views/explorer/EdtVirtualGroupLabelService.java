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

import org.eclipse.sirius.components.core.api.ILabelServiceDelegate;
import org.eclipse.sirius.components.core.api.labels.StyledString;
import org.eclipse.sirius.components.core.api.labels.StyledStringFragment;
import org.eclipse.sirius.components.core.api.labels.StyledStringFragmentStyle;
import org.springframework.stereotype.Service;

/**
 * Label service delegate for EDT virtual group nodes.
 * Provides labels and icons for virtual grouping nodes in the explorer tree.
 *
 * @author EDT Team
 */
@Service
public class EdtVirtualGroupLabelService implements ILabelServiceDelegate {

    private static final String FOLDER_ICON = "/icons/Folder.svg";

    @Override
    public boolean canHandle(Object object) {
        return object instanceof EdtVirtualGroupNode;
    }

    @Override
    public StyledString getStyledLabel(Object object) {
        if (object instanceof EdtVirtualGroupNode virtualNode) {
            String label = virtualNode.label();
            if (EdtVirtualGroupNode.BASIC_TYPES_LABEL.equals(label) 
                    || EdtVirtualGroupNode.ECOA_PREDEFINED_TYPES_LABEL.equals(label)) {
                
                var style = StyledStringFragmentStyle.newDefaultStyledStringFragmentStyle()
                        .foregroundColor("#000001") // Almost pure black to avoid default value trap
                        .italic(false)
                        .struckOut(false)
                        .build();
                var fragment = new StyledStringFragment(label, style);
                return new StyledString(List.of(fragment));
            }
            return StyledString.of(label);
        }
        return StyledString.of("");
    }

    @Override
    public List<String> getImagePaths(Object object) {
        if (object instanceof EdtVirtualGroupNode) {
            return List.of(FOLDER_ICON);
        }
        return List.of();
    }
}
