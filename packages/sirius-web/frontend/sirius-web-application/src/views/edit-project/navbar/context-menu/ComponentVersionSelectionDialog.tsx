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

import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Collapse from '@mui/material/Collapse';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Select from '@mui/material/Select';
import Typography from '@mui/material/Typography';
import { Fragment, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';
import { ComponentCodeHistory, ComponentHistoryEntry, ComponentVersionSelection } from './GenerateEcoaDialog.types';

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------
const useStyles = makeStyles()((theme) => ({
  dialogPaper: {
    minWidth: 600,
    maxWidth: 800,
  },
  componentSection: {
    marginBottom: theme.spacing(2),
  },
  componentHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: theme.spacing(1.5),
    backgroundColor: theme.palette.action.hover,
    borderRadius: theme.shape.borderRadius,
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.action.selected,
    },
  },
  componentHeaderSelected: {
    backgroundColor: theme.palette.success.light,
    '&:hover': {
      backgroundColor: theme.palette.success.main + '30',
    },
  },
  versionList: {
    padding: 0,
    paddingLeft: theme.spacing(2),
  },
  versionItem: {
    borderLeft: `2px solid ${theme.palette.divider}`,
    paddingLeft: theme.spacing(2),
    marginLeft: theme.spacing(1),
  },
  selectedVersion: {
    borderLeftColor: theme.palette.success.main,
    backgroundColor: theme.palette.success.light + '15',
  },
  versionMeta: {
    display: 'flex',
    gap: theme.spacing(1),
    alignItems: 'center',
    marginTop: theme.spacing(0.5),
  },
  summaryBox: {
    padding: theme.spacing(2),
    backgroundColor: theme.palette.info.light + '20',
    borderRadius: theme.shape.borderRadius,
    marginBottom: theme.spacing(2),
  },
}));

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------
interface ComponentVersionSelectionDialogProps {
  open: boolean;
  loading: boolean;
  componentHistory: ComponentCodeHistory | null;
  initialSelections: ComponentVersionSelection[];
  onConfirm: (selections: ComponentVersionSelection[]) => void;
  onCancel: () => void;
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------
export const ComponentVersionSelectionDialog = ({
  open,
  loading,
  componentHistory,
  initialSelections,
  onConfirm,
  onCancel,
}: ComponentVersionSelectionDialogProps) => {
  const { classes, cx } = useStyles();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });

  const [selections, setSelections] = useState<Record<string, ComponentVersionSelection>>({});
  const [expandedComponents, setExpandedComponents] = useState<Record<string, boolean>>({});

  // Initialize selections from props
  useEffect(() => {
    if (initialSelections.length > 0) {
      const initial: Record<string, ComponentVersionSelection> = {};
      initialSelections.forEach((sel) => {
        initial[sel.componentId] = sel;
      });
      setSelections(initial);
    }
  }, [initialSelections]);

  // Expand all components by default when history is loaded
  useEffect(() => {
    if (componentHistory?.components) {
      const expanded: Record<string, boolean> = {};
      componentHistory.components.forEach((comp) => {
        expanded[comp.componentId] = true;
      });
      setExpandedComponents(expanded);
    }
  }, [componentHistory]);

  const handleToggleComponent = (componentId: string) => {
    setExpandedComponents((prev) => ({
      ...prev,
      [componentId]: !prev[componentId],
    }));
  };

  const handleSelectVersion = (componentId: string, componentName: string, versionId: string, versionName: string) => {
    setSelections((prev) => ({
      ...prev,
      [componentId]: { componentId, componentName, versionId, versionName },
    }));
  };

  const handleClearSelection = (componentId: string) => {
    setSelections((prev) => {
      const next = { ...prev };
      delete next[componentId];
      return next;
    });
  };

  const handleConfirm = () => {
    onConfirm(Object.values(selections));
  };

  const selectedCount = Object.keys(selections).length;
  const totalComponents = componentHistory?.components?.length ?? 0;
  const allSelected = totalComponents > 0 && selectedCount === totalComponents;

  // Loading state
  if (loading) {
    return (
      <Dialog open={open} classes={{ paper: classes.dialogPaper }}>
        <DialogTitle>{t('componentSelection.title')}</DialogTitle>
        <DialogContent>
          <Box display="flex" justifyContent="center" alignItems="center" p={4} gap={2}>
            <CircularProgress />
            <Typography>{t('componentSelection.loading')}</Typography>
          </Box>
        </DialogContent>
      </Dialog>
    );
  }

  // No components state
  if (!componentHistory?.components || componentHistory.components.length === 0) {
    return (
      <Dialog open={open} classes={{ paper: classes.dialogPaper }}>
        <DialogTitle>{t('componentSelection.title')}</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mt: 1 }}>
            {t('componentSelection.noComponents')}
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={onCancel}>{t('dialog.buttons.close')}</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} classes={{ paper: classes.dialogPaper }} maxWidth="md" fullWidth onClose={onCancel}>
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <HourglassTopIcon color="warning" />
          {t('componentSelection.title')}
        </Box>
      </DialogTitle>

      <DialogContent>
        {/* Summary */}
        <Paper className={classes.summaryBox} elevation={0}>
          <Typography variant="body2" gutterBottom>
            {t('componentSelection.summary', { selected: selectedCount, total: totalComponents })}
          </Typography>
          {!allSelected && (
            <Alert severity="info" sx={{ mt: 1 }}>
              {t('componentSelection.pleaseSelectAll')}
            </Alert>
          )}
        </Paper>

        {/* Component List */}
        {componentHistory.components.map((component: ComponentHistoryEntry) => {
          const isExpanded = expandedComponents[component.componentId] ?? true;
          const selectedVersion = selections[component.componentId];
          const hasVersions = component.versions && component.versions.length > 0;

          return (
            <Box key={component.componentId} className={classes.componentSection}>
              <Box
                className={cx(classes.componentHeader, selectedVersion && classes.componentHeaderSelected)}
                onClick={() => handleToggleComponent(component.componentId)}>
                <Box display="flex" alignItems="center" gap={1}>
                  {selectedVersion ? (
                    <CheckCircleIcon color="success" fontSize="small" />
                  ) : (
                    <ErrorIcon color="disabled" fontSize="small" />
                  )}
                  <Typography variant="subtitle2">{component.componentName}</Typography>
                  <Chip
                    size="small"
                    label={selectedVersion ? selectedVersion.versionName : t('componentSelection.notSelected')}
                    color={selectedVersion ? 'success' : 'default'}
                    variant={selectedVersion ? 'filled' : 'outlined'}
                  />
                </Box>
                {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
              </Box>

              <Collapse in={isExpanded}>
                <List className={classes.versionList} dense>
                  {!hasVersions ? (
                    <ListItem>
                      <Alert severity="warning" sx={{ width: '100%' }}>
                        {t('componentSelection.noVersions', { component: component.componentName })}
                      </Alert>
                    </ListItem>
                  ) : (
                    component.versions.map((version) => {
                      const isSelected = selectedVersion?.versionId === version.id;

                      return (
                        <ListItem
                          key={version.id}
                          className={cx(classes.versionItem, isSelected && classes.selectedVersion)}
                          disablePadding>
                          <ListItemButton
                            selected={isSelected}
                            onClick={() =>
                              handleSelectVersion(
                                component.componentId,
                                component.componentName,
                                version.id,
                                version.versionName
                              )
                            }>
                            <ListItemText
                              primary={
                                <Box display="flex" alignItems="center" gap={1}>
                                  <Typography variant="body2" fontWeight={isSelected ? 600 : 400}>
                                    {version.versionName}
                                  </Typography>
                                  {isSelected && <CheckCircleIcon color="success" fontSize="small" />}
                                </Box>
                              }
                              secondary={
                                <Box className={classes.versionMeta}>
                                  <Typography variant="caption" color="text.secondary">
                                    {new Date(version.createdAt).toLocaleString('zh-CN')}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {version.author}
                                  </Typography>
                                  {version.commitMessage && (
                                    <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                                      "{version.commitMessage}"
                                    </Typography>
                                  )}
                                </Box>
                              }
                            />
                          </ListItemButton>
                        </ListItem>
                      );
                    })
                  )}
                </List>
              </Collapse>

              <Divider sx={{ mt: 1 }} />
            </Box>
          );
        })}
      </DialogContent>

      <DialogActions>
        <Button onClick={onCancel}>{t('dialog.buttons.cancel')}</Button>
        <Button
          variant="contained"
          color="primary"
          onClick={handleConfirm}
          disabled={!allSelected}
          startIcon={<CheckCircleIcon />}>
          {t('componentSelection.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
