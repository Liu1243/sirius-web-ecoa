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
import { gql, useQuery } from '@apollo/client';
import { VersionForDownload } from './DownloadProjectDialog.types';

const GET_ALL_VERSIONS = gql`
  query getComponentCodeHistoryForDownload($projectId: ID!) {
    componentCodeHistory(input: { projectId: $projectId }) {
      history {
        components {
          versions {
            id
            componentId
            componentName
            versionName
            createdAt
            tags {
              id
              name
              color
            }
          }
        }
      }
    }
  }
`;

export const useDownloadProject = (projectId: string, httpOrigin: string) => {
  const [versions, setVersions] = useState<VersionForDownload[]>([]);

  const { data, loading } = useQuery(GET_ALL_VERSIONS, {
    variables: { projectId },
    fetchPolicy: 'cache-and-network',
  });

  useEffect(() => {
    if (data?.componentCodeHistory?.history?.components) {
      const allVersions: VersionForDownload[] = [];
      for (const component of data.componentCodeHistory.history.components) {
        for (const version of component.versions) {
          allVersions.push(version);
        }
      }
      setVersions(allVersions);
    }
  }, [data]);

  const downloadWithVersions = useCallback(
    async (selectedVersionIds: string[], projectName: string) => {
      const response = await fetch(`${httpOrigin}/api/projects/${projectId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ versionIds: selectedVersionIds }),
      });
      if (!response.ok) return;
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${projectName}.zip`;
      a.click();
      URL.revokeObjectURL(url);
    },
    [httpOrigin, projectId]
  );

  return { versions, loading, downloadWithVersions };
};
