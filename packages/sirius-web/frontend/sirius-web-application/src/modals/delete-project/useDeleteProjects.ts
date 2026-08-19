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

import { gql, useMutation } from '@apollo/client';
import { useMultiToast } from '@eclipse-sirius/sirius-components-core';
import { useTranslation } from 'react-i18next';
import { GQLDeleteProjectMutationData, GQLDeleteProjectMutationVariables } from './useDeleteProject.types';

const deleteProjectMutation = gql`
  mutation deleteProject($input: DeleteProjectInput!) {
    deleteProject(input: $input) {
      __typename
      ... on ErrorPayload {
        message
      }
    }
  }
`;

export const useDeleteProjects = () => {
  const [performProjectDeletion] = useMutation<GQLDeleteProjectMutationData, GQLDeleteProjectMutationVariables>(
    deleteProjectMutation
  );

  const { addErrorMessage, addMessages } = useMultiToast();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'useDeleteProject' });

  const deleteProjects = async (projectIds: string[]) => {
    addMessages([{ body: t('deletingMultiple', { count: projectIds.length }), level: 'INFO' }]);

    let successCount = 0;
    for (const projectId of projectIds) {
      try {
        const variables: GQLDeleteProjectMutationVariables = {
          input: {
            id: crypto.randomUUID(),
            projectId,
          },
        };
        const { data, errors } = await performProjectDeletion({ variables });
        if (errors && errors.length > 0) {
          addErrorMessage(t('errors.unexpected'));
        } else if (data?.deleteProject.__typename === 'ErrorPayload') {
          addErrorMessage((data.deleteProject as any).message);
        } else {
          successCount++;
        }
      } catch (error) {
        addErrorMessage(t('errors.unexpected'));
      }
    }

    if (successCount > 0) {
      addMessages([{ body: t('deleteSuccessMultiple', { count: successCount }), level: 'SUCCESS' }]);
    }
    return successCount;
  };

  return {
    deleteProjects,
  };
};
