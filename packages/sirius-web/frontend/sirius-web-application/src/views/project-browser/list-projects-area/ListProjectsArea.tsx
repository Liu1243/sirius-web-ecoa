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

import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { makeStyles } from 'tss-react/mui';
import { DeleteProjectsModal } from '../../../modals/delete-project/DeleteProjectsModal';
import { useDeleteProjects } from '../../../modals/delete-project/useDeleteProjects';
import { ListProjectsAreaProps, ListProjectsAreaState } from './ListProjectsArea.types';
import { ProjectsTable } from './ProjectsTable';
import { useProjects } from './useProjects';
import { GQLProject } from './useProjects.types';

const useListProjectsAreaStyles = makeStyles()((theme) => ({
  listProjectsArea: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(5),
  },
  header: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
}));

export const ListProjectsArea = ({}: ListProjectsAreaProps) => {
  const { classes } = useListProjectsAreaStyles();

  const [state, setState] = useState<ListProjectsAreaState & { showDeleteModal: boolean }>({
    pageSize: 20,
    startCursor: null,
    endCursor: null,
    globalFilter: '',
    sorting: [{ id: 'createdOn', desc: true }],
    rowSelection: {},
    showDeleteModal: false,
  });

  const projectSort =
    state.sorting[0]?.id === 'createdOn'
      ? { field: 'CREATED_ON' as const, direction: state.sorting[0].desc ? ('DESC' as const) : ('ASC' as const) }
      : state.sorting[0]?.id === 'lastModifiedOn'
      ? {
          field: 'LAST_MODIFIED_ON' as const,
          direction: state.sorting[0].desc ? ('DESC' as const) : ('ASC' as const),
        }
      : { field: 'NAME' as const, direction: state.sorting[0]?.desc ? ('DESC' as const) : ('ASC' as const) };

  const { data, loading, refreshProjects } = useProjects(
    state.startCursor,
    state.endCursor,
    state.pageSize,
    {
      name: { contains: state.globalFilter },
    },
    projectSort
  );

  const { deleteProjects } = useDeleteProjects();

  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'listProjectsArea' });

  const onPreviousPage = () => {
    setState((prevState) => ({
      ...prevState,
      startCursor: null,
      endCursor: data.viewer.projects.pageInfo.startCursor ?? null,
    }));
  };

  const onNextPage = () => {
    setState((prevState) => ({
      ...prevState,
      startCursor: data.viewer.projects.pageInfo.endCursor ?? null,
      endCursor: null,
    }));
  };

  const onPageSizeChange = (pageSize: number) =>
    setState((prevState) => ({
      ...prevState,
      pageSize,
      startCursor: null,
      endCursor: null,
    }));

  const onRefreshProjects = () => {
    refreshProjects();
  };

  const onGlobalFilterChange = (globalFilter: string) => {
    setState((prevState) => ({ ...prevState, globalFilter, startCursor: null, endCursor: null }));
  };

  const onSortingChange = (updater: any) => {
    setState((prevState) => {
      const sorting = typeof updater === 'function' ? updater(prevState.sorting) : updater;
      return { ...prevState, sorting, startCursor: null, endCursor: null };
    });
  };

  const onRowSelectionChange = (updater: any) => {
    setState((prevState) => ({
      ...prevState,
      rowSelection: typeof updater === 'function' ? updater(prevState.rowSelection) : updater,
    }));
  };

  const onDeleteSelected = () => {
    setState((prevState) => ({ ...prevState, showDeleteModal: true }));
  };

  const selectedProjectIds = Object.keys(state.rowSelection);

  const hasPreviousPage = data?.viewer.projects.pageInfo.hasPreviousPage ?? false;
  const hasNextPage = data?.viewer.projects.pageInfo.hasNextPage ?? false;
  const count = data?.viewer.projects.pageInfo.count ?? 0;
  const projects: GQLProject[] = data?.viewer.projects.edges.map((edge) => edge.node) ?? [];

  return (
    <div className={classes.listProjectsArea}>
      <div className={classes.header}>
        <Typography variant="h4">{t('existingProjects')}</Typography>
      </div>
      <div>
        <ProjectsTable
          loading={loading}
          projects={projects}
          rowCount={count}
          hasPreviousPage={hasPreviousPage}
          hasNextPage={hasNextPage}
          onPreviousPage={onPreviousPage}
          onNextPage={onNextPage}
          pageSize={state.pageSize}
          onChange={onRefreshProjects}
          onPageSizeChange={onPageSizeChange}
          globalFilter={state.globalFilter}
          onGlobalFilterChange={onGlobalFilterChange}
          sorting={state.sorting}
          onSortingChange={onSortingChange}
          rowSelection={state.rowSelection}
          onRowSelectionChange={onRowSelectionChange}
          onDeleteSelected={onDeleteSelected}
        />
      </div>
      {state.showDeleteModal && (
        <DeleteProjectsModal
          projectIds={selectedProjectIds}
          onCancel={() => setState((prevState) => ({ ...prevState, showDeleteModal: false }))}
          onSuccess={() => {
            setState((prevState) => ({ ...prevState, showDeleteModal: false, rowSelection: {} }));
            deleteProjects(selectedProjectIds).then(() => {
              onRefreshProjects();
            });
          }}
        />
      )}
    </div>
  );
};
