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
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import DownloadIcon from '@mui/icons-material/Download';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PublishIcon from '@mui/icons-material/Publish';
import RefreshIcon from '@mui/icons-material/Refresh';
import SkipNextIcon from '@mui/icons-material/SkipNext';
import DeleteIcon from '@mui/icons-material/Delete';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import Checkbox from '@mui/material/Checkbox';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
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
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';
import { getCodeServerUrl } from '../../../../core/codeServer';
import { CodeBackflowDialog } from './CodeBackflowDialog';
import { GenerationHistoryDialogProps, TaskStatus, TaskStatusResponse } from './GenerateEcoaDialog.types';

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------
const useStyles = makeStyles()((theme) => ({
  dialogPaper: {
    minWidth: 700,
    maxWidth: 860,
  },
  historyItem: {
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    marginBottom: theme.spacing(1.5),
    overflow: 'hidden',
  },
  itemHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: theme.spacing(1),
    padding: theme.spacing(1, 1.5),
    cursor: 'pointer',
    userSelect: 'none',
    flexWrap: 'wrap',
    '&:hover': {
      backgroundColor: theme.palette.action.hover,
    },
  },
  itemMeta: {
    flex: 1,
    minWidth: 200,
    maxWidth: '100%',
  },
  taskId: {
    fontFamily: 'monospace',
    fontSize: '0.68rem',
    color: theme.palette.text.disabled,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  outputPath: {
    fontFamily: 'monospace',
    fontSize: '0.72rem',
    color: theme.palette.success.main,
    padding: theme.spacing(0.5, 1.5),
    backgroundColor: theme.palette.action.selected,
    borderBottom: `1px solid ${theme.palette.divider}`,
    wordBreak: 'break-all',
  },
  phaseAccordion: {
    boxShadow: 'none',
    borderBottom: `1px solid ${theme.palette.divider}`,
    '&:last-child': { borderBottom: 'none' },
    '&:before': { display: 'none' },
    margin: '0 !important',
  },
  phaseAccordionSummary: {
    minHeight: '40px !important',
    padding: theme.spacing(0, 1.5),
    '& .MuiAccordionSummary-content': {
      margin: theme.spacing(0.75, 0),
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1),
    },
  },
  phaseAccordionDetails: {
    padding: 0,
    backgroundColor: '#1a1a1a',
  },
  logPanel: {
    fontFamily: '"Cascadia Code", "Fira Code", "Consolas", monospace',
    fontSize: '0.72rem',
    color: '#d4d4d4',
    padding: theme.spacing(1, 1.5),
    maxHeight: 200,
    overflowY: 'auto',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-all',
    lineHeight: 1.65,
  },
  emptyLog: {
    color: '#606060',
    fontStyle: 'italic',
  },
  logError: { color: '#f48771' },
  logSuccess: { color: '#4ec9b0' },
  logInfo: { color: '#9cdcfe' },
  logWarn: { color: '#dcdcaa' },
  logSkip: { color: '#808080' },
  phaseTools: {
    display: 'flex',
    gap: theme.spacing(0.5),
  },
  toolBadge: {
    fontSize: '0.65rem',
    fontFamily: 'monospace',
    padding: theme.spacing(0.1, 0.6),
    borderRadius: 3,
    backgroundColor: theme.palette.action.selected,
    color: theme.palette.text.secondary,
  },
  actionButtons: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(0.5),
    flexShrink: 0,
    flexWrap: 'wrap',
    justifyContent: 'flex-end',
  },
  executedPhases: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: theme.spacing(0.5),
    padding: theme.spacing(0.5, 1.5),
    backgroundColor: theme.palette.action.hover,
    borderTop: `1px solid ${theme.palette.divider}`,
    minHeight: 28,
    alignItems: 'center',
  },
  phaseTag: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: theme.spacing(0.5),
    fontSize: '0.7rem',
    fontFamily: 'monospace',
    padding: theme.spacing(0.25, 0.75),
    borderRadius: 3,
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.paper,
  },
  phaseTag_completed: {
    borderColor: theme.palette.success.main,
    backgroundColor: theme.palette.success.light + '20',
  },
  phaseTag_failed: {
    borderColor: theme.palette.error.main,
    backgroundColor: theme.palette.error.light + '20',
  },
  phaseTag_skipped: {
    borderColor: theme.palette.text.disabled,
    backgroundColor: theme.palette.action.selected,
  },
  phaseTag_empty: {
    borderColor: theme.palette.divider,
    backgroundColor: theme.palette.action.selected,
  },
}));

// ---------------------------------------------------------------------------
// Computing node type (matches backend ComputingNodeInfo)
// ---------------------------------------------------------------------------

