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

import { useComponents } from '@eclipse-sirius/sirius-components-core';
import Typography from '@mui/material/Typography';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';
import { CreateProjectAreaProps } from './CreateProjectArea.types';
import { createProjectAreaCardExtensionPoint } from './CreateProjectAreaExtensionPoints';
import { NewProjectCard } from './NewProjectCard';
import { ShowAllProjectTemplatesCard } from './ShowAllProjectTemplatesCard';
import { useProjectTemplates } from './useProjectTemplates';
import { GQLProjectTemplate, ProjectTemplateContext } from './useProjectTemplates.types';

const useCreateProjectAreaStyles = makeStyles()((theme) => ({
  createProjectArea: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(5),
  },
  header: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  content: {
    display: 'grid',
    gap: theme.spacing(1),
    gridTemplateColumns: 'repeat(6, 1fr)',
  },
}));

export const CreateProjectArea = ({}: CreateProjectAreaProps) => {
  const { classes } = useCreateProjectAreaStyles();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'createProjectArea' });

  const createProjectAreaCards = useComponents(createProjectAreaCardExtensionPoint);
  const { data } = useProjectTemplates(
    0,
    100, // Get all templates to filter them
    ProjectTemplateContext.PROJECT_BROWSER
  );
  const allProjectTemplates: GQLProjectTemplate[] = data?.viewer.projectTemplates.edges.map((edge) => edge.node) ?? [];

  // Filter to only show EDT-Blank and EDT-example templates
  const EDT_TEMPLATE_IDS = ['edt-empty', 'edt-example'];
  const displayedTemplates = allProjectTemplates.filter((template) => EDT_TEMPLATE_IDS.includes(template.id));

  // Check if there are other templates besides EDT templates
  const hasOtherTemplates = allProjectTemplates.some(
    (template) =>
      !EDT_TEMPLATE_IDS.includes(template.id) &&
      template.id !== 'upload-project' &&
      template.id !== 'browse-all-project-templates'
  );

  return (
    <div className={classes.createProjectArea}>
      <div className={classes.header}>
        <Typography variant="h4">{t('createNewProject')}</Typography>
      </div>
      <div className={classes.content}>
        {displayedTemplates.map((template) => (
          <NewProjectCard key={template.id} template={template} />
        ))}
        {hasOtherTemplates && <ShowAllProjectTemplatesCard />}
        {createProjectAreaCards.map(({ Component: Card }, index) => (
          <Card key={index} />
        ))}
      </div>
    </div>
  );
};
