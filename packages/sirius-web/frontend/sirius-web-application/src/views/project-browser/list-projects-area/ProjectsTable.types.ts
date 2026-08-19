/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
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

import { MRT_RowSelectionState, MRT_SortingState } from 'material-react-table';
import { GQLProject } from './useProjects.types';

export interface ProjectsTableProps {
  loading: boolean;
  projects: GQLProject[];
  rowCount: number;
  hasPreviousPage: boolean;
  hasNextPage: boolean;
  onPreviousPage: () => void;
  onNextPage: () => void;
  pageSize: number;
  onChange: () => void;
  onPageSizeChange: (page: number) => void;
  globalFilter: string;
  onGlobalFilterChange: (globalFilter: string) => void;
  sorting: MRT_SortingState;
  onSortingChange: (sorting: MRT_SortingState | ((prev: MRT_SortingState) => MRT_SortingState)) => void;
  rowSelection: MRT_RowSelectionState;
  onRowSelectionChange: (
    rowSelection: MRT_RowSelectionState | ((prev: MRT_RowSelectionState) => MRT_RowSelectionState)
  ) => void;
  onDeleteSelected: () => void;
}
