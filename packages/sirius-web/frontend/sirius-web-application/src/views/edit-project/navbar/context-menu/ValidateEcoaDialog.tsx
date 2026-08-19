/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { useContext, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';

interface ValidateEcoaDialogProps {
  open: boolean;
  project: { id: string };
  onClose: () => void;
}

type ValidateStatus = 'IDLE' | 'VALIDATING' | 'PASSED' | 'FAILED' | 'ERROR';

interface ValidateState {
  status: ValidateStatus;
  logs: string[];
  errorMessage: string | null;
}

const useStyles = makeStyles()((theme) => ({
  logPanel: {
    backgroundColor: '#1e1e1e',
    color: '#d4d4d4',
    fontFamily: '"Cascadia Code", "Fira Code", "Consolas", monospace',
    fontSize: '0.78rem',
    padding: theme.spacing(1.5),
    borderRadius: theme.shape.borderRadius,
    height: 220,
    overflowY: 'auto',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-all',
    lineHeight: 1.6,
    marginTop: theme.spacing(1),
  },
  logLine: {
    margin: 0,
    '&:not(:last-child)': { marginBottom: 2 },
  },
  logError: { color: '#f48771' },
  logSuccess: { color: '#4ec9b0' },
  logInfo: { color: '#9cdcfe' },
  logWarning: { color: '#dcdcaa' },
}));

function getLogColor(line: string): string {
  if (line.includes('[ERROR]') || line.includes('failed ✗')) return 'logError';
  if (line.includes('[SUCCESS]') || line.includes('passed ✓')) return 'logSuccess';
  if (line.includes('[WARN]')) return 'logWarning';
  return 'logInfo';
}

const initialState: ValidateState = { status: 'IDLE', logs: [], errorMessage: null };

export const ValidateEcoaDialog = ({ open, project, onClose }: ValidateEcoaDialogProps) => {
  const { classes, cx } = useStyles();
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'validateEcoa' });
  const [state, setState] = useState<ValidateState>(initialState);
  const logPanelRef = useRef<HTMLDivElement>(null);

  // Auto-scroll logs
  useEffect(() => {
    if (logPanelRef.current) {
      logPanelRef.current.scrollTop = logPanelRef.current.scrollHeight;
    }
  }, [state.logs]);

  // Auto-run validation when dialog opens
  useEffect(() => {
    if (open) {
      setState(initialState);
      runValidation();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const runValidation = async () => {
    setState({ status: 'VALIDATING', logs: [t('logPanel.starting')], errorMessage: null });
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/validate/${project.id}`, { method: 'POST' });
      if (!resp.ok) {
        const text = await resp.text();
        setState({ status: 'ERROR', logs: [`[VALIDATE][ERROR] HTTP ${resp.status}: ${text}`], errorMessage: text });
        return;
      }
      const data = await resp.json();
      const passed: boolean = data.success === true;
      setState({
        status: passed ? 'PASSED' : 'FAILED',
        logs: Array.isArray(data.logs) ? data.logs : [passed ? t('logPanel.passed') : t('logPanel.failed')],
        errorMessage: null,
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setState({ status: 'ERROR', logs: [`[VALIDATE][ERROR] ${msg}`], errorMessage: msg });
    }
  };

  const isRunning = state.status === 'VALIDATING';

  const statusIcon =
    state.status === 'VALIDATING' ? (
      <CircularProgress size={18} thickness={5} />
    ) : state.status === 'PASSED' ? (
      <CheckCircleIcon color="success" fontSize="small" />
    ) : state.status === 'FAILED' || state.status === 'ERROR' ? (
      <ErrorIcon color="error" fontSize="small" />
    ) : null;

  const statusLabel =
    state.status === 'IDLE'
      ? ''
      : state.status === 'VALIDATING'
      ? t('status.validating')
      : state.status === 'PASSED'
      ? t('status.passed')
      : state.status === 'FAILED'
      ? t('status.failed')
      : t('status.error');

  return (
    <Dialog open={open} onClose={isRunning ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <VerifiedUserIcon fontSize="small" color="primary" />
          {t('title')}
        </Box>
      </DialogTitle>

      <DialogContent>
        <Typography variant="caption" color="text.secondary">
          {t('description')}
        </Typography>

        {state.status !== 'IDLE' && (
          <Box display="flex" alignItems="center" gap={1} mt={1.5} mb={0.5}>
            {statusIcon}
            <Typography
              variant="body2"
              color={
                state.status === 'PASSED'
                  ? 'success.main'
                  : state.status === 'FAILED' || state.status === 'ERROR'
                  ? 'error.main'
                  : 'text.secondary'
              }
              fontWeight={600}>
              {statusLabel}
            </Typography>
          </Box>
        )}

        <div className={classes.logPanel} ref={logPanelRef}>
          {state.logs.length === 0 ? (
            <p className={classes.logLine} style={{ color: '#808080' }}>
              {t('logPanel.placeholder')}
            </p>
          ) : (
            state.logs.map((line, idx) => {
              const colorKey = getLogColor(line);
              return (
                <p
                  key={idx}
                  className={cx(
                    classes.logLine,
                    colorKey ? (classes[colorKey as keyof typeof classes] as string) : undefined
                  )}>
                  {line}
                </p>
              );
            })
          )}
        </div>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={isRunning}>
          {t('buttons.close')}
        </Button>
        {!isRunning && (
          <Button variant="outlined" onClick={runValidation} startIcon={<VerifiedUserIcon />}>
            {t('buttons.revalidate')}
          </Button>
        )}
        {isRunning && (
          <Button variant="outlined" disabled startIcon={<CircularProgress size={14} />}>
            {t('buttons.validating')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};
