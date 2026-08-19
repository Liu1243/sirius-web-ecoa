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

/** Top-level state of a generation task, including the intermediate AWAITING_CODE state
 * where the skeleton has been generated and the user should write business logic in Code Server
 * before proceeding to execution (CSMGVT or LDP). */
export type TaskStatus =
  | 'INIT'
  | 'EXPORTING_XML'
  | 'GENERATING'
  | 'AWAITING_CODE'
  /** Front-end only: skeleton done, user confirmed code is ready, awaiting execution phase selection. */
  | 'EXECUTION_READY'
  | 'SOURCE_PREP_REQUIRED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

// ---------------------------------------------------------------------------
// Sub-status — which toolchain step is running
// ---------------------------------------------------------------------------
export type TaskSubStatus =
  | 'NONE'
  | 'RUNNING_EXVT'
  | 'RUNNING_MSCIGT'
  | 'RUNNING_ASCTG'
  | 'RUNNING_CSMGVT'
  | 'RUNNING_LDP'
  | 'SWITCHING_ACTIVE_PROJECT'
  | 'CODE_BACKFLOW_APPLIED'
  | 'CONFLICT';

// ---------------------------------------------------------------------------
// Phase-level status — one per pipeline phase
// ---------------------------------------------------------------------------
export type PhaseStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

/** Identifier for each pipeline phase. */
export type PhaseId = 'EXVT' | 'ASCTG' | 'MSCIGT' | 'CSMGVT' | 'LDP' | 'VERSION_SELECT';

export type WorkflowMode = 'DIRECT_DEV' | 'HARNESS_DEV' | 'INTEGRATION';

/** User-configured parameters for each phase. */
export type PhaseParams = Record<PhaseId, Record<string, string>>;

/** Per-phase execution status tracked locally in the dialog. */
export interface PhaseState {
  id: PhaseId;
  status: PhaseStatus;
  /** Tool IDs belonging to this phase (for sub-status mapping). */
  tools: TaskSubStatus[];
}

