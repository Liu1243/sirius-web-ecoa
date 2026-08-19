/*******************************************************************************
 * Copyright (c) 2025, 2026 Obeo.
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

import { ServerContext, ServerContextValue } from '@eclipse-sirius/sirius-components-core';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { useContext, useState } from 'react';
import { VersionPickerPanel } from './VersionPickerPanel';
import { VersionItem } from './VersionPickerPanel.types';
import { DownloadProjectDialogProps } from './DownloadProjectDialog.types';
import { useDownloadProject } from './useDownloadProject';

export const DownloadProjectDialog = ({ open, projectId, projectName, onClose }: DownloadProjectDialogProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { versions, loading, downloadWithVersions } = useDownloadProject(projectId, httpOrigin);

  // selectedKeys uses "componentId/versionName" format (VersionPickerPanel contract).
  // On download, we convert back to version UUIDs required by the API.
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);

  // Pre-select all versions when the dialog finishes opening.
  const handleEntered = () => {
    setSelectedKeys(versions.map((v) => `${v.componentId}/${v.versionName}`));
  };

  const handleDownload = async () => {
    // Map selectedKeys → version UUIDs for the download API.
    const selectedVersionIds = versions
      .filter((v) => selectedKeys.includes(`${v.componentId}/${v.versionName}`))
      .map((v) => v.id);
    await downloadWithVersions(selectedVersionIds, projectName);
    onClose();
  };

  // Map VersionForDownload[] → VersionItem[] for the panel.
  const items: VersionItem[] = versions.map((v) => ({
    componentId: v.componentId,
    componentName: v.componentName,
    versionName: v.versionName,
    tags: v.tags,
    createdAt: v.createdAt,
  }));

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth TransitionProps={{ onEntered: handleEntered }}>
      <DialogTitle>下载项目</DialogTitle>
      <DialogContent>
        {loading ? (
          <Typography variant="body2">加载版本列表...</Typography>
        ) : versions.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            该项目暂无组件源码版本，将直接下载项目文件。
          </Typography>
        ) : (
          <VersionPickerPanel items={items} selectedKeys={selectedKeys} onChange={setSelectedKeys} />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={handleDownload}>
          下载
        </Button>
      </DialogActions>
    </Dialog>
  );
};
