import { describe, expect, it } from 'vitest';

import { defaultPhaseSelection, visiblePhases } from './GenerateEcoaDialog.workflow';

describe('AS6 workflow mode helpers', () => {
  it('returns HARNESS_DEV initial defaults', () => {
    expect(defaultPhaseSelection('HARNESS_DEV', 'initial')).toEqual({
      EXVT: true,
      ASCTG: true,
      MSCIGT: true,
      CSMGVT: false,
      LDP: false,
      VERSION_SELECT: false,
    });
  });

  it('returns HARNESS_DEV execution defaults (CSMGVT only, no LDP)', () => {
    expect(defaultPhaseSelection('HARNESS_DEV', 'execution')).toEqual({
      EXVT: false,
      ASCTG: false,
      MSCIGT: false,
      CSMGVT: true,
      LDP: false,
      VERSION_SELECT: false,
    });
  });

  it('returns INTEGRATION defaults with MSCIGT forced and LDP pre-selected', () => {
    expect(defaultPhaseSelection('INTEGRATION', 'initial')).toEqual({
      EXVT: true,
      ASCTG: false,
      MSCIGT: true,
      CSMGVT: false,
      LDP: true,
      VERSION_SELECT: false,
    });
  });

  it('shows MSCIGT as forced step in INTEGRATION visiblePhases', () => {
    expect(visiblePhases('INTEGRATION')).toEqual(['EXVT', 'MSCIGT', 'CSMGVT', 'LDP']);
  });
});
