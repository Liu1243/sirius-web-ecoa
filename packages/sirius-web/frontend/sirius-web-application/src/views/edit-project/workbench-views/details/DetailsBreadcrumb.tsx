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
import { GQLForm } from '@eclipse-sirius/sirius-components-forms';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { makeStyles } from 'tss-react/mui';
import { useTreePathContext } from '../TreePathContext';

const useDetailsBreadcrumbStyles = makeStyles()((theme) => ({
  breadcrumb: {
    paddingLeft: theme.spacing(1),
    paddingRight: theme.spacing(1),
    paddingTop: theme.spacing(0.5),
    paddingBottom: theme.spacing(0.5),
  },
}));

export interface DetailsBreadcrumbProps {
  form: GQLForm;
}

export const DetailsBreadcrumb = ({ form }: DetailsBreadcrumbProps) => {
  const { classes } = useDetailsBreadcrumbStyles();
  const { treePathEntries } = useTreePathContext();

  const pathEntry = treePathEntries[form.targetObjectId];
  const ancestorLabels = pathEntry?.ancestorLabels ?? [];

  return (
    <div className={classes.breadcrumb}>
      <Breadcrumbs maxItems={8} aria-label="breadcrumb">
        {ancestorLabels.map((label, index) => (
          <Link key={`ancestor-${index}`} underline="hover" color="inherit" href="#">
            <Typography variant="body2">{label}</Typography>
          </Link>
        ))}
        <Typography variant="body2" color="text.primary">
          {form.targetObjectLabel}
        </Typography>
      </Breadcrumbs>
    </div>
  );
};
