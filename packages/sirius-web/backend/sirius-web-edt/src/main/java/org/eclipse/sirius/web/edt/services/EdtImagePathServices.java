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

import org.eclipse.sirius.components.core.api.IImagePathService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Used to support Edt custom images.
 *
 * @author managerial
 */
@Service
public class EdtImagePathServices implements IImagePathService {
    @Override
    public List<String> getPaths() {
        return List.of("/edt-representations/");
    }
}
