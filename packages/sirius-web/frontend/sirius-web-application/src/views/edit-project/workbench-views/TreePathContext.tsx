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
import { createContext, useContext, useState, useCallback } from 'react';
import { TreePathContextValue, TreePathEntry } from './TreePathContext.types';

export const TreePathContext = createContext<TreePathContextValue>({
  treePathEntries: {},
  setTreePathEntry: () => {},
});

export const useTreePathContext = (): TreePathContextValue => {
  return useContext(TreePathContext);
};

export const TreePathContextProvider = ({ children }: { children: React.ReactNode }) => {
  const [treePathEntries, setTreePathEntries] = useState<Record<string, TreePathEntry>>({});

  const setTreePathEntry = useCallback((itemId: string, entry: TreePathEntry | null) => {
    setTreePathEntries((prev) => {
      if (entry === null) {
        const next = { ...prev };
        delete next[itemId];
        return next;
      }
      if (
        prev[itemId] &&
        prev[itemId].ancestorLabels.length === entry.ancestorLabels.length &&
        prev[itemId].ancestorLabels.every((label, i) => label === entry.ancestorLabels[i]) &&
        prev[itemId].hasChildren === entry.hasChildren
      ) {
        return prev;
      }
      return { ...prev, [itemId]: entry };
    });
  }, []);

  return (
    <TreePathContext.Provider value={{ treePathEntries, setTreePathEntry }}>
      {children}
    </TreePathContext.Provider>
  );
};
