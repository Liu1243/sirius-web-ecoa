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
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import { forwardRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NewObjectModal } from './NewObjectModal';
import {
  GQLGetChildCreationDescriptionsQueryData,
  GQLGetChildCreationDescriptionsQueryVariables,
} from './NewObjectModal.types';
import { NewObjectTreeItemContextMenuContributionState } from './NewObjectTreeItemContextMenuContribution.types';

const getChildCreationDescriptionsQuery = gql`
  query getChildCreationDescriptions($editingContextId: ID!, $containerId: ID!) {
    viewer {
      editingContext(editingContextId: $editingContextId) {
        childCreationDescriptions(containerId: $containerId) {
          id
          label
          iconURL
        }
      }
    }
  }
`;

export const NewObjectTreeItemContextMenuContribution = forwardRef(
  (
    { editingContextId, item, readOnly, selectTreeItems, expandItem, onClose }: TreeItemContextMenuComponentProps,
    ref: React.ForwardedRef<HTMLLIElement>
  ) => {
    const [state, setState] = useState<NewObjectTreeItemContextMenuContributionState>({
      isModalOpen: false,
    });

    const { t } = useTranslation('sirius-web-application', { keyPrefix: 'newObjectTreeItemContextMenuContribution' });

    const { data } = useQuery<GQLGetChildCreationDescriptionsQueryData, GQLGetChildCreationDescriptionsQueryVariables>(
      getChildCreationDescriptionsQuery,
      { variables: { editingContextId, containerId: item.id } }
    );

    const { setSelection } = useSelection();
    const onObjectCreated = (selection: Selection) => {
      setSelection(selection);
      selectTreeItems(selection.entries.map((entry) => entry.id));
      expandItem();
      onClose();
    };

    if (
      !data ||
      !data.viewer.editingContext.childCreationDescriptions ||
      data.viewer.editingContext.childCreationDescriptions.length === 0
    ) {
      return null;
    }

    return (
      <>
        <MenuItem
          key="new-object"
          data-testid="new-object"
          onClick={() => setState((prevState) => ({ ...prevState, isModalOpen: true }))}
          ref={ref}
          disabled={readOnly}
          aria-disabled>
          <ListItemIcon>
            <AddIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={t('newObject')} />
        </MenuItem>

        {state.isModalOpen ? (
          <NewObjectModal
            editingContextId={editingContextId}
            item={item}
            onObjectCreated={onObjectCreated}
            onClose={onClose}
          />
        ) : null}
      </>
    );
  }
);
