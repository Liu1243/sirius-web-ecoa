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
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import SkipNextIcon from '@mui/icons-material/SkipNext';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import LinearProgress from '@mui/material/LinearProgress';
import TextField from '@mui/material/TextField';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Select, { SelectChangeEvent } from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import OutlinedInput from '@mui/material/OutlinedInput';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Fragment, useContext, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';
import { getCodeServerUrl } from '../../../../core/codeServer';
import {
  GenerateEcoaDialogProps,
  GenerateEcoaDialogState,
  PhaseId,
  PhaseParams,
  PhaseSelection,
  PhaseState,
  PhaseStatus,
  TaskStatusResponse,
  TaskSubStatus,
  WorkflowMode,
} from './GenerateEcoaDialog.types';
import { defaultPhaseSelection, supportsAwaitingCode, visiblePhases } from './GenerateEcoaDialog.workflow';
import { ComponentVersionSelector } from './ComponentVersionSelector';
import {
  ComponentCodeVersion,
  ComponentCodeTag,
} from '../../workbench-views/component-history/ComponentHistoryView.types';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------
const useStyles = makeStyles()((theme) => ({
  dialogPaper: {
    minWidth: 640,
    maxWidth: 760,
  },
  sectionTitle: {
    fontWeight: 600,
    marginBottom: theme.spacing(1),
  },
  phaseCard: {
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    padding: theme.spacing(1.5),
    marginBottom: theme.spacing(1),
    transition: 'border-color 0.2s ease, background-color 0.2s ease',
  },
  phaseCardRunning: {
    borderColor: theme.palette.primary.main,
    backgroundColor: theme.palette.action.hover,
  },
  phaseCardDone: {
    borderColor: theme.palette.success.main,
  },
  phaseCardFailed: {
    borderColor: theme.palette.error.main,
  },
  phaseCardSkipped: {
    borderColor: theme.palette.divider,
    opacity: 0.5,
  },
  phaseHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  phaseLabel: {
    flex: 1,
  },
  toolChips: {
    display: 'flex',
    gap: theme.spacing(0.5),
    marginTop: theme.spacing(0.5),
    flexWrap: 'wrap',
  },
  toolChip: {
    padding: theme.spacing(0.2, 0.8),
    borderRadius: 4,
    fontSize: '0.7rem',
    fontFamily: 'monospace',
    backgroundColor: theme.palette.action.selected,
  },
  toolChipActive: {
    backgroundColor: theme.palette.primary.main,
    color: theme.palette.primary.contrastText,
  },
  toolChipDone: {
    backgroundColor: theme.palette.success.main,
    color: theme.palette.success.contrastText,
  },
  toolChipFailed: {
    backgroundColor: theme.palette.error.main,
    color: theme.palette.error.contrastText,
  },
  configRow: {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: theme.spacing(1),
    marginBottom: theme.spacing(1.5),
  },
  logPanel: {
    backgroundColor: '#1e1e1e',
    color: '#d4d4d4',
    fontFamily: '"Cascadia Code", "Fira Code", "Consolas", monospace',
    fontSize: '0.78rem',
    padding: theme.spacing(1.5),
    borderRadius: theme.shape.borderRadius,
    height: 180,
    overflowY: 'auto',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-all',
    lineHeight: 1.6,
  },
  logLine: {
    margin: 0,
    '&:not(:last-child)': {
      marginBottom: 2,
    },
  },
  logError: { color: '#f48771' },
  logSuccess: { color: '#4ec9b0' },
  logInfo: { color: '#9cdcfe' },
  logWarning: { color: '#dcdcaa' },
}));

// ---------------------------------------------------------------------------
// Phase definitions
// ---------------------------------------------------------------------------
interface PhaseDef {
  id: PhaseId;
  titleKey: string;
  descKey: string;
  tools: TaskSubStatus[];
  mandatory?: boolean;
  params?: { key: string; labelKey: string; placeholderKey: string; type?: 'text' | 'components' }[];
}

const defaultParams = [
  {
    key: 'additionalArgs',
    labelKey: 'dialog.params.additionalArgs',
    placeholderKey: 'dialog.params.additionalArgsPlaceholder',
  },
];

const PHASE_DEFS: PhaseDef[] = [
  {
    id: 'EXVT',
    titleKey: 'dialog.phases.EXVT.title',
    descKey: 'dialog.phases.EXVT.desc',
    tools: ['RUNNING_EXVT'],
    mandatory: true,
    params: defaultParams,
  },
  // ASCTG before MSCIGT per ECOA AS6 spec: test harness first, then skeleton generation
  {
    id: 'ASCTG',
    titleKey: 'dialog.phases.ASCTG.title',
    descKey: 'dialog.phases.ASCTG.desc',
    tools: ['RUNNING_ASCTG'],
    params: [
      {
        key: 'selected_components',
        labelKey: 'dialog.params.selectedComponents',
        placeholderKey: 'dialog.params.selectedComponentsPlaceholder',
        type: 'components',
      },
      ...defaultParams,
    ],
  },
  {
    id: 'MSCIGT',
    titleKey: 'dialog.phases.MSCIGT.title',
    descKey: 'dialog.phases.MSCIGT.desc',
    tools: ['RUNNING_MSCIGT'],
    params: defaultParams,
  },
  // Execution branches — user selects one (or both) depending on test needs
  {
    id: 'CSMGVT',
    titleKey: 'dialog.phases.CSMGVT.title',
    descKey: 'dialog.phases.CSMGVT.desc',
    tools: ['RUNNING_CSMGVT'],
    params: defaultParams,
  },
  {
    id: 'LDP',
    titleKey: 'dialog.phases.LDP.title',
    descKey: 'dialog.phases.LDP.desc',
    tools: ['RUNNING_LDP'],
    params: defaultParams,
  },
];

const TOOL_LABELS: Record<TaskSubStatus, string> = {
  NONE: '',
  RUNNING_EXVT: 'EXVT',
  RUNNING_MSCIGT: 'MSCIGT',
  RUNNING_ASCTG: 'ASCTG',
  RUNNING_CSMGVT: 'CSMGVT',
  RUNNING_LDP: 'LDP',
  SWITCHING_ACTIVE_PROJECT: 'SWITCH',
  CODE_BACKFLOW_APPLIED: 'BACKFLOW',
  CONFLICT: 'CONFLICT',
};