export interface ComputingNodeInfo {
  nodeId: string;
  protectionDomains: string[];
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Phase definition for log parsing */
interface PhaseDef {
  id: string;
  label: string; // Translation key suffix
  prefixes: string[]; // Log line prefixes belonging to this phase
  tools: string[];
}

const HISTORY_PHASES: PhaseDef[] = [
  {
    id: 'EXVT',
    label: 'EXVT',
    prefixes: ['[EXVT]'],
    tools: ['EXVT'],
  },
  {
    id: 'ASCTG',
    label: 'ASCTG',
    prefixes: ['[ASCTG]'],
    tools: ['ASCTG'],
  },
  {
    id: 'MSCIGT',
    label: 'MSCIGT',
    prefixes: ['[MSCIGT]'],
    tools: ['MSCIGT'],
  },
  {
    id: 'CSMGVT',
    label: 'CSMGVT',
    prefixes: ['[CSMGVT]'],
    tools: ['CSMGVT'],
  },
  {
    id: 'LDP',
    label: 'LDP',
    prefixes: ['[LDP]'],
    tools: ['LDP'],
  },
  {
    id: 'SYSTEM',
    label: 'System',
    prefixes: ['[ECOA-WEB]', '[INIT]', '[SUCCESS]', '[ERROR]', '[WARN]', '[SKIP]', '[MOCK]'],
    tools: [],
  },
];

// ---------------------------------------------------------------------------
// Log parsing helpers
// ---------------------------------------------------------------------------

function classifyLog(line: string): PhaseDef {
  // First try exact prefix match (e.g., "[EXVT]" or "[EXVT][INFO]")
  for (const phase of HISTORY_PHASES) {
    if (phase.prefixes.some((p) => line.startsWith(p))) {
      return phase;
    }
  }
  // Fallback: check if line contains phase pattern like [EXVT] anywhere
  for (const phase of HISTORY_PHASES) {
    if (phase.id !== 'SYSTEM') {
      const phasePattern = `[${phase.id}]`;
      if (line.includes(phasePattern)) {
        return phase;
      }
    }
  }
  return HISTORY_PHASES[HISTORY_PHASES.length - 1]!; // SYSTEM fallback
}

function groupLogsByPhase(logs: string[]): Record<string, string[]> {
  const grouped: Record<string, string[]> = {};
  for (const phase of HISTORY_PHASES) {
    grouped[phase.id] = [];
  }
  for (const line of logs) {
    const phase = classifyLog(line);
    grouped[phase.id]!.push(line);
  }
  return grouped;
}

function getPhaseStatus(
  phaseId: string,
  grouped: Record<string, string[]>
): 'completed' | 'failed' | 'skipped' | 'empty' {
  const lines = grouped[phaseId] ?? [];
  if (lines.length === 0) return 'empty';

  // Get the phase prefix for this phase (e.g., "[EXVT]", "[CSMGVT]")
  const phaseDef = HISTORY_PHASES.find((p) => p.id === phaseId);
  const phasePrefix = phaseDef?.prefixes[0] ?? `[${phaseId}]`;

  // Check for phase-specific errors (lines that start with phase prefix and contain ERROR/FAILED marker)
  const phaseLines = lines.filter((l) => l.startsWith(phasePrefix));

  // Check for actual error markers: [PHASE][ERROR] or [PHASE] [ERROR] or [PHASE] ... [FAILED]
  // But exclude patterns like "0 error messages" or "=== Errors ===" which are just headers/stats
  const hasError = phaseLines.some((l) => {
    const lower = l.toLowerCase();

    // Only check lines that actually contain the phase prefix
    if (!l.startsWith(phasePrefix)) return false;

    // EXCLUDE: Statistics lines showing "0 errors" or counts
    // Patterns: "- 0 error messages", "0 error message(s)", "=== CMake Errors ==="
    if (lower.match(/\b0\s+error/) || lower.match(/error\s*[=:]\s*0\b/)) return false;
    if (lower.match(/\b0\s+critical/) || lower.match(/\b0\s+warning/)) return false;
    // Exclude empty error section headers: "=== CMake Errors ===", "=== Make Errors ==="
    if (lower.match(/===\s*(cmake|make)?\s*errors?\s*===/)) return false;
    // Exclude summary report lines: " - X error message(s)"
    if (lower.match(/-\s*\d+\s+(error|warning|critical)\s+message/)) return false;
    // Exclude lines where [ERROR] is followed by timestamp + INFO level (malformed log)
    if (l.match(/\[ERROR\]\s+\d{4}-\d{2}-\d{2}.*\|\s*INFO\s*\|.*\berror\b/)) return false;

    // DETECT: Real error indicators
    // [PHASE]...[FAILED] pattern - this is always an error
    if (l.includes('[FAILED]')) return true;

    // [PHASE][ERROR] - check if it's a real error
    if (l.includes('[ERROR]')) {
      // Extract content after [ERROR]
      const afterError = l.split(/\[ERROR\]/).pop() || '';
      const afterLower = afterError.toLowerCase().trim();

      // Skip if content indicates 0 errors or is just a header
      if (afterLower.match(/^\s*-?\s*0\s+error/)) return false;
      if (afterLower.match(/^\s*===/)) return false;

      // Real error indicators
      const failureKeywords = ['failed', 'failure', 'exception', 'fatal', 'aborted', 'cannot', 'unable', 'timeout'];
      if (failureKeywords.some((k) => afterLower.includes(k))) return true;
      // Non-zero return code
      if (afterLower.match(/return_code=[1-9]/)) return true;
      // [ERROR] with actual descriptive content (not just stats)
      if (afterLower.length > 5 && !afterLower.match(/^\s*\d+/)) return true;
    }

    return false;
  });

  if (hasError) return 'failed';

  const hasSkip = phaseLines.some((l) => l.includes(`${phasePrefix}[SKIP]`) || l.includes(`${phasePrefix} [SKIP]`));
  if (hasSkip) return 'skipped';

  // If there are phase-specific lines and no errors/skips, it's completed
  if (phaseLines.length > 0) return 'completed';

  return 'completed';
}

function getLogLineClass(line: string, classes: Record<string, string>): string {
  // Match error markers more precisely: [PHASE] [ERROR] or [ERROR] at start
  if (line.match(/^\[\w+\] \[ERROR\]/) || line.startsWith('[ERROR]')) return classes.logError ?? '';
  // Match success markers
  if (line.includes('[SUCCESS]') || line.includes('✓') || line.includes('COMPLETED')) return classes.logSuccess ?? '';
  if (
    line.includes('[INFO]') ||
    line.includes('INFO') ||
    line.includes('[ECOA-WEB]') ||
    line.includes('[INIT]') ||
    line.includes('[MOCK]')
  )
    return classes.logInfo ?? '';
  if (line.includes('[WARNING]') || line.includes('WARNING') || line.includes('[WARN]')) return classes.logWarn ?? '';
  if (line.includes('[SKIP]') || line.includes('跳过')) return classes.logSkip ?? '';
  return '';
}

// ---------------------------------------------------------------------------
// Status helpers
// ---------------------------------------------------------------------------

const STATUS_CHIP_COLOR: Record<TaskStatus, 'success' | 'error' | 'primary' | 'warning' | 'default'> = {
  INIT: 'default',
  EXPORTING_XML: 'warning',
  GENERATING: 'primary',
  AWAITING_CODE: 'warning',
  EXECUTION_READY: 'success',
  SOURCE_PREP_REQUIRED: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
};

function StatusIcon({ status }: { status: TaskStatus }) {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircleIcon color="success" fontSize="small" />;
    case 'FAILED':
      return <ErrorIcon color="error" fontSize="small" />;
    case 'GENERATING':
      return <CircularProgress size={14} />;
    case 'EXPORTING_XML':
      return <CircularProgress size={14} color="warning" />;
    case 'AWAITING_CODE':
      return <HourglassTopIcon color="warning" fontSize="small" />;
    case 'SOURCE_PREP_REQUIRED':
      return <ErrorIcon color="warning" fontSize="small" />;
    case 'CANCELLED':
      return <SkipNextIcon color="disabled" fontSize="small" />;
    default:
      return null;
  }
}

