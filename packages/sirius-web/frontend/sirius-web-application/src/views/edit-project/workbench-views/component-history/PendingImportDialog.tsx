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

import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { PendingImportDialogProps } from './PendingImportDialog.types';
import { usePendingImport } from './usePendingImport';

export const PendingImportDialog = ({ open, projectId, onClose, onConfirmed }: PendingImportDialogProps) => {
  const { pendingVersions, loading, confirm } = usePendingImport(projectId, open);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const hasInitialized = useRef(false);

  // Default select all when versions first load
  useEffect(() => {
    if (!loading && pendingVersions.length > 0 && !hasInitialized.current) {
      hasInitialized.current = true;
      setSelectedIds(pendingVersions.map((v) => v.id));
    }
  }, [loading, pendingVersions]);

  const renamedCount = pendingVersions.filter((v) => v.versionName.includes('-imported')).length;

  const toggleVersion = (id: string) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  const handleConfirm = async () => {
    const accepted = selectedIds;
    const rejected = pendingVersions.map((v) => v.id).filter((id) => !selectedIds.includes(id));
    const ok = await confirm(accepted, rejected);
    if (ok) {
      onConfirmed();
      onClose();
    }
  };

  const handleSkip = async () => {
    const allIds = pendingVersions.map((v) => v.id);
    await confirm([], allIds);
    onClose();
  };

  if (!open || (!loading && pendingVersions.length === 0)) {
    return null;
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>导入的组件源码版本</DialogTitle>
      <DialogContent>
        {loading ? (
          <Typography variant="body2">加载中...</Typography>
        ) : (
          <>
            <Typography variant="body2" gutterBottom>
              共导入 {pendingVersions.length} 个版本
              {renamedCount > 0 ? `，其中 ${renamedCount} 个发生重命名` : ''}
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
              取消勾选的版本将被丢弃
            </Typography>
            <List dense sx={{ maxHeight: 320, overflow: 'auto' }}>
              {pendingVersions.map((version) => {
                const isRenamed = version.versionName.includes('-imported');
                return (
                  <ListItem key={version.id} disablePadding>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={selectedIds.includes(version.id)}
                          onChange={() => toggleVersion(version.id)}
                          size="small"
                        />
                      }
                      label={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
                          <Typography variant="body2">
                            {version.componentName} &nbsp; {version.versionName}
                          </Typography>
                          {isRenamed && (
                            <Tooltip title="版本名与已有版本冲突，已自动重命名">
                              <WarningAmberIcon fontSize="small" color="warning" />
                            </Tooltip>
                          )}
                          {version.tags.map((tag) => (
                            <Chip
                              key={tag.id}
                              label={tag.name}
                              size="small"
                              sx={{ backgroundColor: tag.color, color: '#fff', height: 18, fontSize: 10 }}
                            />
                          ))}
                          <Typography variant="caption" color="text.secondary">
                            ({isRenamed ? '重命名' : '新增'})
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                );
              })}
            </List>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleSkip}>跳过</Button>
        <Button variant="contained" onClick={handleConfirm} disabled={loading}>
          确认导入
        </Button>
      </DialogActions>
    </Dialog>
  );
};