const POLL_INTERVAL_MS = 1500;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function resolvePhaseStatus(
  phase: PhaseDef,
  subStatus: TaskSubStatus,
  taskStatus: string,
  selection: PhaseSelection
): PhaseStatus {
  if (!selection[phase.id as keyof PhaseSelection]) return 'SKIPPED';

  const isRunningThisPhase = phase.tools.includes(subStatus);
  const allTools = PHASE_DEFS.map((p) => p.tools).flat();
  const currentToolIdx = allTools.indexOf(subStatus);
  const phaseFirstToolIdx = allTools.indexOf(phase.tools[0]!);

  if (taskStatus === 'COMPLETED') return 'COMPLETED';

  if (isRunningThisPhase) return 'RUNNING';
  if (currentToolIdx > phaseFirstToolIdx) {
    if (taskStatus === 'FAILED' && currentToolIdx === phaseFirstToolIdx + phase.tools.length) {
      return 'FAILED';
    }
    return 'COMPLETED';
  }
  return 'PENDING';
}

function getLogColor(line: string): string {
  if (line.includes('[ERROR]') || line.includes('FAILED') || line.includes('Error')) return 'logError';
  if (line.includes('[SUCCESS]') || line.includes('COMPLETED') || line.includes('✓')) return 'logSuccess';
  if (line.includes('[INFO]') || line.includes('INFO') || line.includes('[ECOA-WEB]') || line.includes('[INIT]'))
    return 'logInfo';
  if (line.includes('[WARNING]') || line.includes('WARNING') || line.includes('[WARN]') || line.includes('[SKIP]'))
    return 'logWarning';
  return '';
}

// ---------------------------------------------------------------------------
// Phase Card
// ---------------------------------------------------------------------------
interface PhaseCardProps {
  def: PhaseDef;
  phaseStatus: PhaseStatus;
  activeSubStatus: TaskSubStatus;
  enabled: boolean;
  canConfigure: boolean;
  onToggle: () => void;
  onContinue: () => void;
  disabled: boolean;
  phaseParams: Record<string, string | string[]>;
  onParamChange: (key: string, value: string | string[]) => void;
  availableComponents: string[];
}

function PhaseStatusIcon({ status }: { status: PhaseStatus }) {
  switch (status) {
    case 'RUNNING':
      return <CircularProgress size={18} thickness={5} />;
    case 'COMPLETED':
      return <CheckCircleIcon color="success" fontSize="small" />;
    case 'FAILED':
      return <ErrorIcon color="error" fontSize="small" />;
    case 'SKIPPED':
      return <SkipNextIcon color="disabled" fontSize="small" />;
    default:
      return <HourglassEmptyIcon color="disabled" fontSize="small" />;
  }
}

function PhaseCard({
  def,
  phaseStatus,
  activeSubStatus,
  enabled,
  canConfigure,
  onToggle,
  onContinue,
  disabled,
  phaseParams,
  onParamChange,
  availableComponents,
}: PhaseCardProps) {
  const { classes, cx } = useStyles();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });

  const cardClass = cx(
    classes.phaseCard,
    phaseStatus === 'RUNNING' && classes.phaseCardRunning,
    phaseStatus === 'COMPLETED' && classes.phaseCardDone,
    phaseStatus === 'FAILED' && classes.phaseCardFailed,
    phaseStatus === 'SKIPPED' && classes.phaseCardSkipped
  );

  return (
    <div className={cardClass}>
      <div className={classes.phaseHeader}>
        <PhaseStatusIcon status={phaseStatus} />

        {canConfigure ? (
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={enabled}
                onChange={onToggle}
                disabled={disabled || def.mandatory}
                sx={{ py: 0 }}
              />
            }
            label={
              <Typography
                variant="body2"
                className={classes.phaseLabel}
                color={disabled ? 'text.secondary' : 'text.primary'}>
                {t(def.titleKey)}
                {def.mandatory && (
                  <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                    ({t('dialog.phases.mandatory')})
                  </Typography>
                )}
              </Typography>
            }
            sx={{ margin: 0, flex: 1 }}
          />
        ) : (
          <Typography variant="body2" className={classes.phaseLabel}>
            {t(def.titleKey)}
            {def.mandatory && (
              <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                ({t('dialog.phases.mandatory')})
              </Typography>
            )}
          </Typography>
        )}

        {phaseStatus === 'FAILED' && (
          <Tooltip title={t('dialog.buttons.continueFromHere')}>
            <Button size="small" color="warning" variant="outlined" onClick={onContinue} startIcon={<SkipNextIcon />}>
              {t('dialog.buttons.skip')}
            </Button>
          </Tooltip>
        )}
      </div>

      <Typography variant="caption" color="text.secondary" sx={{ pl: 3.5 }}>
        {t(def.descKey)}
      </Typography>

      {canConfigure && def.params && def.params.length > 0 && (
        <Box sx={{ mt: 1, pl: 3.5, pr: 1 }}>
          {def.params.map((p) => {
            if (p.type === 'components') {
              const selectedVals = Array.isArray(phaseParams[p.key])
                ? (phaseParams[p.key] as string[])
                : (phaseParams[p.key] as string)?.split(',').filter(Boolean) || [];

              const handleChange = (event: SelectChangeEvent<typeof selectedVals>) => {
                const {
                  target: { value },
                } = event;
                onParamChange(p.key, typeof value === 'string' ? value.split(',') : value);
              };

              return (
                <FormControlLabel
                  key={p.key}
                  control={
                    <Select
                      multiple
                      displayEmpty
                      size="small"
                      value={selectedVals}
                      onChange={handleChange}
                      input={<OutlinedInput />}
                      renderValue={(selected) => {
                        if (selected.length === 0) {
                          return (
                            <Typography variant="caption" color="text.secondary">
                              {t(p.placeholderKey)}
                            </Typography>
                          );
                        }
                        return (
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                            {selected.map((value) => (
                              <Chip key={value} label={value} size="small" />
                            ))}
                          </Box>
                        );
                      }}
                      disabled={!enabled || availableComponents.length === 0}
                      sx={{ width: 300, ml: 2, '& .MuiSelect-select': { py: 0.5 } }}>
                      {availableComponents.map((name) => (
                        <MenuItem key={name} value={name}>
                          <Checkbox checked={selectedVals.indexOf(name) > -1} size="small" />
                          <Typography variant="body2">{name}</Typography>
                        </MenuItem>
                      ))}
                    </Select>
                  }
                  label={<Typography variant="body2">{t(p.labelKey)}</Typography>}
                  labelPlacement="start"
                  sx={{ ml: 0, mt: 1, display: 'flex', alignItems: 'flex-start' }}
                />
              );
            }

            return (
              <TextField
                key={p.key}
                label={t(p.labelKey)}
                placeholder={t(p.placeholderKey)}
                value={phaseParams[p.key] || ''}
                onChange={(e) => onParamChange(p.key, e.target.value)}
                size="small"
                fullWidth
                variant="outlined"
                margin="dense"
                disabled={!enabled}
                InputLabelProps={{ shrink: true }}
              />
            );
          })}
        </Box>
      )}

      <div className={classes.toolChips} style={{ paddingLeft: '28px' }}>
        {def.tools.map((tool) => {
          const isActive = activeSubStatus === tool && phaseStatus === 'RUNNING';
          const isDone = phaseStatus === 'COMPLETED';
          const isTFailed = phaseStatus === 'FAILED' && activeSubStatus === tool;
          const chipClass = cx(
            classes.toolChip,
            isActive && classes.toolChipActive,
            isDone && classes.toolChipDone,
            isTFailed && classes.toolChipFailed
          );
          return (
            <span key={tool} className={chipClass}>
              {TOOL_LABELS[tool]}
            </span>
          );
        })}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Initial state
