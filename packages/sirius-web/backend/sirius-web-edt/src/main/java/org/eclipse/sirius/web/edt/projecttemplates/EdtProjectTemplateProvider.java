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
package org.eclipse.sirius.web.edt.projecttemplates;

import org.eclipse.sirius.web.application.project.services.api.IProjectTemplateProvider;
import org.eclipse.sirius.web.application.project.services.api.ProjectTemplate;
import org.eclipse.sirius.web.application.project.services.api.ProjectTemplateNature;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Used to provide the edt project template.
 *
 * @author sbegaudeau
 */
@Service
public class EdtProjectTemplateProvider implements IProjectTemplateProvider {

    public static final String EMPTY_PROJECT_TEMPLATE_ID = "edt-empty";

    public static final String EXAMPLE_PROJECT_TEMPLATE_ID = "edt-example";

    public static final String EDT_NATURE = "siriusComponents://nature?kind=edt";

    @Override
    public List<ProjectTemplate> getProjectTemplates() {
        return List.of(new ProjectTemplate(EMPTY_PROJECT_TEMPLATE_ID, "空项目", "/project-templates/edt-blank.png", List.of(new ProjectTemplateNature(EDT_NATURE)))
        // new ProjectTemplate(EXAMPLE_PROJECT_TEMPLATE_ID, "EDT - example", "/project-templates/edt-blank.png",
        // List.of(new ProjectTemplateNature(EDT_NATURE)))
        );
    }
}
