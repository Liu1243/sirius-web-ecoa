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

import GetAppIcon from '@mui/icons-material/GetApp';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import { useState } from 'react';
import { DownloadProjectMenuItemProps } from './DownloadProjectMenuItem.types';
import { DownloadProjectDialog } from './DownloadProjectDialog';

export const DownloadProjectMenuItem = ({ project, name, onClick }: DownloadProjectMenuItemProps) => {
  const [dialogOpen, setDialogOpen] = useState(false);

  const handleMenuItemClick = () => {
    setDialogOpen(true);
  };

  const handleDialogClose = () => {
    setDialogOpen(false);
    onClick();
  };

  const projectDisplayName = name || project.id;

  return (
    <>
      <MenuItem onClick={handleMenuItemClick} data-testid="download-link">
        <ListItemIcon>
          <GetAppIcon />
        </ListItemIcon>
        <ListItemText primary="下载项目文件" />
      </MenuItem>
      <DownloadProjectDialog
        open={dialogOpen}
        projectId={project.id}
        projectName={projectDisplayName}
        onClose={handleDialogClose}
      />
    </>
  );
};
