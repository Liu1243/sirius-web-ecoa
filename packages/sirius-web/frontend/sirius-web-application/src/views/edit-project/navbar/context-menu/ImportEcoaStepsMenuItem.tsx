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
import FileUploadIcon from '@mui/icons-material/FileUpload';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useContext, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ImportEcoaStepsMenuItemProps, ImportEcoaStepsMenuItemState } from './ImportEcoaStepsMenuItem.types';

/**
 * Menu item that imports an ECOA Steps ZIP archive into the current project.
 * Calls POST /api/edt/ecoa/import/{projectId} on the backend.
 */
export const ImportEcoaStepsMenuItem = ({ project, onClick }: ImportEcoaStepsMenuItemProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [state, setState] = useState<ImportEcoaStepsMenuItemState>({
    dialogOpen: false,
    uploading: false,
    message: null,
    success: null,
  });

  const handleMenuItemClick = () => {
    setState((prev) => ({ ...prev, dialogOpen: true, message: null, success: null }));
  };

  const handleClose = () => {
    setState((prev) => ({ ...prev, dialogOpen: false }));
    onClick();
  };

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;

    setState((prev) => ({ ...prev, uploading: true, message: null }));

    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch(`${httpOrigin}/api/edt/ecoa/import/${project.id}`, {
        method: 'POST',
        body: formData,
      });

      const result = await response.json();

      if (response.ok && result.success) {
        setState((prev) => ({
          ...prev,
          uploading: false,
          success: true,
          message: t('importEcoaSteps.success', { types: result.typesImported, services: result.servicesImported }),
        }));
        setTimeout(() => {
          window.location.reload();
        }, 2000);
      } else {
        setState((prev) => ({
          ...prev,
          uploading: false,
          success: false,
          message: t('importEcoaSteps.failure', { message: result.message || t('importEcoaSteps.unknownError') }),
        }));
      }
    } catch (e) {
      setState((prev) => ({
        ...prev,
        uploading: false,
        success: false,
        message: t('importEcoaSteps.networkError', { message: e instanceof Error ? e.message : String(e) }),
      }));
    }
  };

  return (
    <>
      <MenuItem onClick={handleMenuItemClick} data-testid="import-ecoa-steps-menu-item">
        <ListItemIcon>
          <FileUploadIcon />
        </ListItemIcon>
        <ListItemText primary={t('importEcoaSteps.menuItem')} />
      </MenuItem>

      <Dialog open={state.dialogOpen} onClose={handleClose} fullWidth maxWidth="sm">
        <DialogTitle>{t('importEcoaSteps.dialogTitle')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" gutterBottom>
            {t('importEcoaSteps.dialogDesc')}
          </Typography>
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip"
            style={{ marginTop: 16, display: 'block' }}
            data-testid="import-ecoa-steps-file-input"
          />
          {state.message && (
            <Typography variant="body2" color={state.success ? 'success.main' : 'error.main'} style={{ marginTop: 16 }}>
              {state.message}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={state.uploading}>
            {t('importEcoaSteps.cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleUpload}
            disabled={state.uploading || state.success === true}
            data-testid="import-ecoa-steps-upload-button">
            {state.uploading ? t('importEcoaSteps.uploading') : t('importEcoaSteps.upload')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
