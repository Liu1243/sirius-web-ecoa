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
import WarningIcon from '@mui/icons-material/Warning';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  LinearProgress,
  TextField,
  Typography,
} from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface BackflowFile {
  relativePath: string;
  isNew: boolean;
  isConflict?: boolean;
  conflictReason?: string;
}

interface ComponentGroup {
  name: string;
  fileCount: number;
  files: BackflowFile[];
}

interface ExcludedFile {
  relativePath: string;
  exclusionReason: string;
}

interface CodeBackflowDialogProps {
  open: boolean;
  taskId: string;
  onClose: () => void;
  onApplied: () => void;
}

type DialogStep = 'select' | 'scan' | 'review' | 'apply' | 'result';

export const CodeBackflowDialog = ({ open, taskId, onClose, onApplied }: CodeBackflowDialogProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa.backflowDialog' });

  const [step, setStep] = useState<DialogStep>('select');
  const [components, setComponents] = useState<ComponentGroup[]>([]);
  const [selectedComponents, setSelectedComponents] = useState<string[]>([]);
  const [returnableFiles, setReturnableFiles] = useState<BackflowFile[]>([]);
  const [excludedFiles, setExcludedFiles] = useState<ExcludedFile[]>([]);
  const [hasConflicts, setHasConflicts] = useState(false);
  const [conflictFiles, setConflictFiles] = useState<BackflowFile[]>([]);
  const [tag, setTag] = useState('');
  const [commitMessage, setCommitMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [applyResult, setApplyResult] = useState<{
    success: boolean;
    appliedFiles: string[];
    skippedFiles: string[];
    conflictFiles: string[];
    sourceRevision: string;
    errorMessage: string | null;
  } | null>(null);

  const handleScan = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/backflow/scan/${taskId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ returnableComponents: selectedComponents }),
      });
      if (!resp.ok) throw new Error('Scan failed');
      const data = await resp.json();

      // Handle new API response with components
      if (data.components) {
        setComponents(data.components);
        // Auto-select all components by default
        setSelectedComponents(data.components.map((c: ComponentGroup) => c.name));
        // Collect all files from selected components
        const allFiles = data.components.flatMap((c: ComponentGroup) => c.files);
        setReturnableFiles(allFiles);
      } else {
        // Fallback for old API format
        setReturnableFiles(data.returnableFiles || []);
      }
      setExcludedFiles(data.excludedFiles || []);
      setStep('review');
    } catch {
      setReturnableFiles([]);
      setExcludedFiles([]);
    } finally {
      setLoading(false);
    }
  };

  const handleInitialScan = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/backflow/scan/${taskId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      if (!resp.ok) throw new Error('Scan failed');
      const data = await resp.json();

      if (data.components) {
        setComponents(data.components);
        // Auto-select all components by default
        setSelectedComponents(data.components.map((c: ComponentGroup) => c.name));
        const allFiles = data.components.flatMap((c: ComponentGroup) => c.files);
        setReturnableFiles(allFiles);
      } else {
        setReturnableFiles(data.returnableFiles || []);
      }
      setExcludedFiles(data.excludedFiles || []);
      setStep('review');
    } catch {
      // fallback to scan step
    } finally {
      setLoading(false);
    }
  };

  const handleComponentToggle = (componentName: string) => {
    setSelectedComponents((prev) => {
      const newSelection = prev.includes(componentName)
        ? prev.filter((c) => c !== componentName)
        : [...prev, componentName];

      // Update returnable files based on selection
      const selectedFiles = components.filter((c) => newSelection.includes(c.name)).flatMap((c) => c.files);
      setReturnableFiles(selectedFiles);

      return newSelection;
    });
  };

  const handleGeneratePatch = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/backflow/patch/${taskId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ returnableComponents: selectedComponents }),
      });
      if (!resp.ok) throw new Error('Patch generation failed');
      const data = await resp.json();
      setHasConflicts(data.hasConflicts || false);
      setConflictFiles(data.conflictFiles || []);
      // Update returnable files with conflict info
      if (data.returnableFiles) {
        setReturnableFiles(
          data.returnableFiles.map((f: BackflowFile) => ({
            ...f,
            isConflict: f.isConflict || false,
            conflictReason: f.conflictReason,
          }))
        );
      }
      setStep('apply');
    } catch {
      // Stay on review step
    } finally {
      setLoading(false);
    }
  };

  const handleApply = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/backflow/apply/${taskId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mode: 'overwrite',
          returnableComponents: selectedComponents,
          tag: tag.trim() || undefined,
          commitMessage: commitMessage.trim() || undefined,
        }),
      });
      if (!resp.ok) throw new Error('Apply failed');
      const data = await resp.json();
      setApplyResult(data);
      setStep('result');
      if (data.success) {
        onApplied();
      }
    } catch {
      setApplyResult({
        success: false,
        appliedFiles: [],
        skippedFiles: [],
        conflictFiles: [],
        sourceRevision: '',
        errorMessage: 'Network error',
      });
      setStep('result');
    } finally {
      setLoading(false);
    }
  };

  // Auto-trigger initial scan when dialog opens
  useEffect(() => {
    if (open && step === 'select') {
      void handleInitialScan();
    }
  }, [open]);

  const handleClose = () => {
    setStep('select');
    setReturnableFiles([]);
    setExcludedFiles([]);
    setHasConflicts(false);
    setConflictFiles([]);
    setApplyResult(null);
    setComponents([]);
    setSelectedComponents([]);
    setTag('');
    setCommitMessage('');
    onClose();
  };

  const totalSelectedFiles = returnableFiles.length;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth closeAfterTransition>
      <DialogTitle>{t('title')}</DialogTitle>
      <DialogContent>
        {step === 'select' && (
          <Box>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {t('scanDescription')}
            </Typography>
            {components.length > 0 && (
              <FormControl fullWidth sx={{ mb: 2 }}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  {t('selectComponent')}
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {components.map((comp) => (
                    <FormControlLabel
                      key={comp.name}
                      control={
                        <Checkbox
                          checked={selectedComponents.includes(comp.name)}
                          onChange={() => handleComponentToggle(comp.name)}
                        />
                      }
                      label={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body2">{comp.name}</Typography>
                          <Chip size="small" label={`${comp.fileCount} files`} color="primary" variant="outlined" />
                        </Box>
                      }
                    />
                  ))}
                </Box>
                <Box sx={{ mt: 2, display: 'flex', gap: 1 }}>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => setSelectedComponents(components.map((c) => c.name))}>
                    {t('selectAll')}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => {
                      setSelectedComponents([]);
                      setReturnableFiles([]);
                    }}>
                    {t('deselectAll')}
                  </Button>
                </Box>
              </FormControl>
            )}
            <Typography variant="caption" color="text.secondary">
              Task ID: {taskId}
            </Typography>
          </Box>
        )}

        {step === 'scan' && (
          <Box>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {t('scanDescription')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Task ID: {taskId}
            </Typography>
          </Box>
        )}

        {step === 'review' && (
          <Box>
            {/* Selected components summary */}
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              {t('selectedComponents')}: {selectedComponents.length} / {components.length}
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 2 }}>
              {selectedComponents.map((compName) => (
                <Chip
                  key={compName}
                  size="small"
                  label={compName}
                  color="primary"
                  onDelete={() => handleComponentToggle(compName)}
                />
              ))}
            </Box>

            <Divider sx={{ my: 1.5 }} />

            {/* Returnable files */}
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              {t('returnableFiles')} ({totalSelectedFiles})
            </Typography>
            {totalSelectedFiles === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t('noReturnableFiles')}
              </Typography>
            ) : (
              <Box sx={{ maxHeight: 200, overflow: 'auto', mb: 2 }}>
                {components
                  .filter((c) => selectedComponents.includes(c.name))
                  .map((comp) => (
                    <Box key={comp.name} sx={{ mb: 1 }}>
                      <Typography variant="caption" fontWeight={600} color="primary">
                        {comp.name}
                      </Typography>
                      {comp.files.map((f) => (
                        <Box key={f.relativePath} sx={{ display: 'flex', alignItems: 'center', py: 0.25, pl: 2 }}>
                          <Typography variant="caption" sx={{ flex: 1 }}>
                            {f.relativePath.replace(`${comp.name}/`, '')}
                          </Typography>
                          {f.isNew && <Chip size="small" label={t('newFile')} color="info" variant="outlined" />}
                        </Box>
                      ))}
                    </Box>
                  ))}
              </Box>
            )}

            <Divider sx={{ my: 1.5 }} />

            {/* Excluded files */}
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              {t('excludedFiles')} ({excludedFiles.length})
            </Typography>
            {excludedFiles.length > 0 && (
              <Box sx={{ maxHeight: 150, overflow: 'auto' }}>
                {excludedFiles.slice(0, 10).map((f) => (
                  <Typography key={f.relativePath} variant="caption" display="block" color="text.secondary">
                    {f.relativePath} — {f.exclusionReason}
                  </Typography>
                ))}
                {excludedFiles.length > 10 && (
                  <Typography variant="caption" color="text.secondary">
                    ... {excludedFiles.length - 10} more
                  </Typography>
                )}
              </Box>
            )}
          </Box>
        )}

        {step === 'apply' && (
          <Box>
            {hasConflicts && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                <AlertTitle>{t('conflictWarning')}</AlertTitle>
                {conflictFiles.map((f) => (
                  <Typography key={f.relativePath} variant="body2">
                    {f.relativePath}: {f.conflictReason}
                  </Typography>
                ))}
                <Typography variant="body2" sx={{ mt: 1 }}>
                  {t('conflictAdvice')}
                </Typography>
              </Alert>
            )}

            <Typography variant="subtitle2" sx={{ mb: 2 }}>
              {t('backflowOptions')}
            </Typography>

            <TextField
              label={t('tagLabel')}
              placeholder={t('tagPlaceholder')}
              value={tag}
              onChange={(e) => setTag(e.target.value)}
              fullWidth
              size="small"
              sx={{ mb: 2 }}
              helperText={t('tagHelper')}
            />

            <TextField
              label={t('commitLabel')}
              placeholder={t('commitPlaceholder')}
              value={commitMessage}
              onChange={(e) => setCommitMessage(e.target.value)}
              fullWidth
              multiline
              rows={2}
              size="small"
              helperText={t('commitHelper')}
            />

            <Alert severity="info" sx={{ mt: 2 }}>
              {t('testAssumption')}
            </Alert>
          </Box>
        )}

        {step === 'result' && applyResult && (
          <Box>
            {applyResult.success ? (
              <Alert severity="success" icon={<CheckCircleIcon />}>
                <AlertTitle>{t('applySuccess')}</AlertTitle>
                <Typography variant="body2">
                  {t('appliedCount')}: {applyResult.appliedFiles.length}
                </Typography>
                {applyResult.sourceRevision && (
                  <Typography variant="caption" display="block" color="text.secondary">
                    {t('sourceRevision')}: {applyResult.sourceRevision}
                  </Typography>
                )}
              </Alert>
            ) : (
              <Alert severity="error" icon={<ErrorIcon />}>
                <AlertTitle>{t('applyFailed')}</AlertTitle>
                {applyResult.errorMessage && <Typography variant="body2">{applyResult.errorMessage}</Typography>}
                {applyResult.conflictFiles.length > 0 && (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="subtitle2">{t('conflictFiles')}:</Typography>
                    {applyResult.conflictFiles.map((f) => (
                      <Typography key={f} variant="body2">
                        {f}
                      </Typography>
                    ))}
                  </Box>
                )}
              </Alert>
            )}
          </Box>
        )}

        {loading && <LinearProgress sx={{ mt: 2 }} />}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose}>{t('close')}</Button>
        {step === 'select' && (
          <Button variant="contained" onClick={handleScan} disabled={loading || selectedComponents.length === 0}>
            {t('scan')}
          </Button>
        )}
        {step === 'scan' && (
          <Button variant="contained" onClick={handleInitialScan} disabled={loading}>
            {t('scan')}
          </Button>
        )}
        {step === 'review' && (
          <>
            <Button onClick={() => setStep('select')}>{t('back')}</Button>
            <Button
              variant="contained"
              onClick={handleGeneratePatch}
              disabled={loading || returnableFiles.length === 0}>
              {t('generatePatch')}
            </Button>
          </>
        )}
        {step === 'apply' && (
          <>
            <Button onClick={() => setStep('review')}>{t('back')}</Button>
            <Button
              variant="contained"
              color={hasConflicts ? 'warning' : 'primary'}
              onClick={handleApply}
              disabled={loading}
              startIcon={hasConflicts ? <WarningIcon /> : undefined}>
              {hasConflicts ? t('applyWithConflicts') : t('apply')}
            </Button>
          </>
        )}
        {step === 'result' && (
          <Button variant="contained" onClick={handleClose}>
            {t('done')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};