function PhaseStatusIcon({ status }: { status: ReturnType<typeof getPhaseStatus> }) {
  switch (status) {
    case 'completed':
      return <CheckCircleIcon color="success" sx={{ fontSize: 14 }} />;
    case 'failed':
      return <ErrorIcon color="error" sx={{ fontSize: 14 }} />;
    case 'skipped':
      return <SkipNextIcon color="disabled" sx={{ fontSize: 14 }} />;
    default:
      return null;
  }
}

// ---------------------------------------------------------------------------
// PhaseLogPanel
// ---------------------------------------------------------------------------
interface PhaseLogPanelProps {
  phase: PhaseDef;
  logs: string[];
  status: ReturnType<typeof getPhaseStatus>;
  taskStatus: TaskStatus;
}

function PhaseLogPanel({ phase, logs, status, taskStatus }: PhaseLogPanelProps) {
  const { classes, cx } = useStyles();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });

  // Hide SYSTEM phase if there's nothing useful, and empty tool phases for active tasks
  const isEmpty = logs.length === 0;

  // Don't show empty phase sections for non-system phases if task is not completed
  if (isEmpty && taskStatus !== 'COMPLETED' && taskStatus !== 'FAILED' && phase.id !== 'SYSTEM') {
    return null;
  }

  const phaseTitle = phase.id === 'SYSTEM' ? t('historyDialog.phases.SYSTEM') : t(`historyDialog.phases.${phase.id}`);

  const isExpanded = status === 'failed' || status === 'completed';

  return (
    <Accordion defaultExpanded={isExpanded} className={classes.phaseAccordion} disableGutters>
      <AccordionSummary expandIcon={<ExpandMoreIcon sx={{ fontSize: 16 }} />} className={classes.phaseAccordionSummary}>
        <PhaseStatusIcon status={isEmpty ? 'empty' : status} />

        <Typography variant="caption" sx={{ fontWeight: 600, flex: 1 }}>
          {phaseTitle}
        </Typography>

        <div className={classes.phaseTools}>
          {phase.tools.map((tool) => (
            <span key={tool} className={classes.toolBadge}>
              {tool}
            </span>
          ))}
        </div>

        <Typography variant="caption" color="text.disabled" sx={{ ml: 1 }}>
          {logs.length > 0 ? `${logs.length} ${t('historyDialog.item.logCount')}` : t('historyDialog.item.noLog')}
        </Typography>
      </AccordionSummary>

      <AccordionDetails className={classes.phaseAccordionDetails}>
        <div className={classes.logPanel}>
          {logs.length === 0 ? (
            <span className={classes.emptyLog}>{t('historyDialog.item.noLog')}</span>
          ) : (
            logs.map((line, idx) => {
              let displayLine = line;
              if (displayLine.includes('[STDERR]')) {
                if (displayLine.includes('INFO')) {
                  displayLine = displayLine.replace('[STDERR]', '[INFO]');
                } else if (displayLine.includes('WARNING')) {
                  displayLine = displayLine.replace('[STDERR]', '[WARNING]');
                }
              }
              return (
                <div
                  key={idx}
                  className={cx(getLogLineClass(displayLine, classes as unknown as Record<string, string>))}>
                  {displayLine}
                </div>
              );
            })
          )}
        </div>
      </AccordionDetails>
    </Accordion>
  );
}