// ---------------------------------------------------------------------------
const EMPTY_PHASE_SELECTION: PhaseSelection = {
  EXVT: false,
  ASCTG: false,
  MSCIGT: false,
  CSMGVT: false,
  LDP: false,
  VERSION_SELECT: false,
};

const DEFAULT_PHASE_PARAMS: PhaseParams = {
  EXVT: {},
  MSCIGT: {},
  ASCTG: {},
  CSMGVT: {},
  LDP: {},
  VERSION_SELECT: {},
};

const initialPhases: PhaseState[] = PHASE_DEFS.map((def) => ({
  id: def.id,
  status: 'PENDING',
  tools: def.tools,
}));

const initialState: GenerateEcoaDialogState = {
  taskId: null,
  workflowMode: 'DIRECT_DEV',
  pendingRerunFromTaskId: null,
  retryFromTaskId: null,
  status: 'INIT',
  subStatus: 'NONE',
  progress: 0,
  outputPath: null,
  logs: [],
  errorMessage: null,
  phases: initialPhases,
  phaseSelection: defaultPhaseSelection('DIRECT_DEV', 'initial'),
  phaseParams: DEFAULT_PHASE_PARAMS,
  csmgvtResult: null,
  csmgvtProductCheck: null,
  csmgvtCompileErrors: [],
  csmgvtCsmResult: null,
  testWorkspacePath: null,
  patchArtifactPath: null,
  sourceVersionId: null,
  sourceRevision: null,
  selectedComponentVersions: [],
  availableComponentVersions: null,
  availableTags: null,
};

const phaseSelectionFromIds = (phaseIds: string[] | undefined, fallback: PhaseSelection): PhaseSelection => {
  if (!phaseIds) {
    return fallback;
  }

  return {
    ...EMPTY_PHASE_SELECTION,
    EXVT: phaseIds.includes('EXVT'),
    ASCTG: phaseIds.includes('ASCTG'),
    MSCIGT: phaseIds.includes('MSCIGT'),
    CSMGVT: phaseIds.includes('CSMGVT'),
    LDP: phaseIds.includes('LDP'),
    VERSION_SELECT: phaseIds.includes('VERSION_SELECT'),
  };
};

