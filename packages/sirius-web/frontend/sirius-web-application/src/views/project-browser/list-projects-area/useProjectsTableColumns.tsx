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
import { useData } from '@eclipse-sirius/sirius-components-core';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { MRT_ColumnDef } from 'material-react-table';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import { GQLProject } from './useProjects.types';
import { UseProjectsTableColumnsValue } from './useProjectsTableColumns.types';
import { projectsTableColumnCustomizersExtensionPoint } from './useProjectsTableColumnsExtensionPoints';

export const useProjectsTableColumns = (): UseProjectsTableColumnsValue => {
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'useProjectsTableColumns' });
  const formatDateTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value));

  let columnDefinitions: MRT_ColumnDef<GQLProject>[] = [
    {
      accessorKey: 'name',
      header: t('name'),
      size: 200,
      grow: true,
      Cell: ({ row }) => (
        <Link
          component={RouterLink}
          underline="hover"
          to={`/projects/${row.original.id}/edit`}
          sx={{
            whiteSpace: 'nowrap',
            textOverflow: 'ellipsis',
            color: 'inherit',
          }}>
          {row.original.name}
        </Link>
      ),
    },
    {
      accessorKey: 'createdOn',
      header: t('createdOn'),
      size: 180,
      Cell: ({ row }) => <Typography noWrap>{formatDateTime(row.original.createdOn)}</Typography>,
    },
    {
      accessorKey: 'lastModifiedOn',
      header: t('lastModifiedOn'),
      size: 180,
      Cell: ({ row }) => <Typography noWrap>{formatDateTime(row.original.lastModifiedOn)}</Typography>,
    },
  ];

  const { data: customizers } = useData(projectsTableColumnCustomizersExtensionPoint);
  customizers.forEach((customizer) => {
    columnDefinitions = customizer.customize(columnDefinitions);
  });

  const columns = useMemo(() => columnDefinitions, []);

  return {
    columns,
  };
};