// ---------------------------------------------------------------------------
// HistoryItem
// ---------------------------------------------------------------------------
function HistoryItem({
  task,
  t,
  onRerun,
  httpOrigin,
  selected,
  onSelect,
  onDelete,
}: {
  task: TaskStatusResponse;
  t: (key: string, opts?: object) => string;
  onRerun?: (
    taskId: string,
    sourceStatus?: TaskStatus,
    workflowMode?: TaskStatusResponse['workflowMode'],
    continuing?: boolean
  ) => void;
  httpOrigin: string;
  selected: boolean;
  onSelect: (taskId: string, selected: boolean) => void;
  onDelete: (taskId: string) => void;
}) {
  const { classes, cx } = useStyles();
  const [expanded, setExpanded] = useState(false);
  const [backflowOpen, setBackflowOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  // Download menu state
  const [downloadMenuAnchor, setDownloadMenuAnchor] = useState<HTMLElement | null>(null);
  const [computingNodes, setComputingNodes] = useState<ComputingNodeInfo[] | null>(null);
  const [nodesLoading, setNodesLoading] = useState(false);
  const downloadMenuOpen = Boolean(downloadMenuAnchor);

  const createdDate = new Date(task.createdAt).toLocaleString('zh-CN');
  const updatedDate = new Date(task.updatedAt).toLocaleString('zh-CN');

  const grouped = groupLogsByPhase(task.logs);

  const handleOpenCodeServer = () => {
    const path = task.outputPath ?? `/workspace/${task.projectId}/${task.taskId}/Steps`;
    window.open(getCodeServerUrl(path), '_blank');
  };

  /** Download full workspace (no node filter) */
  const handleDownloadAll = useCallback(() => {
    window.open(`${httpOrigin}/api/edt/ecoa/generate/download/${task.taskId}`, '_blank');
  }, [httpOrigin, task.taskId]);

  /** Download workspace filtered for a specific computing node */
  const handleDownloadNode = useCallback(
    (nodeId: string) => {
      window.open(
        `${httpOrigin}/api/edt/ecoa/generate/download/${task.taskId}?nodeId=${encodeURIComponent(nodeId)}`,
        '_blank'
      );
    },
    [httpOrigin, task.taskId]
  );

  /** Open the download dropdown and lazily load the computing node list */
  const handleOpenDownloadMenu = useCallback(
    (event: React.MouseEvent<HTMLElement>) => {
      event.stopPropagation();
      setDownloadMenuAnchor(event.currentTarget);
      if (computingNodes === null && !nodesLoading) {
        setNodesLoading(true);
        fetch(`${httpOrigin}/api/edt/ecoa/generate/download/${task.taskId}/nodes`)
          .then((res) => (res.ok ? res.json() : []))
          .then((nodes: ComputingNodeInfo[]) => setComputingNodes(nodes))
          .catch(() => setComputingNodes([]))
          .finally(() => setNodesLoading(false));
      }
    },
    [httpOrigin, task.taskId, computingNodes, nodesLoading]
  );

  const handleCloseDownloadMenu = useCallback(() => {
    setDownloadMenuAnchor(null);
  }, []);

  const isTerminal =
    task.status === 'COMPLETED' ||
    task.status === 'FAILED' ||
    task.status === 'CANCELLED' ||
    task.status === 'AWAITING_CODE' || // treat as terminal for log display purposes
    task.status === 'SOURCE_PREP_REQUIRED';

  // Only allow workspace download if LDP phase completed successfully
  const ldpPhaseStatus = getPhaseStatus('LDP', grouped);
  const isLdpSuccessful = ldpPhaseStatus === 'completed';
  const isAwaitingCode =
    (task.workflowMode === 'HARNESS_DEV' || task.workflowMode === 'DIRECT_DEV') && task.status === 'AWAITING_CODE';
  const displayedHistoryPhases =
    task.workflowMode === 'INTEGRATION'
      ? HISTORY_PHASES.filter(
          (phase) => phase.id === 'EXVT' || phase.id === 'CSMGVT' || phase.id === 'LDP' || phase.id === 'SYSTEM'
        )
      : task.workflowMode === 'DIRECT_DEV'
      ? HISTORY_PHASES.filter((phase) => phase.id !== 'ASCTG')
      : HISTORY_PHASES;
  const totalLogs = task.logs.length;

  // Calculate which phases have been executed (have logs)
  const executedPhases = displayedHistoryPhases.filter((phase) => {
    const lines = grouped[phase.id] ?? [];
    return lines.length > 0 && phase.id !== 'SYSTEM';
  });

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteConfirmOpen(true);
  };

  const handleConfirmDelete = () => {
    setDeleteConfirmOpen(false);
    onDelete(task.taskId);
  };

  return (
    <div className={classes.historyItem}>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className={classes.itemHeader} onClick={() => setExpanded(!expanded)}>
        <Checkbox
          size="small"
          checked={selected}
          onChange={(e) => {
            e.stopPropagation();
            onSelect(task.taskId, e.target.checked);
          }}
          onClick={(e) => e.stopPropagation()}
        />

        <StatusIcon status={task.status} />

        <Chip
          label={t(`historyDialog.status.${task.status}`)}
          color={STATUS_CHIP_COLOR[task.status]}
          size="small"
          variant="outlined"
          sx={{ minWidth: 80 }}
        />

        <div className={classes.itemMeta}>
          <Typography variant="body2" component="div">
            {createdDate}
            {task.status === 'GENERATING' && (
              <Box component="span" sx={{ ml: 1 }}>
                <LinearProgress
                  variant="determinate"
                  value={task.progress}
                  sx={{ display: 'inline-flex', width: 80, verticalAlign: 'middle', ml: 1 }}
                />
                <Typography variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                  {task.progress}%
                </Typography>
              </Box>
            )}
          </Typography>
          <Typography variant="caption" className={classes.taskId}>
            {t('historyDialog.item.updated', { date: updatedDate })} | {totalLogs} {t('historyDialog.item.logCount')} |{' '}
            {task.taskId}
          </Typography>
        </div>

        <Box className={classes.actionButtons}>
          <Chip label={task.workflowMode} size="small" variant="outlined" />

          {onRerun && (
            <Tooltip
              title={
                isAwaitingCode
                  ? t('historyDialog.rerunExecution') // "骨架已就绪，选择执行分支继续运行"
                  : t('historyDialog.rerun')
              }>
              <IconButton
                size="small"
                color={isAwaitingCode ? 'warning' : 'primary'}
                onClick={(e) => {
                  e.stopPropagation();
                  // For AWAITING_CODE tasks: pass continuing=true so the caller can
                  // use /continue/{taskId} instead of /rerun/{taskId}.
                  onRerun(task.taskId, task.status, task.workflowMode, isAwaitingCode);
                }}>
                <PlayArrowIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}

          {/* Download workspace as ZIP: split button (full + per-node dropdown) - only show if LDP succeeded */}
          {isTerminal && isLdpSuccessful && (
            <Box sx={{ display: 'inline-flex', alignItems: 'center' }}>
              {/* Primary download button: full workspace */}
              <Tooltip title={t('historyDialog.item.download')}>
                <IconButton
                  size="small"
                  color="default"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDownloadAll();
                  }}>
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>

              {/* Dropdown arrow: opens per-node menu */}
              <Tooltip title={t('historyDialog.item.downloadByNode')}>
                <IconButton size="small" color="default" sx={{ ml: -0.5, p: '2px' }} onClick={handleOpenDownloadMenu}>
                  <ArrowDropDownIcon fontSize="small" />
                </IconButton>
              </Tooltip>

              {/* Per-computing-node download menu */}
              <Menu
                anchorEl={downloadMenuAnchor}
                open={downloadMenuOpen}
                onClose={handleCloseDownloadMenu}
                onClick={(e) => e.stopPropagation()}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
                {/* Always show full-workspace option */}
                <MenuItem
                  dense
                  onClick={() => {
                    handleDownloadAll();
                    handleCloseDownloadMenu();
                  }}>
                  <DownloadIcon fontSize="small" sx={{ mr: 1 }} />
                  <ListItemText
                    primary={t('historyDialog.item.downloadAll')}
                    secondary={t('historyDialog.item.downloadAllDesc')}
                    secondaryTypographyProps={{ fontSize: '0.7rem' }}
                  />
                </MenuItem>

                {/* Per-node options */}
                {nodesLoading && (
                  <MenuItem dense disabled>
                    <CircularProgress size={14} sx={{ mr: 1 }} />
                    <ListItemText primary={t('historyDialog.item.downloadNodesLoading')} />
                  </MenuItem>
                )}
                {!nodesLoading && computingNodes !== null && computingNodes.length === 0 && (
                  <MenuItem dense disabled>
                    <ListItemText primary={t('historyDialog.item.downloadNoNodes')} />
                  </MenuItem>
                )}
                {!nodesLoading &&
                  computingNodes !== null &&
                  computingNodes.length > 0 && [
                    <Divider key="divider" />,
                    ...computingNodes.map((node) => (
                      <MenuItem
                        key={node.nodeId}
                        dense
                        onClick={() => {
                          handleDownloadNode(node.nodeId);
                          handleCloseDownloadMenu();
                        }}>
                        <DownloadIcon fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />
                        <ListItemText
                          primary={`${t('historyDialog.item.downloadNode')} [${node.nodeId}]`}
                          secondary={node.protectionDomains.join(', ')}
                          secondaryTypographyProps={{ fontSize: '0.68rem' }}
                        />
                      </MenuItem>
                    )),
                  ]}
              </Menu>
            </Box>
          )}

          {/* Code Backflow button: for all completed tasks */}
          {task.status === 'COMPLETED' && (
            <Tooltip title={t('historyDialog.item.backflow')}>
              <IconButton
                size="small"
                color="primary"
                onClick={(e) => {
                  e.stopPropagation();
                  setBackflowOpen(true);
                }}>
                <PublishIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}

          {/* Open Code Server button: for AWAITING_CODE or COMPLETED tasks with outputPath */}
          {(isAwaitingCode || task.status === 'COMPLETED') && (
            <Tooltip
              title={
                isAwaitingCode
                  ? t('historyDialog.openCodeServerTooltip.awaiting')
                  : `Code-Server: ${task.outputPath ?? `/workspace/.../${task.taskId}/Steps`}`
              }>
              <IconButton
                size="small"
                color={isAwaitingCode ? 'warning' : 'success'}
                onClick={(e) => {
                  e.stopPropagation();
                  handleOpenCodeServer();
                }}>
                <OpenInNewIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>

        {/* Delete single task button */}
        <Tooltip title={t('historyDialog.item.delete')}>
          <IconButton size="small" color="error" onClick={handleDeleteClick}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        {expanded ? (
          <ExpandLessIcon fontSize="small" color="action" />
        ) : (
          <ExpandMoreIcon fontSize="small" color="action" />
        )}
      </div>

      {/* ── Executed phases (visible when collapsed) ───────────────────── */}
      {!expanded && (
        <div className={classes.executedPhases}>
          {executedPhases.length > 0 ? (
            executedPhases.map((phase) => {
              const phaseStatus = getPhaseStatus(phase.id, grouped);
              return (
                <Tooltip key={phase.id} title={t(`historyDialog.phases.${phase.id}`)}>
                  <span className={cx(classes.phaseTag, classes[`phaseTag_${phaseStatus}`])}>
                    <PhaseStatusIcon status={phaseStatus} />
                    {phase.id}
                  </span>
                </Tooltip>
              );
            })
          ) : totalLogs > 0 ? (
            <Typography variant="caption" color="text.secondary">
              {totalLogs} {t('historyDialog.item.logCount')}
            </Typography>
          ) : null}
        </div>
      )}

      {/* ── Output path ────────────────────────────────────────────────── */}
      {task.outputPath && expanded && <div className={classes.outputPath}>📁 {task.outputPath}</div>}
      {expanded && (task.activeProjectFile || task.harnessProjectFile) && (
        <Box sx={{ px: 1.5, py: 1, borderBottom: (theme) => `1px solid ${theme.palette.divider}` }}>
          <Typography variant="caption" display="block" color="text.secondary">
            {t('historyDialog.item.activeProjectFile')}: {task.activeProjectFile ?? '-'}
          </Typography>
          {task.harnessProjectFile && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.harnessProjectFile')}: {task.harnessProjectFile}
            </Typography>
          )}
        </Box>
      )}
      {expanded && (task.sourceState || task.codeWorkspacePath) && (
        <Box sx={{ px: 1.5, py: 1, borderBottom: (theme) => `1px solid ${theme.palette.divider}` }}>
          {task.sourceState && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.sourceState')}: {task.sourceState}
            </Typography>
          )}
          {task.codeWorkspacePath && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.codeWorkspacePath')}: {task.codeWorkspacePath}
            </Typography>
          )}
          {task.sourceReadinessEvidence && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.sourceReadinessEvidence')}: {task.sourceReadinessEvidence}
            </Typography>
          )}
          {task.activeProjectFile && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.activeProjectFile')}: {task.activeProjectFile}
            </Typography>
          )}
          {task.outputPath && (
            <Typography variant="caption" display="block" color="text.secondary">
              {t('historyDialog.item.workspacePath')}: {task.outputPath}
            </Typography>
          )}
        </Box>
      )}
      {expanded && (task.testWorkspacePath || task.patchArtifactPath || task.sourceVersionId || task.sourceRevision) && (
        <Box sx={{ px: 1.5, py: 1, borderBottom: (theme) => `1px solid ${theme.palette.divider}` }}>
          <Typography variant="caption" display="block" color="text.secondary" sx={{ mb: 0.5 }}>
            {t('historyDialog.item.backflowAudit')}:
          </Typography>
          {task.testWorkspacePath && (
            <Typography variant="caption" display="block" color="text.secondary">
              testWorkspace: {task.testWorkspacePath}
            </Typography>
          )}
          {task.patchArtifactPath && (
            <Typography variant="caption" display="block" color="text.secondary">
              patch: {task.patchArtifactPath}
            </Typography>
          )}
          {task.sourceVersionId && (
            <Typography variant="caption" display="block" color="text.secondary">
              sourceVersion: {task.sourceVersionId}
            </Typography>
          )}
          {task.sourceRevision && (
            <Typography variant="caption" display="block" color="text.secondary">
              revision: {task.sourceRevision}
            </Typography>
          )}
        </Box>
      )}
      {expanded && task.csmgvtResult && task.csmgvtResult.runtimeLogFound && (
        <Box sx={{ px: 1.5, py: 1, borderBottom: (theme) => `1px solid ${theme.palette.divider}` }}>
          <Typography variant="caption" display="block" color="text.secondary" sx={{ mb: 0.5 }}>
            {t('historyDialog.item.csmgvtResult')}:
          </Typography>
          {task.csmgvtResult.isEmpty && (
            <Typography variant="caption" display="block" color="warning.main">
              {t('historyDialog.item.csmgvtEmptyLog')}
            </Typography>
          )}
          {!task.csmgvtResult.isEmpty &&
            Object.entries(task.csmgvtResult.keyTraces).map(([name, found]) => (
              <Typography key={name} variant="caption" display="block" color={found ? 'success.main' : 'error.main'}>
                {found ? '✓' : '✗'} {name}
              </Typography>
            ))}
          {task.csmgvtResult.failureKeywords.length > 0 && (
            <Typography variant="caption" display="block" color="error.main">
              {t('historyDialog.item.csmgvtFailure')}: {task.csmgvtResult.failureKeywords.join(', ')}
            </Typography>
          )}
        </Box>
      )}

      {/* ── Phase log panels (expandable) ──────────────────────────────── */}
      <Collapse in={expanded}>
        <Divider />
        {displayedHistoryPhases.map((phase) => {
          const lines = grouped[phase.id] ?? [];
          const phaseStatus = getPhaseStatus(phase.id, grouped);

          // Hide empty system section unless there are actual log lines
          if (phase.id === 'SYSTEM' && lines.length === 0) return null;
          // Hide empty non-system phases for terminal tasks only if truly empty
          if (phase.id !== 'SYSTEM' && lines.length === 0 && isTerminal) return null;

          return (
            <PhaseLogPanel key={phase.id} phase={phase} logs={lines} status={phaseStatus} taskStatus={task.status} />
          );
        })}
        {task.logs.length === 0 && (
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="caption" color="text.secondary">
              {t('historyDialog.item.noLog')}
            </Typography>
          </Box>
        )}
      </Collapse>
      <CodeBackflowDialog
        open={backflowOpen}
        taskId={task.taskId}
        onClose={() => setBackflowOpen(false)}
        onApplied={() => setBackflowOpen(false)}
      />

      {/* Delete confirmation dialog */}
      <Dialog
        open={deleteConfirmOpen}
        onClose={() => setDeleteConfirmOpen(false)}
        maxWidth="xs"
        fullWidth
        closeAfterTransition>
        <DialogTitle>{t('historyDialog.deleteSingle.title')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            {t('historyDialog.deleteSingle.content', { taskId: task.taskId.substring(0, 8) })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)} size="small">
            {t('historyDialog.buttons.cancel')}
          </Button>
          <Button onClick={handleConfirmDelete} color="error" variant="contained" size="small">
            {t('historyDialog.buttons.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Dialog
// ---------------------------------------------------------------------------
export const GenerationHistoryDialog = ({ open, project, onClose, onRerun }: GenerationHistoryDialogProps) => {
  const { classes } = useStyles();
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });

  const [tasks, setTasks] = useState<TaskStatusResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedTasks, setSelectedTasks] = useState<Set<string>>(new Set());
  const [batchDeleteConfirmOpen, setBatchDeleteConfirmOpen] = useState(false);

  const loadHistory = async () => {
    setLoading(true);
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/generate/history/${project.id}`);
      if (resp.ok) {
        const data: TaskStatusResponse[] = await resp.json();
        // Sort newest first
        setTasks(data.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
      }
    } catch {
      // silently ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      void loadHistory();
      setSelectedTasks(new Set()); // Clear selection when dialog opens
    }
  }, [open]);

  const handleSelectTask = (taskId: string, selected: boolean) => {
    setSelectedTasks((prev) => {
      const next = new Set(prev);
      if (selected) {
        next.add(taskId);
      } else {
        next.delete(taskId);
      }
      return next;
    });
  };

  const handleSelectAll = (selected: boolean) => {
    if (selected) {
      setSelectedTasks(new Set(tasks.map((t) => t.taskId)));
    } else {
      setSelectedTasks(new Set());
    }
  };

  const handleDeleteTask = async (taskId: string) => {
    try {
      const resp = await fetch(`${httpOrigin}/api/edt/ecoa/generate/task/${taskId}`, {
        method: 'DELETE',
      });
      if (resp.ok) {
        setTasks((prev) => prev.filter((t) => t.taskId !== taskId));
        setSelectedTasks((prev) => {
          const next = new Set(prev);
          next.delete(taskId);
          return next;
        });
      }
    } catch {
      // silently ignore
    }
  };

  const handleBatchDelete = async () => {
    setBatchDeleteConfirmOpen(false);
    const taskIds = Array.from(selectedTasks);
    // Delete sequentially to avoid overwhelming the server
    for (const taskId of taskIds) {
      try {
        await fetch(`${httpOrigin}/api/edt/ecoa/generate/task/${taskId}`, {
          method: 'DELETE',
        });
      } catch {
        // silently ignore individual failures
      }
    }
    // Refresh the list
    void loadHistory();
    setSelectedTasks(new Set());
  };

  const allSelected = tasks.length > 0 && selectedTasks.size === tasks.length;
  const someSelected = selectedTasks.size > 0 && selectedTasks.size < tasks.length;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      classes={{ paper: classes.dialogPaper }}
      maxWidth="lg"
      fullWidth
      closeAfterTransition>
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" gap={1}>
            <Checkbox
              size="small"
              checked={allSelected}
              indeterminate={someSelected}
              onChange={(e) => handleSelectAll(e.target.checked)}
              disabled={tasks.length === 0}
            />
            <Typography variant="h6">{t('historyDialog.title')}</Typography>
            {selectedTasks.size > 0 && (
              <Chip
                label={`${selectedTasks.size} ${t('historyDialog.item.selected')}`}
                color="primary"
                size="small"
                variant="outlined"
              />
            )}
          </Box>
          <Box display="flex" alignItems="center" gap={1}>
            {selectedTasks.size > 0 && (
              <Tooltip title={t('historyDialog.batchDelete.tooltip')}>
                <Button
                  size="small"
                  color="error"
                  startIcon={<DeleteSweepIcon />}
                  onClick={() => setBatchDeleteConfirmOpen(true)}>
                  {t('historyDialog.batchDelete.button')}
                </Button>
              </Tooltip>
            )}
            <Tooltip title={t('historyDialog.refresh')}>
              <IconButton size="small" onClick={loadHistory} disabled={loading}>
                {loading ? <CircularProgress size={16} /> : <RefreshIcon />}
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </DialogTitle>

      <DialogContent sx={{ pb: 1 }}>
        {loading && tasks.length === 0 && (
          <Box display="flex" justifyContent="center" p={3}>
            <CircularProgress />
          </Box>
        )}
        {!loading && tasks.length === 0 && (
          <Typography color="text.secondary" align="center" sx={{ py: 3 }}>
            {t('historyDialog.noHistory')}
          </Typography>
        )}

        {tasks.map((task) => (
          <HistoryItem
            key={task.taskId}
            task={task}
            t={t as (key: string, opts?: object) => string}
            onRerun={onRerun}
            httpOrigin={httpOrigin}
            selected={selectedTasks.has(task.taskId)}
            onSelect={handleSelectTask}
            onDelete={handleDeleteTask}
          />
        ))}
      </DialogContent>

      {/* Batch delete confirmation dialog */}
      <Dialog
        open={batchDeleteConfirmOpen}
        onClose={() => setBatchDeleteConfirmOpen(false)}
        maxWidth="xs"
        fullWidth
        closeAfterTransition>
        <DialogTitle>{t('historyDialog.batchDelete.title')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            {t('historyDialog.batchDelete.content', { count: selectedTasks.size })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBatchDeleteConfirmOpen(false)} size="small">
            {t('historyDialog.buttons.cancel')}
          </Button>
          <Button onClick={handleBatchDelete} color="error" variant="contained" size="small">
            {t('historyDialog.buttons.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      <DialogActions>
        <Button onClick={onClose}>{t('historyDialog.close')}</Button>
      </DialogActions>
    </Dialog>
  );
};
