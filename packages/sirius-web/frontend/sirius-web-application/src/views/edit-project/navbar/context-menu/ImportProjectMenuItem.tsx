/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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

import {ServerContext, ServerContextValue} from '@eclipse-sirius/sirius-components-core';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import {useContext, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {sendFormData} from '../../../../core/sendFile';
import {ImportProjectMenuItemProps, ImportProjectMenuItemState, ZipVersionEntry} from './ImportProjectMenuItem.types';
import {VersionItem} from './VersionPickerPanel.types';
import {VersionPickerPanel} from './VersionPickerPanel';

const INITIAL_STATE: ImportProjectMenuItemState = {
  dialogOpen: false,
  step: 'select-file',
  selectedFile: null,
  availableVersions: [],
  selectedVersionKeys: [],
  message: null,
};

/**
 * Menu item that imports a Sirius Web project ZIP into the current project.
 *
 * Two-step dialog:
 *  1. Select file → POST /preview to discover ComponentCode versions in the ZIP.
 *  2. (Optional) Select which versions to import → POST /import-zip to perform import.
 *
 * The selected File object is stored in state so it remains available when the
 * file input DOM element is hidden during the version-selection step.
 *
 * On success the page reloads so the explorer reflects the replaced document.
 */
export const ImportProjectMenuItem = ({ project, onClick }: ImportProjectMenuItemProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'importProjectMenuItem' });
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [state, setState] = useState<ImportProjectMenuItemState>(INITIAL_STATE);

  const handleMenuItemClick = () => {
    setState({ ...INITIAL_STATE, dialogOpen: true });
  };

  const handleClose = () => {
    setState(INITIAL_STATE);
    onClick();
  };

  // -------------------------------------------------------------------------
  // Step 1 – preview: read file from input, call /preview, get version list
  // -------------------------------------------------------------------------
  const handlePreview = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;

    // Store the file in state immediately so later steps can use it
    setState((prev) => ({ ...prev, selectedFile: file, step: 'importing', message: null }));

    try {
      const formData = new FormData();
      formData.append('file', file);

      // Use sendFormData (XHR-based) for consistent timeout / error handling with UploadProjectView.
      const json = await sendFormData(`${httpOrigin}/api/edt/project/import-zip/${project.id}/preview`, formData);

      if (!json.success) {
        setState((prev) => ({
          ...prev,
          step: 'error',
          message: t('previewError', { message: json.message || t('unknownError') }),
        }));
        return;
      }

      const versions: ZipVersionEntry[] = (json.versions ?? []).map((v: any) => ({
        componentId: v.componentId ?? '',
        componentName: v.componentName ?? v.componentId ?? '',
        versionName: v.versionName ?? '',
        commitMessage: v.commitMessage,
        author: v.author,
        tags: v.tags ?? [],
      }));

      // Pre-select all versions (mirrors the download dialog default)
      const allKeys = versions.map((v) => `${v.componentId}/${v.versionName}`);

      setState((prev) => ({
        ...prev,
        step: 'select-versions',
        availableVersions: versions,
        selectedVersionKeys: allKeys,
        message: null,
      }));
    } catch (e) {
      setState((prev) => ({
        ...prev,
        step: 'error',
        message: t('networkError', { message: e instanceof Error ? e.message : String(e) }),
      }));
    }
  };

  // -------------------------------------------------------------------------
  // Step 2 – import: use the file stored in state (not from the DOM input)
  // -------------------------------------------------------------------------
  const handleImport = async () => {
    // Use state.selectedFile — the DOM input element may be hidden and its
    // FileList reset; the File object stored in state remains valid.
    const file = state.selectedFile;
    if (!file) return;

    setState((prev) => ({ ...prev, step: 'importing', message: null }));

    try {
      const formData = new FormData();
      formData.append('file', file);
      state.selectedVersionKeys.forEach((key) => formData.append('selectedVersionIds', key));

      // Use sendFormData (XHR-based) for consistent timeout / error handling with UploadProjectView.
      const json = await sendFormData(`${httpOrigin}/api/edt/project/import-zip/${project.id}`, formData);

      if (!json.success) {
        setState((prev) => ({
          ...prev,
          step: 'error',
          message: t('failure', { message: json.message || t('unknownError') }),
        }));
        return;
      }

      // Auto-confirm all pending component code versions so they become visible immediately
      try {
        const pendingResp = await fetch(`${httpOrigin}/api/edt/component-code/pending/${project.id}`);
        if (pendingResp.ok) {
          const pendingData = await pendingResp.json();
          const pendingVersionIds: string[] = (pendingData.versions ?? []).map((v: any) => v.id);
          if (pendingVersionIds.length > 0) {
            await fetch(`${httpOrigin}/api/edt/component-code/confirm/${project.id}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ acceptedVersionIds: pendingVersionIds, rejectedVersionIds: [] }),
            });
          }
        }
      } catch (_) {
        // confirm failure is non-fatal; versions remain pending but import succeeded
      }

      setState((prev) => ({
        ...prev,
        step: 'done',
        message: t('success'),
      }));
      setTimeout(() => {
        window.location.reload();
      }, 1500);
    } catch (e) {
      setState((prev) => ({
        ...prev,
        step: 'error',
        message: t('networkError', { message: e instanceof Error ? e.message : String(e) }),
      }));
    }
  };

  // -------------------------------------------------------------------------
  // Render helpers
  // -------------------------------------------------------------------------
  const renderContent = () => {
    const { step, availableVersions, selectedVersionKeys } = state;

    if (step === 'importing') {
      return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
          <CircularProgress size={20} />
          <Typography variant="body2">{t('uploading')}</Typography>
        </Box>
      );
    }

    if (step === 'select-versions') {
      if (availableVersions.length === 0) {
        return (
          <Typography variant="body2" color="text.secondary">
            {t('noVersions')}
          </Typography>
        );
      }
      // Map ZipVersionEntry[] → VersionItem[] for VersionPickerPanel.
      const items: VersionItem[] = availableVersions.map((v) => ({
        componentId: v.componentId,
        componentName: v.componentName,
        versionName: v.versionName,
        author: v.author,
        commitMessage: v.commitMessage,
        tags: v.tags,
      }));
      return (
        <VersionPickerPanel
          items={items}
          selectedKeys={selectedVersionKeys}
          onChange={(keys) => setState((prev) => ({ ...prev, selectedVersionKeys: keys }))}
        />
      );
    }

    if (step === 'done') {
      return (
        <Typography variant="body2" color="success.main">
          {state.message}
        </Typography>
      );
    }

    if (step === 'error') {
      return (
        <Typography variant="body2" color="error.main">
          {state.message}
        </Typography>
      );
    }

    // step === 'select-file' (default)
    return (
      <>
        <Typography variant="body2" gutterBottom>
          {t('dialogDesc')}
        </Typography>
        <input
          ref={fileInputRef}
          type="file"
          accept=".zip"
          style={{ marginTop: 16, display: 'block' }}
          data-testid="import-project-file-input"
        />
      </>
    );
  };

  const renderActions = () => {
    const { step } = state;

    if (step === 'select-file') {
      return (
        <>
          <Button onClick={handleClose}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handlePreview} data-testid="import-project-next-button">
            {t('next')}
          </Button>
        </>
      );
    }

    if (step === 'importing') {
      return <Button disabled>{t('cancel')}</Button>;
    }

    if (step === 'select-versions') {
      return (
        <>
          <Button onClick={handleClose}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleImport} data-testid="import-project-upload-button">
            {t('upload')}
          </Button>
        </>
      );
    }

    // done / error
    return <Button onClick={handleClose}>{t('close')}</Button>;
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <>
      <MenuItem onClick={handleMenuItemClick} data-testid="import-project-menu-item">
        <ListItemIcon>
          <CloudUploadIcon />
        </ListItemIcon>
        <ListItemText primary={t('menuItem')} />
      </MenuItem>

      <Dialog open={state.dialogOpen} onClose={handleClose} fullWidth maxWidth="md">
        <DialogTitle>{t('dialogTitle')}</DialogTitle>
        <DialogContent>{renderContent()}</DialogContent>
        <DialogActions>{renderActions()}</DialogActions>
      </Dialog>
    </>
  );
};
