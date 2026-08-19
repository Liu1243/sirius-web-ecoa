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

import { useCurrentProject } from '@eclipse-sirius/sirius-web-application';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { Panel } from '@xyflow/react';
import { memo } from 'react';
import { makeStyles } from 'tss-react/mui';

const EDT_NATURE = 'siriusComponents://nature?kind=edt';

const useEdtDiagramInformationPanelStyles = makeStyles()((theme) => ({
  edtDiagramInformationPanel: {
    display: 'flex',
    flexDirection: 'column',
    padding: theme.spacing(1),
    maxWidth: '250px',
  },
  links: {
    display: 'flex',
    flexDirection: 'column',
    listStyle: 'disc',
    listStylePosition: 'inside',
    paddingTop: theme.spacing(1),
  },
}));

export const EdtDiagramInformationPanel = memo(() => {
  const { classes } = useEdtDiagramInformationPanelStyles();

  const { project } = useCurrentProject();

  if (project.natures.filter((nature) => nature.name === EDT_NATURE).length === 0) {
    return null;
  }
  return (
    <Panel position="bottom-left">
      <Paper className={classes.edtDiagramInformationPanel}>
        <Typography variant="subtitle2">Resources</Typography>
        <Typography variant="body2">Useful links for this project.</Typography>
        <ul className={classes.links}>
          <li>
            <Link href="https://github.com/NFTESG/edt" target="_blank" rel="noopener noreferrer">
              EDT on GitHub
            </Link>
          </li>
        </ul>
      </Paper>
    </Panel>
  );
});
