/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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
import { gql, useQuery } from '@apollo/client';
import { useMultiToast } from '@eclipse-sirius/sirius-components-core';
import { useEffect } from 'react';

const getAllRepresentationsQuery = gql`
  query getAllRepresentations($editingContextId: ID!) {
    viewer {
      editingContext(editingContextId: $editingContextId) {
        representations(first: 200) {
          edges {
            node {
              id
              label
              kind
              iconURLs
              description {
                id
              }
            }
          }
          pageInfo {
            hasNextPage
            count
          }
        }
      }
    }
  }
`;

interface GQLRepresentationNode {
  id: string;
  label: string;
  kind: string;
  iconURLs: string[];
  description: { id: string };
}

interface GQLAllRepresentationsData {
  viewer: {
    editingContext: {
      representations: {
        edges: Array<{ node: GQLRepresentationNode }>;
        pageInfo: { hasNextPage: boolean; count: number };
      };
    };
  };
}

interface GQLAllRepresentationsVariables {
  editingContextId: string;
}

export interface UseAllRepresentationsValue {
  representations: GQLRepresentationNode[];
  loading: boolean;
  refetch: () => void;
}

export const useAllRepresentations = (editingContextId: string): UseAllRepresentationsValue => {
  const { addErrorMessage } = useMultiToast();

  const { data, loading, error, refetch } = useQuery<GQLAllRepresentationsData, GQLAllRepresentationsVariables>(
    getAllRepresentationsQuery,
    {
      variables: { editingContextId },
      fetchPolicy: 'cache-and-network',
    }
  );

  useEffect(() => {
    if (error) {
      addErrorMessage('无法加载视图列表');
    }
  }, [error]);

  const representations = data?.viewer?.editingContext?.representations?.edges?.map((e) => e.node) ?? [];

  return { representations, loading, refetch };
};