// ---------------------------------------------------------------------------
// Main Dialog
// ---------------------------------------------------------------------------
export const GenerateEcoaDialog = ({
  open,
  project,
  initialPhasesToRun,
  initialWorkflowMode,
  rerunSourceTaskId,
  continueFromActiveTask,
  onClose,
}: GenerateEcoaDialogProps) => {
  const { classes, cx } = useStyles();
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });

  const [state, setState] = useState<GenerateEcoaDialogState>(initialState);
  const [availableComponents, setAvailableComponents] = useState<string[]>([]);
  const logPanelRef = useRef<HTMLDivElement>(null);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Fetch ASCTG components if ASCTG is enabled and dialog is open
  useEffect(() => {
    if (open && state.phaseSelection.ASCTG && state.status === 'INIT') {
      // Fetching components directly without export first, as export happens on generate.
      const fetchDirectly = async () => {
        try {
          const compResp = await fetch(`${httpOrigin}/api/edt/ecoa/generate/components/${project.id}`);
          if (compResp.ok) {
            const data = await compResp.json();
            if (Array.isArray(data)) {
              setAvailableComponents(data);
            }
          }
        } catch (e) {
          console.error('Failed to fetch ASCTG components directly:', e);
        }
      };
      fetchDirectly();
    }
  }, [open, state.phaseSelection.ASCTG, state.status, project.id, httpOrigin]);

  // Auto-scroll logs
  useEffect(() => {
    if (logPanelRef.current) {
      logPanelRef.current.scrollTop = logPanelRef.current.scrollHeight;
    }
  }, [state.logs]);

  // Reset on open/close
  useEffect(() => {
    if (open) {
      const nextWorkflowMode: WorkflowMode = initialWorkflowMode ?? 'DIRECT_DEV';
      const nextStage =
        (rerunSourceTaskId || continueFromActiveTask) &&
        (nextWorkflowMode === 'HARNESS_DEV' || nextWorkflowMode === 'DIRECT_DEV') &&
        Boolean(initialPhasesToRun?.some((phaseId) => phaseId === 'CSMGVT' || phaseId === 'LDP'))
          ? 'execution'
          : 'initial';
      const initSelection = phaseSelectionFromIds(
        initialPhasesToRun,
        defaultPhaseSelection(nextWorkflowMode, nextStage)
      );

      if (continueFromActiveTask && rerunSourceTaskId) {
        // Continue-from-history mode: the source task is an AWAITING_CODE task from history.
        // Store its taskId in pendingRerunFromTaskId so the next call goes to /continue/{taskId}.
        setState(() => ({
          ...initialState,
          workflowMode: nextWorkflowMode,
          phaseSelection: initSelection,
          // Pre-set pendingRerunFromTaskId so handleStartGeneration uses /continue/{taskId}
          pendingRerunFromTaskId: rerunSourceTaskId,
          logs: [t('dialog.logPanel.continueFromHistoryReady', { id: rerunSourceTaskId })],
        }));
      } else if (rerunSourceTaskId) {
        // Rerun mode: show configuration UI so the user can choose phases before triggering.
        // The actual API call happens inside handleStartGeneration when the user clicks Start.
        setState(() => ({
          ...initialState,
          workflowMode: nextWorkflowMode,
          phaseSelection: initSelection,
          logs: [t('dialog.logPanel.rerunReady', { id: rerunSourceTaskId })],
        }));
      } else {
        setState(() => ({
          ...initialState,
          workflowMode: nextWorkflowMode,
          phaseSelection: initSelection,
        }));
      }
    } else {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    }
  }, [open, initialPhasesToRun, initialWorkflowMode, rerunSourceTaskId, continueFromActiveTask]);

  // Fetch component versions for INTEGRATION mode
  useEffect(() => {
    if (open && state.workflowMode === 'INTEGRATION' && state.status === 'INIT') {
      const fetchComponentVersions = async () => {
        try {
          // Fetch component versions from the component code history API
          const resp = await fetch(`${httpOrigin}/api/graphql`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              query: `
                query GetComponentCodeHistory($projectId: ID!) {
                  componentCodeHistory(input: { projectId: $projectId }) {
                    history {
                      components {
                        componentId
                        componentName
                        versions {
                          id
                          versionName
                          createdAt
                          author
                          tags {
                            id
                            name
                            color
                          }
                        }
                      }
                    }
                  }
                }
              `,
              variables: { projectId: project.id },
            }),
          });

          if (resp.ok) {
            const result = await resp.json();
            if (result.data?.componentCodeHistory?.history) {
              setState((prev) => ({
                ...prev,
                availableComponentVersions: result.data.componentCodeHistory.history,
              }));
            }
          }

          // Fetch available tags
          const tagsResp = await fetch(`${httpOrigin}/api/graphql`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              query: `
                query GetComponentCodeTags($projectId: ID!) {
                  componentCodeTags(input: { projectId: $projectId }) {
                    tags {
                      id
                      name
                      color
                    }
                  }
                }
              `,
              variables: { projectId: project.id },
            }),
          });

          if (tagsResp.ok) {
            const tagsResult = await tagsResp.json();
            if (tagsResult.data?.componentCodeTags?.tags) {
              setState((prev) => ({
                ...prev,
                availableTags: { tags: tagsResult.data.componentCodeTags.tags },
              }));
            }
          }
        } catch (e) {
          console.error('Failed to fetch component versions for INTEGRATION mode:', e);
        }
      };

      fetchComponentVersions();
    }
  }, [open, state.workflowMode, state.status, project.id, httpOrigin]);

  // Polling
  useEffect(() => {
    const isTerminal =
      !state.taskId || state.status === 'COMPLETED' || state.status === 'FAILED' || state.status === 'CANCELLED';

    if (isTerminal) {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      return () => {};
    }

    pollTimerRef.current = setInterval(async () => {
      try {
        const resp = await fetch(`${httpOrigin}/api/edt/ecoa/generate/status/${state.taskId}`);
        if (!resp.ok) return;
        const data: TaskStatusResponse = await resp.json();
        setState((prev) => ({
          ...prev,
          workflowMode: data.workflowMode ?? prev.workflowMode,
          status: data.status,
          subStatus: data.subStatus,
          progress: data.progress,
          outputPath: data.outputPath ?? prev.outputPath,
          logs: data.logs,
          csmgvtResult: data.csmgvtResult ?? prev.csmgvtResult,
          csmgvtProductCheck: data.csmgvtProductCheck ?? prev.csmgvtProductCheck,
          csmgvtCompileErrors: data.csmgvtCompileErrors ?? prev.csmgvtCompileErrors,
          csmgvtCsmResult: data.csmgvtCsmResult ?? prev.csmgvtCsmResult,
          testWorkspacePath: data.testWorkspacePath ?? prev.testWorkspacePath,
          patchArtifactPath: data.patchArtifactPath ?? prev.patchArtifactPath,
          sourceVersionId: data.sourceVersionId ?? prev.sourceVersionId,
          sourceRevision: data.sourceRevision ?? prev.sourceRevision,
          phases: PHASE_DEFS.map((def) => ({
            id: def.id,
            status: resolvePhaseStatus(def, data.subStatus, data.status, prev.phaseSelection),
            tools: def.tools,
          })),
        }));
      } catch {
        // Ignore transient network errors
      }
    }, POLL_INTERVAL_MS);

    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [state.taskId, state.status, httpOrigin]);

  // ---------------------------------------------------------------------------
  // Phase mutual exclusion logic
  // Stages 1-3 (EXVT, ASCTG, MSCIGT) and Stages 4-5 (CSMGVT, LDP) are mutually exclusive
  // ---------------------------------------------------------------------------
  const INITIAL_PHASES: PhaseId[] = ['EXVT', 'ASCTG', 'MSCIGT'];
  const EXECUTION_PHASES: PhaseId[] = ['CSMGVT', 'LDP'];

  const isInitialPhaseSelected = (selection: PhaseSelection): boolean =>
    INITIAL_PHASES.some((id) => selection[id as keyof PhaseSelection]);

  const isExecutionPhaseSelected = (selection: PhaseSelection): boolean =>
    EXECUTION_PHASES.some((id) => selection[id as keyof PhaseSelection]);

  const isPhaseDisabled = (phaseId: PhaseId, selection: PhaseSelection): boolean => {
    // EXECUTION_READY: initial phases are locked (already ran), execution phases are configurable
    if (isExecutionReady) {
      return INITIAL_PHASES.includes(phaseId);
    }

    // INTEGRATION mode: MSCIGT is always forced (cannot be deselected).
    // It must re-run to regenerate inc-gen/ and src-gen/ infrastructure files,
    // which are NOT returned by code-backflow (only src/*.c and *_user_context.h are).
    if (state.workflowMode === 'INTEGRATION' && phaseId === 'MSCIGT') {
      return true;
    }
    // Other INTEGRATION phases can be selected independently (no mutual exclusion)
    if (state.workflowMode === 'INTEGRATION') {
      return false;
    }

    // Initial phases (1-3) are disabled when any execution phase (4-5) is selected
    if (INITIAL_PHASES.includes(phaseId) && isExecutionPhaseSelected(selection)) {
      return true;
    }
    // Execution phases (4-5) are disabled when any initial phase (1-3) is selected
    if (EXECUTION_PHASES.includes(phaseId) && isInitialPhaseSelected(selection)) {
      return true;
    }
    return false;
  };

  // ---------------------------------------------------------------------------
  // Handlers
  // ---------------------------------------------------------------------------
  const handlePhaseToggle = (phaseId: PhaseId) => {
    setState((prev) => {
      const newSelection = {
        ...prev.phaseSelection,
        [phaseId]: !prev.phaseSelection[phaseId as keyof PhaseSelection],
      };

      // INTEGRATION mode: no mutual exclusion, all phases can be selected together
      if (prev.workflowMode !== 'INTEGRATION') {
        // Other modes: Initial phases (1-3) and execution phases (4-5) are mutually exclusive
        // When selecting an initial phase (1-3), automatically unselect all execution phases (4-5)
        if (INITIAL_PHASES.includes(phaseId) && newSelection[phaseId as keyof PhaseSelection]) {
          EXECUTION_PHASES.forEach((id) => {
            newSelection[id as keyof PhaseSelection] = false;
          });
        }

        // When selecting an execution phase (4-5), automatically unselect all initial phases (1-3)
        if (EXECUTION_PHASES.includes(phaseId) && newSelection[phaseId as keyof PhaseSelection]) {
          INITIAL_PHASES.forEach((id) => {
            newSelection[id as keyof PhaseSelection] = false;
          });
        }
      }

      return {
        ...prev,
        phaseSelection: newSelection,
      };
    });
  };

  const handleWorkflowModeChange = (_event: React.SyntheticEvent, newValue: WorkflowMode) => {
    const nextWorkflowMode = newValue;
    const nextStage =
      (nextWorkflowMode === 'HARNESS_DEV' || nextWorkflowMode === 'DIRECT_DEV') && state.pendingRerunFromTaskId
        ? 'execution'
        : 'initial';

    setState((prev) => ({
      ...prev,
      workflowMode: nextWorkflowMode,
      phaseSelection: defaultPhaseSelection(nextWorkflowMode, nextStage),
    }));
  };

  const handleParamChange = (phaseId: PhaseId, key: string, value: string | string[]) => {
    setState((prev) => ({
      ...prev,
      phaseParams: {
        ...prev.phaseParams,
        [phaseId]: {
          ...prev.phaseParams[phaseId],
          [key]: value,
        },
      },
    }));
  };

  const handleContinueFromFailed = async () => {
    // Re-trigger generation, skipping up to the current failed phase
    // For now: just mark phase as skipped and resume polling
    setState((prev) => ({
      ...prev,
      status: 'GENERATING',
      errorMessage: null,
      phases: prev.phases.map((p) => (p.status === 'FAILED' ? { ...p, status: 'SKIPPED' } : p)),
    }));
  };

  const handleStartGeneration = async () => {
    // INTEGRATION mode: require all components to have a version selected
    if (state.workflowMode === 'INTEGRATION') {
      const totalComponents = state.availableComponentVersions?.components?.length || 0;
      if (state.selectedComponentVersions.length < totalComponents) {
        const missingCount = totalComponents - state.selectedComponentVersions.length;
        setState((prev) => ({
          ...prev,
          errorMessage: t('dialog.validation.allComponentsRequired', { count: missingCount }),
        }));
        return;
      }
    }

    const continueFromTaskId = state.pendingRerunFromTaskId;
    const retryFromTaskId = state.retryFromTaskId;

    // Determine whether this run uses execution phases (CSMGVT / LDP).
    // When retrying an execution-phase failure the backend must receive
    // continuing=true so it validates and runs them correctly.
    const isExecutionPhaseRun =
      state.phaseSelection.CSMGVT || state.phaseSelection.LDP
        ? !state.phaseSelection.EXVT && !state.phaseSelection.ASCTG && !state.phaseSelection.MSCIGT
        : false;

    setState((prev) => ({
      ...prev,
      // When continuing from AWAITING_CODE, skip EXPORTING_XML (XML was already exported in first run)
      status: continueFromTaskId ? 'GENERATING' : 'EXPORTING_XML',
      logs: continueFromTaskId ? prev.logs : [t('dialog.logPanel.requesting')],
      errorMessage: null,
      phases: PHASE_DEFS.map((def) => ({
        id: def.id,
        // In EXECUTION_READY, preserve COMPLETED status for initial phases
        // so the progress bar looks continuous through both workflow stages.
        status:
          prev.status === 'EXECUTION_READY' && prev.phases.find((p) => p.id === def.id)?.status === 'COMPLETED'
            ? ('COMPLETED' as const)
            : prev.phaseSelection[def.id as keyof PhaseSelection]
            ? ('PENDING' as const)
            : ('SKIPPED' as const),
        tools: def.tools,
      })),
    }));

    // Build selected phases list to send to backend
    const selectedPhases = PHASE_DEFS.filter((def) => state.phaseSelection[def.id as keyof PhaseSelection]).map(
      (def) => def.id
    );

    try {
      // Determine which API to call:
      // 1. pendingRerunFromTaskId — user clicked "Continue to Execution" in same session
      //    → /continue/{taskId}: reuses same DB record, history stays as ONE entry
      // 2. retryFromTaskId — user clicked "Retry" after a FAILED task in current session
      //    → /rerun/{taskId}: creates a new linked task reusing the same workspace
      // 3. rerunSourceTaskId prop — user clicks Rerun from history dialog
      //    → /rerun/{taskId}: creates a new linked task (different workspace allowed)
      // 4. otherwise — fresh generation
      const url = continueFromTaskId
        ? `${httpOrigin}/api/edt/ecoa/generate/continue/${continueFromTaskId}`
        : retryFromTaskId
        ? `${httpOrigin}/api/edt/ecoa/generate/rerun/${retryFromTaskId}`
        : rerunSourceTaskId
        ? `${httpOrigin}/api/edt/ecoa/generate/rerun/${rerunSourceTaskId}`
        : `${httpOrigin}/api/edt/ecoa/generate/${project.id}`;

      // Clear pendingRerunFromTaskId and retryFromTaskId now that we've consumed them
      setState((prev) => ({ ...prev, pendingRerunFromTaskId: null, retryFromTaskId: null }));

      // Convert any array parameters to comma-separated strings for the backend API
      const serializedPhaseParams: Record<string, Record<string, string>> = {};
      for (const [pId, paramsObj] of Object.entries(state.phaseParams)) {
        serializedPhaseParams[pId] = {};
        for (const [paramKey, paramVal] of Object.entries(paramsObj)) {
          if (Array.isArray(paramVal)) {
            serializedPhaseParams[pId][paramKey] = paramVal.join(',');
          } else {
            serializedPhaseParams[pId][paramKey] = paramVal;
          }
        }
      }

      // Prepare request body
      const requestBody: any = {
        workflowMode: state.workflowMode,
        selectedPhases,
        phaseParams: serializedPhaseParams,
        // Pass continuing=true when retrying execution-phase failures so the backend
        // validates and runs CSMGVT/LDP correctly (and skips XML re-export).
        ...(retryFromTaskId && isExecutionPhaseRun ? { continuing: true } : {}),
      };

      // INTEGRATION mode: add selected component versions
      if (state.workflowMode === 'INTEGRATION' && state.selectedComponentVersions.length > 0) {
        requestBody.selectedVersions = state.selectedComponentVersions.map((sv) => ({
          componentId: sv.componentId,
          componentName: sv.componentName,
          versionId: sv.versionId,
          versionName: sv.versionName,
        }));
      }

      const resp = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
      });

      // Check response status first before parsing JSON
      if (!resp.ok) {
        // Try to get error message from response body, fallback to status text
        let errorMessage: string;
        try {
          const errorData = await resp.json();
          errorMessage = errorData.message || errorData.error || `HTTP ${resp.status}: ${resp.statusText}`;
        } catch {
          // If JSON parsing fails, try text
          try {
            const text = await resp.text();
            errorMessage = text || `HTTP ${resp.status}: ${resp.statusText}`;
          } catch {
            errorMessage = `HTTP ${resp.status}: ${resp.statusText}`;
          }
        }
        setState((prev) => ({
          ...prev,
          status: 'FAILED',
          errorMessage: errorMessage,
          logs: [...prev.logs, `[ERROR] ${errorMessage}`],
        }));
        return;
      }

      const data = await resp.json();

      if (!data.taskId) {
        setState((prev) => ({
          ...prev,
          status: 'FAILED',
          errorMessage: data.message || t('dialog.logPanel.failedMsg'),
          logs: [...prev.logs, data.message ? `[ERROR] ${data.message}` : t('dialog.logPanel.reqFailed')],
        }));
        return;
      }

      setState((prev) => ({
        ...prev,
        taskId: data.taskId,
        status: 'GENERATING',
        logs: [...prev.logs, t('dialog.logPanel.taskCreated', { id: data.taskId })],
      }));
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setState((prev) => ({
        ...prev,
        status: 'FAILED',
        errorMessage: t('dialog.logPanel.networkFailed', { msg }),
        logs: [...prev.logs, t('dialog.logPanel.networkFailedLog', { msg })],
      }));
    }
  };

  const handleOpenCodeServer = () => {
    const path = state.outputPath ?? `/workspace/${project.id}/${state.taskId ?? 'latest'}/Steps`;
    window.open(getCodeServerUrl(path), '_blank');
  };

  const handleContinueToExecution = () => {
    // User has confirmed business logic is complete.
    // Transition to EXECUTION_READY rather than resetting the dialog:
    // - initial phase cards remain COMPLETED (progress bar stays continuous)
    // - execution phase cards appear as PENDING (user can configure which to run)
    // - "Start Execution" button triggers the continue API with pendingRerunFromTaskId
    setState((prev) => ({
      ...prev,
      pendingRerunFromTaskId: prev.taskId,
      taskId: null, // stop polling; execution will create a new taskId
      status: 'EXECUTION_READY',
      // Initial phases stay COMPLETED; execution phases become PENDING
      phases: PHASE_DEFS.map((def) => ({
        id: def.id,
        status:
          INITIAL_PHASES.includes(def.id) && prev.phaseSelection[def.id as keyof PhaseSelection]
            ? ('COMPLETED' as const)
            : EXECUTION_PHASES.includes(def.id)
            ? ('PENDING' as const)
            : ('SKIPPED' as const),
        tools: def.tools,
      })),
      logs: [...prev.logs, t('awaitingCode.continueLog')],
      phaseSelection: defaultPhaseSelection(prev.workflowMode, 'execution'),
    }));
  };

  // ---------------------------------------------------------------------------
  // Computed
  // ---------------------------------------------------------------------------
  const isRunning = state.status === 'EXPORTING_XML' || state.status === 'GENERATING';
  const isCompleted = state.status === 'COMPLETED';
  const isFailed = state.status === 'FAILED';
  const isAwaitingCode = supportsAwaitingCode(state.workflowMode) && state.status === 'AWAITING_CODE';
  // EXECUTION_READY: skeleton done, user confirmed, now selecting execution branch
  const isExecutionReady = state.status === 'EXECUTION_READY';
  const canStart = state.status === 'INIT';
  // In EXECUTION_READY allow configuring execution phase cards (CSMGVT / LDP)
  const canConfigureExecution = isExecutionReady;
  const displayedPhaseDefs = PHASE_DEFS.filter((def) => visiblePhases(state.workflowMode).includes(def.id));

  const progressLabel =
    state.status === 'INIT'
      ? t('dialog.status.READY')
      : state.status === 'EXPORTING_XML'
      ? t('dialog.status.EXPORTING_XML')
      : state.status === 'GENERATING'
      ? t('dialog.status.GENERATING', { step: state.subStatus.replace('RUNNING_', '') })
      : state.status === 'AWAITING_CODE'
      ? t('dialog.status.AWAITING_CODE')
      : state.status === 'EXECUTION_READY'
      ? t('dialog.status.EXECUTION_READY')
      : state.status === 'COMPLETED'
      ? t('dialog.status.COMPLETED')
      : state.status === 'FAILED'
      ? t('dialog.status.FAILED')
      : state.status;

  return (
    <Dialog
      open={open}
      onClose={isRunning ? undefined : onClose}
      classes={{ paper: classes.dialogPaper }}
      data-testid="generate-ecoa-dialog">
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <HourglassTopIcon fontSize="small" color="primary" />
          {t('dialog.title')}
        </Box>
      </DialogTitle>

      <DialogContent>
        {/* ── Pipeline phases ─────────────────────────────────────────── */}
        <Typography variant="subtitle2" className={classes.sectionTitle}>
          {t('dialog.pipeline')}
        </Typography>

        {canStart && (
          <Box sx={{ mb: 1.5 }}>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
              {t('dialog.workflowMode.label')}
            </Typography>
            <Tabs
              value={state.workflowMode}
              onChange={handleWorkflowModeChange}
              textColor="primary"
              indicatorColor="primary">
              <Tab value="DIRECT_DEV" label={t('dialog.workflowMode.directDev')} />
              <Tab value="HARNESS_DEV" label={t('dialog.workflowMode.harnessDev')} />
              <Tab value="INTEGRATION" label={t('dialog.workflowMode.integration')} />
            </Tabs>
          </Box>
        )}

        {displayedPhaseDefs.map((def) => {
          const phase = state.phases.find((p) => p.id === def.id)!;
          const enabled = state.phaseSelection[def.id as keyof PhaseSelection];

          // Insert the "write business logic" workflow divider between MSCIGT and CSMGVT
          const divider =
            (state.workflowMode === 'HARNESS_DEV' || state.workflowMode === 'DIRECT_DEV') && def.id === 'CSMGVT' ? (
              <Box
                key="workflow-divider"
                sx={(theme) => ({
                  border: `1px dashed ${isAwaitingCode ? theme.palette.warning.main : theme.palette.divider}`,
                  borderRadius: theme.shape.borderRadius,
                  p: 1.5,
                  mb: 1,
                  bgcolor: isAwaitingCode ? theme.palette.warning.light + '18' : theme.palette.action.hover,
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 1.5,
                })}>
                <HourglassTopIcon
                  sx={{ mt: 0.2, flexShrink: 0 }}
                  fontSize="small"
                  color={isAwaitingCode ? 'warning' : 'disabled'}
                />
                <Box flex={1}>
                  <Typography variant="body2" fontWeight={600} gutterBottom>
                    {t('workflowStep.title')}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                    {t('workflowStep.desc')}
                  </Typography>
                  {(isAwaitingCode || !canStart) && (
                    <Button
                      size="small"
                      variant="outlined"
                      color={isAwaitingCode ? 'warning' : 'inherit'}
                      startIcon={<OpenInNewIcon />}
                      onClick={handleOpenCodeServer}
                      sx={{ fontSize: '0.75rem' }}>
                      {t('awaitingCode.openCodeServer')}
                    </Button>
                  )}
                </Box>
              </Box>
            ) : null;

          // In INTEGRATION mode, MSCIGT is forced: show a read-only annotation chip
          const integrationMscigt = state.workflowMode === 'INTEGRATION' && def.id === 'MSCIGT';

          return (
            <Fragment key={def.id}>
              {divider}
              {integrationMscigt && canStart && (
                <Chip
                  size="small"
                  label={t('dialog.phases.MSCIGT.integrationForced')}
                  sx={{ mb: 0.5, fontSize: '0.68rem', opacity: 0.85, alignSelf: 'flex-start' }}
                />
              )}
              <PhaseCard
                key={def.id}
                def={def}
                phaseStatus={phase.status}
                activeSubStatus={state.subStatus}
                enabled={enabled}
                disabled={isPhaseDisabled(def.id, state.phaseSelection)}
                canConfigure={canStart || (canConfigureExecution && EXECUTION_PHASES.includes(def.id))}
                onToggle={() => handlePhaseToggle(def.id)}
                onContinue={handleContinueFromFailed}
                phaseParams={state.phaseParams[def.id as keyof PhaseParams] || {}}
                onParamChange={(key, value) => handleParamChange(def.id, key, value as string | string[])}
                availableComponents={availableComponents}
              />
            </Fragment>
          );
        })}

        {/* ── Component Version Selection (INTEGRATION mode only) ─────── */}
        {canStart && state.workflowMode === 'INTEGRATION' && (
          <Box sx={{ mt: 2, mb: 2 }}>
            <Typography variant="subtitle2" className={classes.sectionTitle}>
              组件版本选择
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1.5 }}>
              选择要用于 LDP（加载-部署-运行）操作的组件源码版本。可按标签过滤，支持批量选择。
            </Typography>
            <ComponentVersionSelector
              components={(state.availableComponentVersions?.components || []).map((comp: any) => ({
                componentId: comp.componentId,
                componentName: comp.componentName,
                versions: comp.versions.map((v: any) => ({
                  id: v.id,
                  componentId: comp.componentId,
                  componentName: comp.componentName,
                  versionName: v.versionName,
                  createdAt: v.createdAt,
                  author: v.author,
                  tags: v.tags,
                  commitMessage: null,
                  modelVersionId: null,
                })) as ComponentCodeVersion[],
              }))}
              availableTags={(state.availableTags?.tags || []) as ComponentCodeTag[]}
              selectedVersions={state.selectedComponentVersions}
              onSelectionChange={(selections) =>
                setState((prev) => ({ ...prev, selectedComponentVersions: selections }))
              }
            />
          </Box>
        )}

        {/* ── Options row (only before start) ─────────────────────────── */}

        {/* ── EXECUTION_READY banner ───────────────────────────────────── */}
        {isExecutionReady && (
          <Alert severity="success" sx={{ mt: 1.5, mb: 1 }} icon={<PlayArrowIcon fontSize="inherit" />}>
            <AlertTitle>{t('executionReady.title')}</AlertTitle>
            {t('executionReady.message')}
          </Alert>
        )}

        {/* ── AWAITING_CODE banner ──────────────────────────────────────── */}
        {isAwaitingCode && (
          <Alert
            severity="info"
            sx={{ mt: 1.5, mb: 1 }}
            icon={<HourglassTopIcon fontSize="inherit" />}
            action={
              <Button color="info" size="small" startIcon={<OpenInNewIcon />} onClick={handleOpenCodeServer}>
                {t('awaitingCode.openCodeServer')}
              </Button>
            }>
            <AlertTitle>{t('awaitingCode.title')}</AlertTitle>
            {t('awaitingCode.message')}
          </Alert>
        )}

        {/* ── SOURCE_PREP_REQUIRED: three entry points ──────────────────── */}
        {state.status === 'SOURCE_PREP_REQUIRED' && (
          <Alert severity="warning" sx={{ mt: 1.5, mb: 1 }}>
            <AlertTitle>{t('sourcePrep.title')}</AlertTitle>
            {t('sourcePrep.message')}
            <Box sx={{ mt: 1.5, display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<OpenInNewIcon />}
                onClick={handleOpenCodeServer}>
                {t('sourcePrep.openCodeServer')}
              </Button>
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<CheckCircleIcon />}
                onClick={() => {
                  setState((prev) => ({
                    ...prev,
                    sourceReadinessEvidence: 'MANUAL_CONFIRM',
                    status: 'AWAITING_CODE',
                  }));
                }}>
                {t('sourcePrep.manualConfirm')}
              </Button>
            </Box>
          </Alert>
        )}

        {/* ── CSMGVT Result View (4 sub-steps) ─────────────────────────────── */}
        {(state.csmgvtProductCheck ||
          state.csmgvtCompileErrors.length > 0 ||
          state.csmgvtCsmResult ||
          state.csmgvtResult) && (
          <Box sx={{ mt: 1.5, mb: 1, border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
            <Typography variant="subtitle2" gutterBottom>
              {t('csmgvtResult.title')}
            </Typography>

            {/* Sub-step 1: Output product check */}
            {state.csmgvtProductCheck && (
              <Box sx={{ mb: 1 }}>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                  {t('csmgvtResult.productCheck')}
                </Typography>
                {state.csmgvtProductCheck.missingProducts.length > 0 ? (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {state.csmgvtProductCheck.foundProducts.map((p) => (
                      <Chip
                        key={p}
                        size="small"
                        label={p}
                        color="success"
                        variant="filled"
                        icon={<CheckCircleIcon />}
                      />
                    ))}
                    {state.csmgvtProductCheck.missingProducts.map((p) => (
                      <Chip key={p} size="small" label={p} color="error" variant="outlined" />
                    ))}
                  </Box>
                ) : (
                  <Chip
                    size="small"
                    label={t('csmgvtResult.allProductsFound')}
                    color="success"
                    icon={<CheckCircleIcon />}
                  />
                )}
              </Box>
            )}

            {/* Sub-step 2: Compile classification */}
            {state.csmgvtCompileErrors.length > 0 && (
              <Alert severity="error" sx={{ mb: 1 }}>
                <AlertTitle>{t('csmgvtResult.compileFailed')}</AlertTitle>
                {state.csmgvtCompileErrors.map((err) => (
                  <Typography key={err} variant="body2">
                    {err}
                  </Typography>
                ))}
              </Alert>
            )}

            {/* Sub-step 3: CSM execution result */}
            {state.csmgvtCsmResult && (
              <Box sx={{ mb: 1 }}>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                  {t('csmgvtResult.csmRun')}
                </Typography>
                {state.csmgvtCsmResult.csmRan ? (
                  state.csmgvtCsmResult.csmTimeoutNormal ? (
                    <Chip size="small" label={t('csmgvtResult.csmTimeout')} color="info" variant="outlined" />
                  ) : state.csmgvtCsmResult.csmReturnCode === 0 ? (
                    <Chip
                      size="small"
                      label={t('csmgvtResult.csmSuccess')}
                      color="success"
                      icon={<CheckCircleIcon />}
                    />
                  ) : (
                    <Chip
                      size="small"
                      label={`${t('csmgvtResult.csmFailed')} (rc=${state.csmgvtCsmResult.csmReturnCode})`}
                      color="error"
                      variant="outlined"
                    />
                  )
                ) : (
                  <Chip size="small" label={t('csmgvtResult.csmNotFound')} color="warning" variant="outlined" />
                )}
              </Box>
            )}

            {/* Sub-step 4: Runtime log trace check */}
            {state.csmgvtResult && (
              <>
                {state.csmgvtResult.isEmpty && (
                  <Alert severity="warning" sx={{ mb: 1 }}>
                    {t('csmgvtResult.emptyLog')}
                  </Alert>
                )}
                {state.csmgvtResult.runtimeLogFound && !state.csmgvtResult.isEmpty && (
                  <>
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                      {t('csmgvtResult.traceCheck')}
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
                      {Object.entries(state.csmgvtResult.keyTraces).map(([name, found]) => (
                        <Chip
                          key={name}
                          size="small"
                          label={name}
                          color={found ? 'success' : 'error'}
                          variant={found ? 'filled' : 'outlined'}
                          icon={found ? <CheckCircleIcon /> : undefined}
                        />
                      ))}
                    </Box>
                    {state.csmgvtResult.failureKeywords.length > 0 && (
                      <Alert severity="error" sx={{ mb: 1 }}>
                        <AlertTitle>{t('csmgvtResult.failureDetected')}</AlertTitle>
                        {state.csmgvtResult.failureKeywords.join(', ')}
                      </Alert>
                    )}
                  </>
                )}
                {!state.csmgvtResult.runtimeLogFound && (
                  <Typography variant="caption" color="text.secondary">
                    {t('csmgvtResult.noRuntimeLog')}
                  </Typography>
                )}
              </>
            )}
          </Box>
        )}

        {/* ── Progress bar ─────────────────────────────────────────────── */}
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5} mt={1}>
          <Typography variant="caption" color="text.secondary">
            {progressLabel}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {state.progress}%
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={state.progress}
          color={
            isCompleted
              ? 'success'
              : isFailed
              ? 'error'
              : isAwaitingCode
              ? 'warning'
              : isExecutionReady
              ? 'success'
              : 'primary'
          }
          sx={{ mb: 2 }}
        />

        {/* ── Log panel ────────────────────────────────────────────────── */}
        <Typography variant="subtitle2" className={classes.sectionTitle}>
          {t('dialog.logPanel.title')}
        </Typography>
        <div className={classes.logPanel} ref={logPanelRef} data-testid="generate-ecoa-log-panel">
          {state.logs.length === 0 ? (
            <p className={classes.logLine} style={{ color: '#808080' }}>
              {t('dialog.logPanel.placeholder')}
            </p>
          ) : (
            state.logs.map((line, idx) => {
              let displayLine = line;
              if (displayLine.includes('[STDERR]')) {
                if (displayLine.includes('INFO')) {
                  displayLine = displayLine.replace('[STDERR]', '[INFO]');
                } else if (displayLine.includes('WARNING')) {
                  displayLine = displayLine.replace('[STDERR]', '[WARNING]');
                }
              }
              const colorKey = getLogColor(displayLine);
              return (
                <p
                  key={idx}
                  className={cx(
                    classes.logLine,
                    colorKey ? (classes[colorKey as keyof typeof classes] as string) : undefined
                  )}>
                  {displayLine}
                </p>
              );
            })
          )}
        </div>

        {/* ── Error banner ─────────────────────────────────────────────── */}
        {state.errorMessage && (
          <Box mt={1} p={1} bgcolor="error.dark" borderRadius={1}>
            <Typography variant="caption" color="error.contrastText">
              {state.errorMessage}
            </Typography>
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={isRunning}>
          {t('dialog.buttons.close')}
        </Button>
        {canStart && (
          <Button
            variant="contained"
            color="primary"
            startIcon={<PlayArrowIcon />}
            onClick={handleStartGeneration}
            data-testid="generate-ecoa-start-button">
            {t('dialog.buttons.start')}
          </Button>
        )}
        {isAwaitingCode && (
          <>
            <Button variant="outlined" color="info" startIcon={<OpenInNewIcon />} onClick={handleOpenCodeServer}>
              {t('awaitingCode.openCodeServer')}
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<PlayArrowIcon />}
              onClick={handleContinueToExecution}
              data-testid="generate-ecoa-continue-execution-button">
              {t('awaitingCode.continueToExecution')}
            </Button>
          </>
        )}
        {isExecutionReady && (
          <>
            <Button variant="outlined" color="inherit" startIcon={<OpenInNewIcon />} onClick={handleOpenCodeServer}>
              {t('awaitingCode.openCodeServer')}
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<PlayArrowIcon />}
              onClick={handleStartGeneration}
              data-testid="generate-ecoa-start-execution-button">
              {t('executionReady.startButton')}
            </Button>
          </>
        )}
        {isRunning && (
          <Button variant="outlined" disabled startIcon={<CircularProgress size={14} />}>
            {t('dialog.buttons.generating')}
          </Button>
        )}
        {isCompleted && (
          <Button
            variant="contained"
            color="success"
            startIcon={<OpenInNewIcon />}
            onClick={handleOpenCodeServer}
            data-testid="generate-ecoa-open-editor-button">
            {t('dialog.buttons.openCodeServer')}
          </Button>
        )}
        {isFailed && (
          <>
            {(state.workflowMode === 'DIRECT_DEV' || state.workflowMode === 'HARNESS_DEV') && (
              <Button
                variant="outlined"
                color="warning"
                startIcon={<OpenInNewIcon />}
                onClick={handleOpenCodeServer}
                data-testid="generate-ecoa-fix-in-code-server-button">
                {t('dialog.buttons.fixInCodeServer')}
              </Button>
            )}
            <Button
              variant="outlined"
              color="error"
              startIcon={<PlayArrowIcon />}
              onClick={() =>
                setState((prev) => ({
                  ...initialState,
                  workflowMode: prev.workflowMode,
                  phaseSelection: prev.phaseSelection,
                  phaseParams: prev.phaseParams,
                  // Store the failed taskId so the next run uses rerun API (reusing
                  // the same workspace), avoiding the "DIRECT_DEV initial runs only
                  // allow EXVT and MSCIGT" error when retrying execution-phase failures.
                  retryFromTaskId: prev.taskId,
                }))
              }>
              {t('dialog.buttons.retry')}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};
