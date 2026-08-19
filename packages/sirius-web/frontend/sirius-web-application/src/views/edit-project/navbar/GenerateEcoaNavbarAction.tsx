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

import HistoryIcon from '@mui/icons-material/History';
import MemoryIcon from '@mui/icons-material/Memory';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import Button from '@mui/material/Button';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { GenerateEcoaDialog } from './context-menu/GenerateEcoaDialog';
import { WorkflowMode } from './context-menu/GenerateEcoaDialog.types';
import { GenerationHistoryDialog } from './context-menu/GenerationHistoryDialog';
import { ValidateEcoaDialog } from './context-menu/ValidateEcoaDialog';

export interface GenerateEcoaNavbarActionProps {
  project: {
    id: string;
  };
}

/**
 * A standalone navbar action component that provides a split button to trigger
 * ECOA code generation or view generation history.
 */

export const GenerateEcoaNavbarAction = ({ project }: GenerateEcoaNavbarActionProps) => {
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'generateEcoa' });
  const [generateOpen, setGenerateOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [validateOpen, setValidateOpen] = useState(false);
  const [initialPhases, setInitialPhases] = useState<string[] | undefined>();
  const [initialWorkflowMode, setInitialWorkflowMode] = useState<WorkflowMode>('DIRECT_DEV');
  const [rerunSourceTaskId, setRerunSourceTaskId] = useState<string | undefined>();
  // When continuing from a history AWAITING_CODE task, the dialog uses /continue/{taskId}
  const [continueFromActiveTask, setContinueFromActiveTask] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleGenerateClick = () => {
    // Normal new task flow
    setInitialPhases(undefined);
    setInitialWorkflowMode('DIRECT_DEV');
    setRerunSourceTaskId(undefined);
    setContinueFromActiveTask(false);
    setGenerateOpen(true);
  };

  const handleHistoryClick = () => {
    setHistoryOpen(true);
  };

  const handleGenerateClose = () => setGenerateOpen(false);
  const handleHistoryClose = () => setHistoryOpen(false);

  /**
   * Called when the user clicks the "Re-run" button on a history item.
   *
   * - `continuing=true`  → source task is AWAITING_CODE; open the dialog in
   *   "continue" mode so the user picks execution phases and triggers /continue/{taskId}.
   * - `continuing=false` → source task is COMPLETED / FAILED / other; open in
   *   "rerun" mode so the user picks any phases and triggers /rerun/{taskId}.
   */
  const handleRerun = (
    taskId: string,
    sourceStatus?: import('./context-menu/GenerateEcoaDialog.types').TaskStatus,
    workflowMode?: WorkflowMode,
    continuing?: boolean
  ) => {
    const resolvedMode: WorkflowMode = workflowMode ?? 'DIRECT_DEV';

    if (continuing) {
      // AWAITING_CODE task: continue with execution phases
      setInitialPhases(['CSMGVT', 'LDP']);
      setInitialWorkflowMode(resolvedMode);
      setRerunSourceTaskId(taskId);
      setContinueFromActiveTask(true);
    } else {
      // COMPLETED / FAILED / other: rerun from scratch in the same workspace
      // Pre-select phases based on source status to give a sensible default
      const isExecutionStatus = sourceStatus === 'COMPLETED' || sourceStatus === 'FAILED';
      const defaultPhases: string[] =
        isExecutionStatus && (resolvedMode === 'DIRECT_DEV' || resolvedMode === 'HARNESS_DEV')
          ? ['EXVT', 'MSCIGT']
          : (undefined as unknown as string[]);
      setInitialPhases(defaultPhases ?? undefined);
      setInitialWorkflowMode(resolvedMode);
      setRerunSourceTaskId(taskId);
      setContinueFromActiveTask(false);
    }

    setHistoryOpen(false);
    setGenerateOpen(true);
  };

  return (
    <>
      <div ref={containerRef} style={{ display: 'flex', gap: 8, marginRight: 16, alignItems: 'center' }}>
        <Button
          variant="contained"
          color="primary"
          size="small"
          onClick={handleGenerateClick}
          startIcon={<MemoryIcon />}
          data-testid="navbar-generate-ecoa-button"
          sx={{ whiteSpace: 'nowrap', textTransform: 'none', fontWeight: 500, paddingX: 2, height: 32 }}>
          {t('menuItem.generateEcoaCode')}
        </Button>
        <Button
          variant="outlined"
          color="primary"
          size="small"
          onClick={handleHistoryClick}
          startIcon={<HistoryIcon />}
          data-testid="navbar-generation-history-button"
          sx={{ whiteSpace: 'nowrap', textTransform: 'none', fontWeight: 500, paddingX: 2, height: 32 }}>
          {t('menuItem.generationHistory')}
        </Button>
        <Button
          variant="outlined"
          color="inherit"
          size="small"
          onClick={() => setValidateOpen(true)}
          startIcon={<VerifiedUserIcon />}
          data-testid="navbar-validate-ecoa-button"
          sx={{ whiteSpace: 'nowrap', textTransform: 'none', fontWeight: 500, paddingX: 2, height: 32 }}>
          {t('menuItem.validateXml')}
        </Button>
      </div>

      <GenerateEcoaDialog
        open={generateOpen}
        project={project}
        initialPhasesToRun={initialPhases}
        initialWorkflowMode={initialWorkflowMode}
        rerunSourceTaskId={rerunSourceTaskId}
        continueFromActiveTask={continueFromActiveTask}
        onClose={handleGenerateClose}
      />

      <GenerationHistoryDialog
        open={historyOpen}
        project={project}
        onClose={handleHistoryClose}
        onRerun={handleRerun}
      />

      <ValidateEcoaDialog open={validateOpen} project={project} onClose={() => setValidateOpen(false)} />
    </>
  );
};
