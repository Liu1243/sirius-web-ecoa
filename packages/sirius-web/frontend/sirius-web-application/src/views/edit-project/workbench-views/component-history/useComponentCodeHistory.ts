import { useCallback, useEffect, useState } from 'react';
import { gql, useQuery, useMutation } from '@apollo/client';
import { ComponentCodeHistory, ComponentCodeTag } from './ComponentHistoryView.types';
import { UseComponentCodeHistoryValue } from './useComponentCodeHistory.types';

const GET_COMPONENT_CODE_HISTORY = gql`
  query getComponentCodeHistory($projectId: ID!) {
    componentCodeHistory(input: { projectId: $projectId }) {
      history {
        components {
          componentId
          componentName
          versions {
            id
            componentId
            componentName
            versionName
            commitMessage
            author
            createdAt
            modelVersionId
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

const GET_COMPONENT_CODE_TAGS = gql`
  query getComponentCodeTags($projectId: ID!) {
    componentCodeTags(input: { projectId: $projectId }) {
      tags {
        id
        name
        color
      }
    }
  }
`;

const DELETE_COMPONENT_CODE_VERSION = gql`
  mutation deleteComponentCodeVersion($input: DeleteComponentCodeVersionInput!) {
    deleteComponentCodeVersion(input: $input) {
      versionId
    }
  }
`;

export const useComponentCodeHistory = (projectId: string): UseComponentCodeHistoryValue => {
  const [history, setHistory] = useState<ComponentCodeHistory | null>(null);
  const [tags, setTags] = useState<ComponentCodeTag[]>([]);

  const {
    data: historyData,
    loading: historyLoading,
    error: historyError,
    refetch: refetchHistory,
  } = useQuery(GET_COMPONENT_CODE_HISTORY, {
    variables: { projectId },
    fetchPolicy: 'cache-and-network',
  });

  const { data: tagsData, refetch: refetchTags } = useQuery(GET_COMPONENT_CODE_TAGS, {
    variables: { projectId },
    fetchPolicy: 'cache-and-network',
  });

  const [deleteVersionMutation] = useMutation(DELETE_COMPONENT_CODE_VERSION);

  useEffect(() => {
    if (historyData?.componentCodeHistory?.history) {
      setHistory(historyData.componentCodeHistory.history);
    }
  }, [historyData?.componentCodeHistory?.history]);

  useEffect(() => {
    if (tagsData?.componentCodeTags?.tags) {
      setTags(tagsData.componentCodeTags.tags);
    }
  }, [tagsData?.componentCodeTags?.tags]);

  const refresh = useCallback(() => {
    refetchHistory();
    refetchTags();
  }, [refetchHistory, refetchTags]);

  const deleteVersion = useCallback(
    async (versionId: string): Promise<boolean> => {
      try {
        await deleteVersionMutation({
          variables: {
            input: {
              versionId,
              id: crypto.randomUUID(),
            },
          },
        });
        await refetchHistory();
        return true;
      } catch (error) {
        console.error('Failed to delete version:', error);
        return false;
      }
    },
    [deleteVersionMutation, refetchHistory]
  );

  const deleteVersions = useCallback(
    async (versionIds: string[]): Promise<{ success: string[]; failed: string[] }> => {
      const result = { success: [] as string[], failed: [] as string[] };

      await Promise.all(
        versionIds.map(async (versionId) => {
          try {
            await deleteVersionMutation({
              variables: {
                input: {
                  versionId,
                  id: crypto.randomUUID(),
                },
              },
            });
            result.success.push(versionId);
          } catch (error) {
            console.error(`Failed to delete version ${versionId}:`, error);
            result.failed.push(versionId);
          }
        })
      );

      await refetchHistory();
      return result;
    },
    [deleteVersionMutation, refetchHistory]
  );

  return {
    state: {
      history,
      tags,
      loading: historyLoading,
      error: historyError,
    },
    refresh,
    deleteVersion,
    deleteVersions,
  };
};
