import { PhaseId, PhaseSelection, WorkflowMode } from './GenerateEcoaDialog.types';

export type WorkflowStage = 'initial' | 'execution';

const EMPTY_SELECTION: PhaseSelection = {
  EXVT: false,
  ASCTG: false,
  MSCIGT: false,
  CSMGVT: false,
  LDP: false,
  VERSION_SELECT: false,
};

export const defaultPhaseSelection = (mode: WorkflowMode, stage: WorkflowStage): PhaseSelection => {
  // DIRECT_DEV: initial = EXVT + MSCIGT, execution = CSMGVT
  if (mode === 'DIRECT_DEV' && stage === 'execution') {
    return {
      ...EMPTY_SELECTION,
      CSMGVT: true,
    };
  }

  if (mode === 'DIRECT_DEV') {
    return {
      ...EMPTY_SELECTION,
      EXVT: true,
      MSCIGT: true,
    };
  }

  // HARNESS_DEV: initial = EXVT + ASCTG + MSCIGT, execution = CSMGVT
  if (mode === 'HARNESS_DEV') {
    if (stage === 'execution') {
      return {
        ...EMPTY_SELECTION,
        CSMGVT: true,
      };
    }
    return {
      ...EMPTY_SELECTION,
      EXVT: true,
      ASCTG: true,
      MSCIGT: true,
    };
  }

  // INTEGRATION: EXVT + MSCIGT (forced/read-only) + LDP
  // MSCIGT is mandatory in INTEGRATION mode: it regenerates inc-gen/src-gen/ infrastructure
  // that code-backflow does NOT return. Without it, CSMGVT/LDP cannot compile.
  return {
    ...EMPTY_SELECTION,
    EXVT: true,
    MSCIGT: true,
    LDP: true,
  };
};

export const visiblePhases = (mode: WorkflowMode): PhaseId[] => {
  if (mode === 'INTEGRATION') {
    // INTEGRATION: EXVT + MSCIGT (read-only mandatory, runs implicitly before component overlay)
    // + CSMGVT (optional integration testing) + LDP (load-deploy-run)
    return ['EXVT', 'MSCIGT', 'CSMGVT', 'LDP'];
  }

  if (mode === 'DIRECT_DEV') {
    return ['EXVT', 'MSCIGT', 'CSMGVT', 'LDP'];
  }

  // HARNESS_DEV: unit-test isolation — CSMGVT only; LDP not applicable (other components are stubs)
  return ['EXVT', 'ASCTG', 'MSCIGT', 'CSMGVT'];
};

/** Whether the given workflow mode enters AWAITING_CODE after MSCIGT.
 * Both DIRECT_DEV and HARNESS_DEV wait for user to write code before continuing to execution phases.
 */
export const supportsAwaitingCode = (mode: WorkflowMode): boolean => {
  return mode === 'DIRECT_DEV' || mode === 'HARNESS_DEV';
};