// ---------------------------------------------------------------------------
// API types (from backend)
// ---------------------------------------------------------------------------
export interface TaskStatusResponse {
  taskId: string;
  projectId: string;
  workspaceId: string | null;
  status: TaskStatus;
  subStatus: TaskSubStatus;
  progress: number;
  outputPath: string | null;
  logs: string[];
  workflowMode: WorkflowMode;
  baseProjectFile: string | null;
  activeProjectFile: string | null;
  harnessProjectFile: string | null;
  userId: string | null;
  sourceState: string | null;
  codeWorkspacePath: string | null;
  sourceReadinessEvidence: string | null;
  csmgvtResult: CsmgvtResult | null;
  csmgvtProductCheck: CsmgvtProductCheck | null;
  csmgvtCompileErrors: string[];
  csmgvtCsmResult: CsmgvtCsmResult | null;
  testWorkspacePath: string | null;
  patchArtifactPath: string | null;
  sourceVersionId: string | null;
  sourceRevision: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Structured CSMGVT runtime.log check result. */
export interface CsmgvtResult {
  runtimeLogFound: boolean;
  runtimeLogPath: string | null;
  keyTraces: Record<string, boolean>;
  failureKeywords: string[];
  isEmpty: boolean;
}

// ---------------------------------------------------------------------------
// Component Version Selection for INTEGRATION mode
// ---------------------------------------------------------------------------

/** Represents a selected component version for LDP in INTEGRATION mode. */
export interface SelectedComponentVersion {
  componentId: string;
  componentName: string;
  versionId: string;
  versionName: string;
  tags: { id: string; name: string; color: string }[];
}

/** Input for fetching component versions for selection. */
export interface GetComponentVersionsInput {
  projectId: string;
}

/** Payload containing available component versions grouped by component. */
export interface ComponentVersionsPayload {
  components: {
    componentId: string;
    componentName: string;
    versions: {
      id: string;
      versionName: string;
      createdAt: string;
      author: string;
      tags: { id: string; name: string; color: string }[];
    }[];
  }[];
}

/** Payload containing available tags for filtering. */
export interface ComponentTagsPayload {
  tags: { id: string; name: string; color: string }[];
}

/** CSMGVT output product check result. */
export interface CsmgvtProductCheck {
  outputDirFound: boolean;
  outputDirPath: string | null;
  missingProducts: string[];
  foundProducts: string[];
}

/** CSMGVT csm execution result. */
export interface CsmgvtCsmResult {
  csmRan: boolean;
  csmReturnCode: number;
  csmStdout: string;
  csmStderr: string;
  csmTimedOut: boolean;
  csmTimeoutNormal: boolean;
}

// ---------------------------------------------------------------------------
// Dialog props & state
// ---------------------------------------------------------------------------
export interface GenerateEcoaDialogProps {
  open: boolean;
  project: {
    id: string;
  };
  onClose: () => void;
  initialPhasesToRun?: string[];
  initialWorkflowMode?: WorkflowMode;
  /**
   * When set (from a Re-run action in History), the dialog starts in config
   * mode (INIT state) so the user can choose which phases to run before
   * clicking Start. On Start, the rerun API is called with the selected phases.
   */
  rerunSourceTaskId?: string;
  /**
   * When true, indicates this is continuing from an active AWAITING_CODE task.
   * In this case, the continue API will be used instead of rerun API,
   * allowing execution of CSMGVT/LDP phases (continuing=true).
   */
  continueFromActiveTask?: boolean;
}

/** Which phases the user has chosen to execute. */
export interface PhaseSelection {
  EXVT: boolean;
  ASCTG: boolean;
  MSCIGT: boolean;
  CSMGVT: boolean;
  LDP: boolean;
  VERSION_SELECT: boolean;
}

export interface GenerateEcoaDialogState {
  /** null = not started */
  taskId: string | null;
  workflowMode: WorkflowMode;
  /**
   * When the user clicks "Continue to Execution" after AWAITING_CODE,
   * the original taskId is moved here so the next generation call goes through
   * the rerun API (sharing the same workspaceId / skeleton directory).
   */
  pendingRerunFromTaskId: string | null;
  /**
   * When the user clicks "Retry" after a FAILED task, the failed taskId is stored
   * here so the next generation call uses the rerun API (reusing the same workspace).
   * This prevents the "DIRECT_DEV initial runs only allow EXVT and MSCIGT" error
   * that occurs when execution-phase failures are retried via the fresh generate API.
   */
  retryFromTaskId: string | null;
  status: TaskStatus;
  subStatus: TaskSubStatus;
  progress: number;
  outputPath: string | null;
  logs: string[];
  errorMessage: string | null;
  /** Per-phase status, updated as callbacks arrive. */
  phases: PhaseState[];
  /** Phase selection checkboxes (config before run). */
  phaseSelection: PhaseSelection;
  /** Custom parameters configuration for each phase. */
  phaseParams: PhaseParams;
  /** Structured CSMGVT runtime.log check result (null until CSMGVT completes). */
  csmgvtResult: CsmgvtResult | null;
  /** CSMGVT output product check result. */
  csmgvtProductCheck: CsmgvtProductCheck | null;
  /** CSMGVT classified compile errors. */
  csmgvtCompileErrors: string[];
  /** CSMGVT csm execution result. */
  csmgvtCsmResult: CsmgvtCsmResult | null;
  /** Code backflow audit: test workspace path. */
  testWorkspacePath: string | null;
  /** Code backflow audit: patch artifact path. */
  patchArtifactPath: string | null;
  /** Code backflow audit: source version ID. */
  sourceVersionId: string | null;
  /** Code backflow audit: source revision hash. */
  sourceRevision: string | null;
  /**
   * INTEGRATION mode: selected component versions for LDP.
   * Key: componentId, Value: selected version info
   */
  selectedComponentVersions: SelectedComponentVersion[];
  /** Available component versions for selection (INTEGRATION mode). */
  availableComponentVersions: ComponentVersionsPayload | null;
  /** Available tags for filtering (INTEGRATION mode). */
  availableTags: ComponentTagsPayload | null;
}

export interface GenerationHistoryDialogProps {
  open: boolean;
  project: {
    id: string;
  };
  onClose: () => void;
  /**
   * Called when the user clicks "Re-run" on a history item.
   * `sourceStatus` is the status of the original task so the caller can
   * decide which phases to pre-select (e.g. AWAITING_CODE → only CSMGVT + LDP).
   * `continuing` is true when an AWAITING_CODE task should continue via /continue/{taskId}.
   */
  onRerun?: (taskId: string, sourceStatus?: TaskStatus, workflowMode?: WorkflowMode, continuing?: boolean) => void;
}
