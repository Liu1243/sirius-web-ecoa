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
import { gql, useQuery } from '@apollo/client';
import { Selection, useSelection } from '@eclipse-sirius/sirius-components-core';
import { TreeItemContextMenuComponentProps } from '@eclipse-sirius/sirius-components-trees';
import AddIcon from '@mui/icons-material/Add';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import { forwardRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NewRepresentationModal } from './NewRepresentationModal';
import {
  GQLGetRepresentationDescriptionsQueryData,
  GQLGetRepresentationDescriptionsQueryVariables,
} from './NewRepresentationModal.types';
import { NewRepresentationTreeItemContextMenuContributionState } from './NewRepresentationTreeItemContextMenuContribution.types';
import {
  GQLGetExistingRepresentationsQueryData,
  GQLGetExistingRepresentationsQueryVariables,
} from './NewRepresentationTreeItemContextMenuContribution.types';

const getRepresentationDescriptionsQuery = gql`
  query getRepresentationDescriptions($editingContextId: ID!, $objectId: ID!) {
    viewer {
      editingContext(editingContextId: $editingContextId) {
        representationDescriptions(objectId: $objectId) {
          edges {
            node {
              id
              label
              defaultName
            }
          }
          pageInfo {
            hasNextPage
            hasPreviousPage
            startCursor
            endCursor
          }
        }
      }
    }
  }
`;

const getExistingRepresentationsQuery = gql`
  query getExistingRepresentations($editingContextId: ID!, $targetObjectId: ID!) {
    viewer {
      editingContext(editingContextId: $editingContextId) {
        representations(targetObjectId: $targetObjectId) {
          edges {
            node {
              id
              label
              kind
              iconURLs
              targetObjectId
            }
          }
        }
      }
    }
  }
`;

export const NewRepresentationTreeItemContextMenuContribution = forwardRef(
  (
    { editingContextId, item, readOnly, selectTreeItems, expandItem, onClose }: TreeItemContextMenuComponentProps,
    ref: React.ForwardedRef<HTMLLIElement>
  ) => {
    const [state, setState] = useState<NewRepresentationTreeItemContextMenuContributionState>({
      isModalOpen: false,
    });

    const { t } = useTranslation('sirius-web-application', {
      keyPrefix: 'newRepresentationTreeItemContextMenuContribution',
    });

    const { data } = useQuery<
      GQLGetRepresentationDescriptionsQueryData,
      GQLGetRepresentationDescriptionsQueryVariables
    >(getRepresentationDescriptionsQuery, { variables: { editingContextId, objectId: item.id } });

    const { data: existingRepresentationsData } = useQuery<
      GQLGetExistingRepresentationsQueryData,
      GQLGetExistingRepresentationsQueryVariables
    >(getExistingRepresentationsQuery, { variables: { editingContextId, targetObjectId: item.id } });

    const { setSelection } = useSelection();
    const onObjectCreated = (selection: Selection) => {
      setSelection(selection);
      selectTreeItems(selection.entries.map((entry) => entry.id));
      expandItem();
      onClose();
    };

    const existingRepresentations =
      existingRepresentationsData?.viewer.editingContext.representations.edges.map((edge) => edge.node) ?? [];

    const hasExistingRepresentations = existingRepresentations.length > 0;

    if (
      !data ||
      !data.viewer.editingContext.representationDescriptions ||
      (data.viewer.editingContext.representationDescriptions.edges
        .map((edge) => edge.node)
        .filter(
          (node) => node.label !== 'New Diagram Description' && node.label !== 'protal' && node.label !== 'Portal'
        ).length === 0 &&
        !hasExistingRepresentations)
    ) {
      return null;
    }

    if (hasExistingRepresentations) {
      const firstRepresentation = existingRepresentations[0];
      return (
        <MenuItem
          key="open-representation"
          onClick={() => {
            setSelection({ entries: [{ id: firstRepresentation.id }] });
            onClose();
          }}
          ref={ref}
          data-testid="open-representation">
          <ListItemIcon>
            <OpenInNewIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={t('openRepresentation')} />
        </MenuItem>
      );
    }

    return (
      <>
        <MenuItem
          key="new-representation"
          onClick={() => setState((prevState) => ({ ...prevState, isModalOpen: true }))}
          ref={ref}
          data-testid="new-representation"
          disabled={readOnly}
          aria-disabled>
          <ListItemIcon>
            <AddIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={t('newRepresentation')} />
        </MenuItem>

        {state.isModalOpen ? (
          <NewRepresentationModal
            editingContextId={editingContextId}
            item={item}
            onRepresentationCreated={onObjectCreated}
            onClose={onClose}
          />
        ) : null}
      </>
    );
  }
);
