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

import { useCallback, useEffect, useState } from 'react';
import { PendingVersion } from './PendingImportDialog.types';

export const usePendingImport = (projectId: string, open: boolean) => {
  const [pendingVersions, setPendingVersions] = useState<PendingVersion[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchPending = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(`/api/edt/component-code/pending/${projectId}`);
      if (response.ok) {
        const data = await response.json();
        setPendingVersions(data.versions || []);
      }
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (open) {
      fetchPending();
    }
  }, [open, fetchPending]);

  const confirm = useCallback(
    async (acceptedVersionIds: string[], rejectedVersionIds: string[]) => {
      const response = await fetch(`/api/edt/component-code/confirm/${projectId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ acceptedVersionIds, rejectedVersionIds }),
      });
      return response.ok;
    },
    [projectId]
  );

  return { pendingVersions, loading, confirm };
};
