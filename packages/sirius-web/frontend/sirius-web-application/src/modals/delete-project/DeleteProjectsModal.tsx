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
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import { useTranslation } from 'react-i18next';
import { DeleteProjectsModalProps } from './DeleteProjectsModal.types';

export const DeleteProjectsModal = ({ projectIds, onCancel, onSuccess }: DeleteProjectsModalProps) => {
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'deleteProjectModal' });

  const onDeleteProjects = (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => {
    event.preventDefault();
    onSuccess();
  };

  return (
    <Dialog open onClose={onCancel} aria-labelledby="dialog-title" maxWidth="xs" fullWidth>
      <DialogTitle id="dialog-title">{t('titleMultiple', { count: projectIds.length })}</DialogTitle>
      <DialogContent>
        <DialogContentText>{t('contentMultiple')}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={onDeleteProjects} color="primary" data-testid="delete-projects">
          {t('submit')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
