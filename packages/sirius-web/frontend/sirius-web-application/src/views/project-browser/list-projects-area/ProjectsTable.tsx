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

import { useTableTranslation } from '@eclipse-sirius/sirius-components-tables';
import DeleteIcon from '@mui/icons-material/Delete';
import Button from '@mui/material/Button';
import { MaterialReactTable, MRT_TableOptions, useMaterialReactTable } from 'material-react-table';
import { useTranslation } from 'react-i18next';
import { CursorBasedPagination } from '../../../table/CursorBasedPagination';
import { ProjectActionButton } from './ProjectActionButton';
import { ProjectsTableProps } from './ProjectsTable.types';
import { GQLProject } from './useProjects.types';
import { useProjectsTableColumns } from './useProjectsTableColumns';

export const ProjectsTable = ({
  loading,
  projects,
  rowCount,
  hasPreviousPage,
  hasNextPage,
  onPreviousPage,
  onNextPage,
  pageSize,
  onChange,
  onPageSizeChange,
  globalFilter,
  onGlobalFilterChange,
  sorting,
  onSortingChange,
  rowSelection,
  onRowSelectionChange,
  onDeleteSelected,
}: ProjectsTableProps) => {
  const { columns } = useProjectsTableColumns();
  const localization = useTableTranslation();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'projectsTable' });
  const tableOptions: MRT_TableOptions<GQLProject> = {
    columns,
    data: projects,
    rowCount: rowCount,
    enablePagination: true,
    manualPagination: true,
    enableColumnActions: false,
    enableColumnFilters: false,
    enableFullScreenToggle: false,
    enableDensityToggle: false,
    enableHiding: false,
    enableSorting: true,
    manualSorting: true,
    enableMultiSort: false,
    enableRowSelection: true,
    getRowId: (row) => row.id,
    onRowSelectionChange: onRowSelectionChange,
    muiTableBodyProps: () =>
      ({
        'data-testid': 'projects',
      } as any),
    state: { globalFilter, isLoading: loading, rowSelection, sorting },

    enableGlobalFilter: true,
    onGlobalFilterChange: onGlobalFilterChange,
    onSortingChange: onSortingChange,

    renderTopToolbarCustomActions: ({ table }) => {
      const hasSelection = table.getIsSomeRowsSelected() || table.getIsAllRowsSelected();
      return hasSelection ? (
        <Button color="secondary" onClick={onDeleteSelected} variant="contained" startIcon={<DeleteIcon />}>
          {t('deleteSelected')}
        </Button>
      ) : null;
    },

    enableRowActions: true,
    positionActionsColumn: 'last',
    displayColumnDefOptions: {
      'mrt-row-actions': {
        header: t('actions'),
        size: 10,
        grow: false,
      },
    },
    renderRowActions: ({ row }) => <ProjectActionButton project={row.original} onChange={onChange} />,

    enableBottomToolbar: true,
    renderBottomToolbar: () => (
      <CursorBasedPagination
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
        pageSize={pageSize}
        onPageSizeChange={onPageSizeChange}
      />
    ),

    localization: localization,
  };
  const table = useMaterialReactTable<GQLProject>(tableOptions);

  return <MaterialReactTable table={table} />;
};
