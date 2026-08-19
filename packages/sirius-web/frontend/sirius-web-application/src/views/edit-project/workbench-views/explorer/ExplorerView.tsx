/*******************************************************************************
 * Copyright (c) 2019, 2025 Obeo.
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
import {
  RepresentationLoadingIndicator,
  Selection,
  SelectionEntry,
  useSelection,
  WorkbenchViewComponentProps,
  WorkbenchViewHandle,
  getTextFromStyledString,
} from '@eclipse-sirius/sirius-components-core';
import {
  FilterBar,
  GQLGetTreePathVariables,
  GQLTree,
  GQLTreeFilter,
  GQLTreeItem,
  TreeFilter,
  TreeToolBar,
  TreeToolBarContext,
  TreeToolBarContextValue,
  TreeView,
  useTreeFilters,
  useTreePath,
  useTreeSelection,
} from '@eclipse-sirius/sirius-components-trees';
import { Theme } from '@mui/material/styles';
import { ForwardedRef, forwardRef, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';
import { DuplicateObjectKeyboardShortcut } from './context-menu-contributions/duplicate-object/DuplicateObjectKeyboardShortcut';
import { ExplorerViewConfiguration, ExplorerViewState } from './ExplorerView.types';
import { TreeDescriptionsMenu } from './TreeDescriptionsMenu';
import { useExplorerDescriptions } from './useExplorerDescriptions';
import { useExplorerSubscription } from './useExplorerSubscription';
import { GQLTreeEventPayload, GQLTreeRefreshedEventPayload } from './useExplorerSubscription.types';
import { useExplorerViewHandle } from './useExplorerViewHandle';
import { useCurrentProject } from '../../useCurrentProject';
import { useProjectSubscription } from '../../navbar/useProjectSubscription';
import { useTreePathContext } from '../TreePathContext';
import { TreePathEntry } from '../TreePathContext.types';

const useStyles = makeStyles()((theme: Theme) => ({
  treeView: {
    display: 'grid',
    gridTemplateColumns: 'auto',
    gridTemplateRows: 'auto auto 1fr',
    justifyItems: 'stretch',
    overflow: 'auto',
  },
  treeFilter: {
    paddingTop: theme.spacing(1),
  },
  treeContent: {
    overflow: 'auto',
  },
}));

import { useTranslation } from 'react-i18next';

const isTreeRefreshedEventPayload = (payload: GQLTreeEventPayload): payload is GQLTreeRefreshedEventPayload =>
  payload && payload.__typename === 'TreeRefreshedEventPayload';

export const ExplorerView = forwardRef<WorkbenchViewHandle, WorkbenchViewComponentProps>(
  (
    { editingContextId, id, initialConfiguration, readOnly }: WorkbenchViewComponentProps,
    ref: ForwardedRef<WorkbenchViewHandle>
  ) => {
    const { classes: styles } = useStyles();
    const { t } = useTranslation('sirius-web-application');

    const initialExplorerViewConfiguration: ExplorerViewConfiguration =
      initialConfiguration as unknown as ExplorerViewConfiguration;
    const [state, setState] = useState<ExplorerViewState>({
      filterBar: false,
      filterBarText: '',
      filterBarTreeFiltering: false,
      treeFilters: initialExplorerViewConfiguration?.activeTreeFilters ?? [],
      activeTreeDescriptionId: initialExplorerViewConfiguration?.activeTreeDescriptionId ?? null,
      expanded: {},
      maxDepth: {},
      tree: null,
      selectedTreeItemIds: [],
      singleTreeItemSelected: null,
      selectionPivotTreeItemId: null,
    });

    // If we are requested to reveal the global selection, we need to compute the tree path to expand
    const { getTreePath, data: treePathData } = useTreePath();

    const applySelection = (selection: Selection) => {
      const newSelectedTreeItemIds = selection.entries.map((entry) => entry.id);
      setState((prevState) => ({
        ...prevState,
        selectedTreeItemIds: newSelectedTreeItemIds,
      }));

      if (state.tree && newSelectedTreeItemIds.length > 0) {
        const variables: GQLGetTreePathVariables = {
          editingContextId,
          treeId: state.tree.id,
          selectionEntryIds: newSelectedTreeItemIds,
        };
        getTreePath({ variables });
      }
    };

    useExplorerViewHandle(id, state.tree?.id, state.treeFilters, state.activeTreeDescriptionId, applySelection, ref);

    const { setTreePathEntry } = useTreePathContext();

    /**
     * Traverse the tree to find the ancestor labels for a given tree item.
     * Returns the labels from root to the item's parent (excludes the item itself).
     */
    const findAncestorPathInTree = useCallback(
      (tree: GQLTree | null, targetItemId: string): TreePathEntry | null => {
        if (!tree) return null;
        const search = (items: GQLTreeItem[], path: string[]): TreePathEntry | null => {
          for (const item of items) {
            if (item.id === targetItemId) {
              return {
                ancestorLabels: path,
                hasChildren: item.hasChildren,
              };
            }
            if (item.children && item.children.length > 0) {
              const result = search(item.children, [...path, getTextFromStyledString(item.label)]);
              if (result) return result;
            }
          }
          return null;
        };
        return search(tree.children, []);
      },
      []
    );

    // Update the TreePathContext whenever the tree data or selection changes
    useEffect(() => {
      if (state.tree && state.selectedTreeItemIds.length > 0) {
        // Build paths for all selected items
        for (const selectedId of state.selectedTreeItemIds) {
          const pathEntry = findAncestorPathInTree(state.tree, selectedId);
          if (pathEntry) {
            setTreePathEntry(selectedId, pathEntry);
          }
        }
      }
    }, [state.tree, state.selectedTreeItemIds, findAncestorPathInTree, setTreePathEntry]);

    const { project } = useCurrentProject();
    const { payload: projectPayload } = useProjectSubscription(project.id);

    const treeToolBarContributionComponents = useContext<TreeToolBarContextValue>(TreeToolBarContext).map(
      (contribution) => contribution.props.component
    );
    const activeTreeFilterIds = state.treeFilters.filter((filter) => filter.state).map((filter) => filter.id);

    const { payload } = useExplorerSubscription(
      editingContextId,
      state.activeTreeDescriptionId,
      activeTreeFilterIds,
      state.expanded[state.activeTreeDescriptionId] ?? [],
      state.maxDepth[state.activeTreeDescriptionId] ?? 1
    );

    // I18N-managed labels: only virtual folder keys (edt.tree.*) and top-level
    // structural items (step labels, Steps root) are translated. All other tree
    // item labels (EMF model elements, user-named objects) stay in English.
    const shouldTranslate = (text: string): boolean => {
      if (!text) return false;
      // Virtual folder names (e.g. edt.tree.services, edt.tree.moduleTypes)
      if (text.startsWith('edt.tree.')) return true;
      // Step labels: 0-Types, 1-Services, 2-ComponentDefinitions, 3-InitialAssembly,
      // 4-ComponentImplementations, 5-Integration
      if (/^[0-5]-[A-Z]/.test(text)) return true;
      // Root Steps node label
      if (text === 'Steps' || text === 'Integration') return true;
      return false;
    };

    const translateTreeItem = (item: GQLTreeItem): GQLTreeItem => {
      let translatedLabel = item.label;
      const labelAny = item.label as any;

      // GQLStyledString has styledStringFragments: [{text, styledStringFragmentStyle}]
      if (labelAny && Array.isArray(labelAny.styledStringFragments)) {
        const translatedFragments = labelAny.styledStringFragments.map((fragment: any) => {
          const text = fragment.text;
          if (typeof text === 'string' && shouldTranslate(text)) {
            const translated = t(text);
            // Only use translation if it differs from the key (meaning a translation exists)
            if (translated !== text) {
              return { ...fragment, text: translated };
            }
          }
          return fragment;
        });
        translatedLabel = { ...labelAny, styledStringFragments: translatedFragments } as any;
      }
      // Fallback: check if it's a simple string
      else if (typeof labelAny === 'string') {
        const key = labelAny;
        if (key && shouldTranslate(key)) {
          const translated = t(key);
          if (translated !== key) {
            translatedLabel = translated as any;
          }
        }
      }

      const translatedChildren = item.children?.map(translateTreeItem);
      return {
        ...item,
        label: translatedLabel,
        children: translatedChildren,
      };
    };

    const translateTree = (tree: GQLTree): GQLTree => {
      if (!tree) return tree;
      const treeAny = tree as any;

      // Handle 'children' (standard GQLTree type)
      if (Array.isArray(treeAny.children)) {
        return {
          ...tree,
          children: treeAny.children.map(translateTreeItem),
        } as any;
      }

      // Handle 'roots' (plural)
      if (Array.isArray(treeAny.roots)) {
        return {
          ...tree,
          roots: treeAny.roots.map(translateTreeItem),
        } as any;
      }

      // Handle 'root' (singular)
      if (treeAny.root) {
        return {
          ...tree,
          root: translateTreeItem(treeAny.root),
        } as any;
      }

      return tree;
    };

    useEffect(() => {
      if (isTreeRefreshedEventPayload(payload)) {
        const translatedTree = translateTree(payload.tree);
        setState((prevState) => ({ ...prevState, tree: translatedTree }));
      }
    }, [payload]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
      if (projectPayload && projectPayload.__typename === 'ProjectRenamedEventPayload') {
        const newName = (projectPayload as any).newName;
        setState((prevState) => {
          if (!prevState.tree) return prevState;

          const mutateRoots = (roots: any[]) => {
            if (!roots || roots.length === 0) return roots;
            const newRoots = [...roots];
            const oldLabel = newRoots[0].label;

            let newLabel: any = newName;
            if (
              oldLabel &&
              Array.isArray(oldLabel.styledStringFragments) &&
              oldLabel.styledStringFragments.length > 0
            ) {
              newLabel = {
                ...oldLabel,
                styledStringFragments: [
                  {
                    ...oldLabel.styledStringFragments[0],
                    text: newName,
                  },
                ],
              };
            }
            newRoots[0] = { ...newRoots[0], label: newLabel };
            return newRoots;
          };

          let newTree = { ...prevState.tree };
          const treeAny = newTree as any;
          if (Array.isArray(treeAny.children)) {
            newTree = { ...newTree, children: mutateRoots(treeAny.children) } as any;
          } else if (Array.isArray(treeAny.roots)) {
            newTree = { ...newTree, roots: mutateRoots(treeAny.roots) } as any;
          } else if (treeAny.root) {
            newTree = { ...newTree, root: { ...treeAny.root, label: newName } } as any;
          }

          return { ...prevState, tree: newTree };
        });
      }
    }, [projectPayload]);

    const { explorerDescriptions } = useExplorerDescriptions(editingContextId);

    useEffect(() => {
      if (explorerDescriptions && explorerDescriptions.length > 0) {
        const expandedInitiated: { [key: string]: string[] } = {};
        const maxDepthInitiated: { [key: string]: number } = {};
        explorerDescriptions.forEach((explorerDescription) => {
          expandedInitiated[explorerDescription.id] = [];
          maxDepthInitiated[explorerDescription.id] = 1;
        });

        setState((prevState) => ({
          ...prevState,
          activeTreeDescriptionId: state.activeTreeDescriptionId ?? explorerDescriptions[0].id,
          expanded: expandedInitiated,
          maxDepth: maxDepthInitiated,
        }));
      }
    }, [explorerDescriptions]);

    const { loading, treeFilters } = useTreeFilters(editingContextId, state.activeTreeDescriptionId || null);

    const prevTreeFiltersRef = useRef<GQLTreeFilter[]>([]);

    useEffect(() => {
      if (!loading) {
        // 只有当 treeFilters 内容实际变化时才更新状态，避免无限循环
        const prevFilters = prevTreeFiltersRef.current;
        const hasChanged =
          treeFilters.length !== prevFilters.length ||
          treeFilters.some(
            (filter, index) =>
              filter.id !== prevFilters[index]?.id ||
              filter.label !== prevFilters[index]?.label ||
              filter.defaultState !== prevFilters[index]?.defaultState
          );

        if (hasChanged) {
          prevTreeFiltersRef.current = treeFilters;
          const allAvailableFilters: TreeFilter[] = treeFilters.map((gqlTreeFilter) => ({
            id: gqlTreeFilter.id,
            label: gqlTreeFilter.label,
            state: gqlTreeFilter.defaultState,
          }));
          setState((prevState) => ({
            ...prevState,
            treeFilters: allAvailableFilters.map((availableFilter) => {
              const existingFilter: TreeFilter = prevState.treeFilters.find(
                (filter) => filter.id === availableFilter.id
              );
              if (existingFilter) {
                return {
                  ...availableFilter,
                  state: existingFilter.state,
                };
              } else {
                return availableFilter;
              }
            }),
          }));
        }
      }
    }, [loading, treeFilters]);

    // Auto-expand tree to show Steps children (Step0-Step5) on initial page load/refresh
    const initialExpandDoneRef = useRef<boolean>(false);

    useEffect(() => {
      if (!state.tree || initialExpandDoneRef.current) return;

      const activeExpanded = state.expanded[state.activeTreeDescriptionId] ?? [];
      const treeAny = state.tree as any;
      const roots = Array.isArray(treeAny?.children)
        ? treeAny.children
        : Array.isArray(treeAny?.roots)
        ? treeAny.roots
        : treeAny?.root
        ? [treeAny.root]
        : [];

      // Step 1: If nothing is expanded yet, expand root items that have children
      if (activeExpanded.length === 0) {
        const rootItemIds: string[] = [];
        for (const root of roots) {
          if (root.hasChildren && root.id) {
            rootItemIds.push(root.id);
          }
        }
        if (rootItemIds.length > 0) {
          setState((prevState) => ({
            ...prevState,
            expanded: {
              ...prevState.expanded,
              [prevState.activeTreeDescriptionId]: rootItemIds,
            },
            maxDepth: {
              ...prevState.maxDepth,
              [prevState.activeTreeDescriptionId]: 2,
            },
          }));
        }
        return;
      }

      // Step 2: Find the Steps node and expand it
      const findStepsItemId = (items: any[]): string | null => {
        for (const item of items) {
          if (item.kind && item.kind.includes('entity=Steps')) {
            return item.id;
          }
          if (item.children && item.children.length > 0) {
            const found = findStepsItemId(item.children);
            if (found) return found;
          }
        }
        return null;
      };

      const stepsItemId = findStepsItemId(roots);

      if (stepsItemId && !activeExpanded.includes(stepsItemId)) {
        setState((prevState) => ({
          ...prevState,
          expanded: {
            ...prevState.expanded,
            [prevState.activeTreeDescriptionId]: [...activeExpanded, stepsItemId],
          },
          maxDepth: {
            ...prevState.maxDepth,
            [prevState.activeTreeDescriptionId]: 3,
          },
        }));
        initialExpandDoneRef.current = true;
      } else if (stepsItemId && activeExpanded.includes(stepsItemId)) {
        initialExpandDoneRef.current = true;
      }
    }, [state.tree]); // eslint-disable-line react-hooks/exhaustive-deps

    const treeElement = useRef<HTMLDivElement>(null);
    useEffect(() => {
      const downHandler = (event) => {
        if (
          (event.ctrlKey === true || event.metaKey === true) &&
          event.key === 'f' &&
          event.target.tagName !== 'INPUT'
        ) {
          event.preventDefault();
          setState((prevState) => {
            return { ...prevState, filterBar: true, filterBarText: '', filterBarTreeFiltering: false };
          });
        }
      };
      const element = treeElement?.current;
      if (element) {
        element.addEventListener('keydown', downHandler);

        return () => {
          element.removeEventListener('keydown', downHandler);
        };
      }
      return null;
    }, [treeElement]);

    const { selection, setSelection } = useSelection();
    const { treeItemClick } = useTreeSelection();

    const selectionKey: string = selection?.entries
      .map((entry) => entry.id)
      .sort()
      .join(':');

    const revealSelection = useCallback(() => {
      if (state.tree && selection.entries.length > 0) {
        const variables: GQLGetTreePathVariables = {
          editingContextId,
          treeId: state.tree.id,
          selectionEntryIds: selection.entries.map((entry) => entry.id),
        };
        getTreePath({ variables });
      }
    }, [editingContextId, selectionKey, state.tree, getTreePath]);

    useEffect(() => {
      if (treePathData && treePathData.viewer?.editingContext?.treePath) {
        setState((prevState) => {
          const { expanded, maxDepth } = prevState;
          const { treeItemIdsToExpand, maxDepth: expandedMaxDepth } = treePathData.viewer.editingContext.treePath;
          const newExpanded: string[] = [...expanded[prevState.activeTreeDescriptionId]];

          treeItemIdsToExpand?.forEach((itemToExpand) => {
            if (!expanded[prevState.activeTreeDescriptionId].includes(itemToExpand)) {
              newExpanded.push(itemToExpand);
            }
          });
          return {
            ...prevState,
            selectedTreeItemIds: selection.entries.map((entry) => entry.id),
            expanded: {
              ...prevState.expanded,
              [prevState.activeTreeDescriptionId]: newExpanded,
            },
            maxDepth: {
              ...prevState.maxDepth,
              [prevState.activeTreeDescriptionId]: Math.max(
                expandedMaxDepth,
                maxDepth[prevState.activeTreeDescriptionId]
              ),
            },
          };
        });
      }
    }, [treePathData]);

    const onExpandedElementChange = (newExpandedIds: string[], newMaxDepth: number) => {
      setState((prevState) => ({
        ...prevState,
        expanded: {
          ...prevState.expanded,
          [prevState.activeTreeDescriptionId]: newExpandedIds,
        },
        maxDepth: {
          ...prevState.maxDepth,
          [prevState.activeTreeDescriptionId]: Math.max(
            newMaxDepth,
            prevState.maxDepth[prevState.activeTreeDescriptionId]
          ),
        },
      }));
    };

    let filterBar: JSX.Element = <div />;
    if (state.filterBar) {
      filterBar = (
        <div className={styles.treeFilter}>
          <FilterBar
            onTextChange={(event) => {
              const {
                target: { value },
              } = event;
              setState((prevState) => {
                return { ...prevState, filterBarText: value };
              });
            }}
            onFilterButtonClick={(enabled) =>
              setState((prevState) => ({
                ...prevState,
                filterBarTreeFiltering: enabled,
              }))
            }
            onClose={() =>
              setState((prevState) => {
                return { ...prevState, filterBar: false, filterBarText: '', filterBarTreeFiltering: false };
              })
            }
          />
        </div>
      );
    }

    const onTreeItemClick = (event: React.MouseEvent<HTMLDivElement, MouseEvent>, tree: GQLTree, item: GQLTreeItem) => {
      var localSelection = treeItemClick(event, tree, item, state.selectedTreeItemIds, true);
      setState((prevState) => ({
        ...prevState,
        selectedTreeItemIds: localSelection.selectedTreeItemIds,
        singleTreeItemSelected: localSelection.singleTreeItemSelected,
      }));
      var globalSelection = treeItemClick(
        event,
        state.tree,
        item,
        selection.entries.map((entry) => entry.id),
        true
      );
      setSelection({ entries: globalSelection.selectedTreeItemIds.map<SelectionEntry>((id) => ({ id })) });
    };

    const treeDescriptionSelector: JSX.Element = explorerDescriptions.length > 1 && (
      <TreeDescriptionsMenu
        treeDescriptions={explorerDescriptions}
        activeTreeDescriptionId={state.activeTreeDescriptionId}
        onTreeDescriptionChange={(treeDescription) =>
          setState((prevState) => ({
            ...prevState,
            activeTreeDescriptionId: treeDescription.id,
            tree: null,
          }))
        }
      />
    );

    if (!state.tree || loading) {
      return (
        <div className={styles.treeView} ref={treeElement}>
          <RepresentationLoadingIndicator />
        </div>
      );
    }

    return (
      <div className={styles.treeView} ref={treeElement}>
        <TreeToolBar
          editingContextId={editingContextId}
          readOnly={readOnly}
          treeFilters={state.treeFilters}
          onRevealSelection={revealSelection}
          onTreeFilterMenuItemClick={(treeFilters) =>
            setState((prevState) => {
              return { ...prevState, treeFilters };
            })
          }
          onFilter={() => {
            setState((prevState) => {
              return !prevState.filterBar
                ? { ...prevState, filterBar: true, filterBarText: '', filterBarTreeFiltering: false }
                : { ...prevState, filterBar: false, filterBarText: '', filterBarTreeFiltering: false };
            });
          }}
          treeToolBarContributionComponents={treeToolBarContributionComponents}>
          {treeDescriptionSelector}
        </TreeToolBar>
        <DuplicateObjectKeyboardShortcut
          editingContextId={editingContextId}
          readOnly={readOnly}
          selectedTreeItem={state.singleTreeItemSelected}
          selectTreeItems={(selectedTreeItemIds: string[]) =>
            setState((prevState) => {
              return { ...prevState, selectedTreeItemIds };
            })
          }>
          {filterBar}
          <div className={styles.treeContent}>
            <TreeView
              editingContextId={editingContextId}
              readOnly={readOnly}
              tree={state.tree}
              textToHighlight={state.filterBarText}
              textToFilter={state.filterBarTreeFiltering ? state.filterBarText : null}
              onExpandedElementChange={onExpandedElementChange}
              expanded={state.expanded[state.activeTreeDescriptionId]}
              maxDepth={state.maxDepth[state.activeTreeDescriptionId]}
              onTreeItemClick={onTreeItemClick}
              selectTreeItems={(selectedTreeItemIds: string[]) =>
                setState((prevState) => {
                  return { ...prevState, selectedTreeItemIds };
                })
              }
              selectedTreeItemIds={state.selectedTreeItemIds}
              data-testid="explorer://"
            />
          </div>
        </DuplicateObjectKeyboardShortcut>
      </div>
    );
  }
);
