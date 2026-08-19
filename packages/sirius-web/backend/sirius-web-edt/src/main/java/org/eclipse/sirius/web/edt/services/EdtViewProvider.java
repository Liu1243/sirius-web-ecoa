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

import java.util.UUID;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.sirius.components.emf.ResourceMetadataAdapter;
import org.eclipse.sirius.components.emf.services.IDAdapter;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.DefaultColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.web.edt.representations.componentimplementationdiagram.EdtComponentImplementationDiagramDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.compositediagram.EdtCompositeDiagramDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.EdtLogicalSystemDiagramDescriptionProvider;
import org.eclipse.sirius.web.edt.services.api.IEdtViewProvider;
import org.springframework.stereotype.Service;

/**
 * Used to create the view model used by Edt.
 *
 * @author managerial
 */
@Service
public class EdtViewProvider implements IEdtViewProvider {

    @Override
    public View create() {
        var colorPalette = new EdtColorPaletteProvider().getColorPalette();
        var view = new ViewBuilders().newView()
                .colorPalettes(
                        colorPalette
                )
                .build();

        IColorProvider colorProvider = new DefaultColorProvider(view);
        // Add diagram descriptions here
        var compositeDiagramDescription = new EdtCompositeDiagramDescriptionProvider().create(colorProvider);
        var logicalSystemDiagramDescription = new EdtLogicalSystemDiagramDescriptionProvider().create(colorProvider);
        var componentImplementationDiagramDescription = new EdtComponentImplementationDiagramDescriptionProvider().create(colorProvider);

        view.getDescriptions().add(compositeDiagramDescription);
        view.getDescriptions().add(logicalSystemDiagramDescription);
        view.getDescriptions().add(componentImplementationDiagramDescription);

        view.eAllContents().forEachRemaining(eObject -> {
            eObject.eAdapters().add(new IDAdapter(UUID.nameUUIDFromBytes(EcoreUtil.getURI(eObject).toString().getBytes())));
        });

        String resourcePath = UUID.nameUUIDFromBytes("EdtView".getBytes()).toString();
        JsonResource resource = new JSONResourceFactory().createResourceFromPath(resourcePath);
        resource.eAdapters().add(new ResourceMetadataAdapter("EdtView"));
        resource.getContents().add(view);

        return view;
    }
}
