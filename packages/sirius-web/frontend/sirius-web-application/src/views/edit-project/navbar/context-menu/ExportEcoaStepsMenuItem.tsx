/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/

import { ServerContext, ServerContextValue } from '@eclipse-sirius/sirius-components-core';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import { useContext } from 'react';
import { useTranslation } from 'react-i18next';
import { ExportEcoaStepsMenuItemProps } from './ExportEcoaStepsMenuItem.types';

/**
 * Menu item that exports the current project as an ECOA Steps ZIP archive.
 * Calls GET /api/edt/ecoa/export/{projectId} on the backend.
 */
export const ExportEcoaStepsMenuItem = ({ project, onClick }: ExportEcoaStepsMenuItemProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application');
  const href = `${httpOrigin}/api/edt/ecoa/export/${project.id}`;

  return (
    <MenuItem component="a" href={href} download onClick={onClick} data-testid="export-ecoa-steps-link">
      <ListItemIcon>
        <FileDownloadIcon />
      </ListItemIcon>
      <ListItemText primary={t('exportEcoaSteps.menuItem')} />
    </MenuItem>
  );
};
