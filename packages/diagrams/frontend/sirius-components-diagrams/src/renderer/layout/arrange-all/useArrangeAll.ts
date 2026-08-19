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
import { useMultiToast } from '@eclipse-sirius/sirius-components-core';
import { Edge, Node, useReactFlow, useStoreApi } from '@xyflow/react';
import { LayoutOptions } from 'elkjs/lib/elk-api';
import ELK, { ElkLabel, ElkNode } from 'elkjs/lib/elk.bundled';
import { EdgeData, NodeData } from '../../DiagramRenderer.types';
import { useFitView } from '../../fit-to-screen/useFitView';
import { ListNodeData } from '../../node/ListNode.types';
import { DiagramNodeType } from '../../node/NodeTypes.types';
import { useOverlap } from '../../overlap/useOverlap';
import { RawDiagram } from '../layout.types';
import { labelHorizontalPadding, labelVerticalPadding } from '../layoutParams';
import { useLayout } from '../useLayout';
import { useSynchronizeLayoutData } from '../useSynchronizeLayoutData';
import { UseArrangeAllValue } from './useArrangeAll.types';

const isListData = (node: Node): node is Node<ListNodeData> => node.type === 'listNode';

function reverseOrdreMap<K, V>(map: Map<K, V>): Map<K, V> {
  const reversedNodes = Array.from(map.entries()).reverse();
  return new Map<K, V>(reversedNodes);
}

const getSubNodes = (nodes: Node<NodeData, string>[]): Map<string, Node<NodeData, string>[]> => {
  const subNodes: Map<string, Node<NodeData, string>[]> = new Map<string, Node<NodeData, string>[]>();
  for (const node of nodes.filter((n) => !n.hidden)) {
    const parentNodeId: string = node.parentId ?? 'root';
    if (!subNodes.has(parentNodeId)) {
      subNodes.set(parentNodeId, []);
    }
    subNodes.get(parentNodeId)?.push(node);
  }
  return subNodes;
};

const computeHeaderVerticalFootprint = (
  node,
  viewportZoom: number,
  reactFlowWrapper: React.MutableRefObject<HTMLDivElement | null>
): number => {
  if (node && node.data.insideLabel?.isHeader) {
    const label = reactFlowWrapper?.current?.querySelector<HTMLDivElement>(
      `[data-id="${node.data.insideLabel.id}-content"]`
    );
    if (label) {
      return label.getBoundingClientRect().height / viewportZoom + labelVerticalPadding * 2;
    }
  }
  return 0;
};

const computeLabels = (
  node,
  viewportZoom: number,
  reactFlowWrapper: React.MutableRefObject<HTMLDivElement | null>
): ElkLabel[] => {
  const labels: ElkLabel[] = [];
  if (node && node.data.insideLabel) {
    const label = reactFlowWrapper?.current?.querySelector<HTMLDivElement>(
      `[data-id="${node.data.insideLabel.id}-content"]`
    );
    if (label) {
      const elkLabel: ElkLabel = {
        id: node.data.insideLabel.id,
        width: label.getBoundingClientRect().width / viewportZoom + labelHorizontalPadding * 2,
        height: label.getBoundingClientRect().height / viewportZoom + labelVerticalPadding * 2,
        text: node.data.insideLabel.text,
        x: 0,
        y: 0,
      };
      labels.push(elkLabel);
    }
  }
  if (node && node.data.outsideLabels.BOTTOM_MIDDLE) {
    const label = reactFlowWrapper?.current?.querySelector<HTMLDivElement>(
      `[data-id="${node.data.outsideLabels.BOTTOM_MIDDLE.id}-content"]`
    );
    if (label) {
      const elkLabel: ElkLabel = {
        id: node.data.outsideLabels.BOTTOM_MIDDLE.id,
        width: label.getBoundingClientRect().width / viewportZoom + labelHorizontalPadding * 2,
        height: label.getBoundingClientRect().height / viewportZoom + labelVerticalPadding * 2,
        text: node.data.outsideLabels.BOTTOM_MIDDLE.text,
        x: 0,
        y: node.height,
      };
      labels.push(elkLabel);
    }
  }
  return labels;
};

export const useArrangeAll = (reactFlowWrapper: React.MutableRefObject<HTMLDivElement | null>): UseArrangeAllValue => {
  const { getNodes, getEdges, setNodes, setEdges } = useReactFlow<Node<NodeData>, Edge<EdgeData>>();
  const store = useStoreApi<Node<NodeData>, Edge<EdgeData>>();
  const { layout } = useLayout();
  const { synchronizeLayoutData } = useSynchronizeLayoutData();
  const { resolveNodeOverlap } = useOverlap();
  const { addErrorMessage } = useMultiToast();
  const { fitView } = useFitView();

  const elk = new ELK();

  const getELKLayout = async (
    nodes,
    edges,
    options: LayoutOptions = {},
    parentNodeId: string,
    headerVerticalFootprint: number,
    allNodes?: Node<NodeData, string>[]
  ): Promise<any> => {
    const zoom = store.getState().transform[2];
    const graph: ElkNode = {
      id: parentNodeId,
      layoutOptions: options,
      children: nodes.map((node) => {
        const borderNodes = allNodes?.filter((n) => n.parentId === node.id && n.data.isBorderNode) ?? [];
        const ports = borderNodes.map((bn) => ({
          id: bn.id,
          width: bn.width,
          height: bn.height,
          properties: {
            'elk.port.side': bn.data.borderNodePosition,
          },
        }));
        let extraWidth = 0;
        let extraHeight = 0;
        if (isListData(node)) {
          // For listNode itself: measure its label children's widths
          const innerNodes = allNodes?.filter((n) => n.parentId === node.id && !n.data.isBorderNode) ?? [];
          innerNodes.forEach((innerNode) => {
            const innerLabel = reactFlowWrapper?.current?.querySelector<HTMLDivElement>(
              `[data-id="${innerNode.data?.insideLabel?.id}-content"]`
            );
            if (innerLabel) {
              const labelW = innerLabel.getBoundingClientRect().width / zoom + labelHorizontalPadding * 2;
              const labelH = innerLabel.getBoundingClientRect().height / zoom + labelVerticalPadding * 2;
              if (labelW > extraWidth) extraWidth = labelW;
              extraHeight += labelH;
            }
          });
        } else {
          // For freeFormNode containers: measure their listNode children's actual rendered size
          const childListNodes = allNodes?.filter((n) => n.parentId === node.id && isListData(n)) ?? [];
          let totalChildHeight = 0;
          let maxChildWidth = 0;
          childListNodes.forEach((listNode) => {
            // Use current rendered height if available, otherwise estimate from inner labels
            const listNodeInnerNodes =
              allNodes?.filter((n) => n.parentId === listNode.id && !n.data.isBorderNode) ?? [];
            let listNodeH = 0;
            let listNodeW = 0;
            listNodeInnerNodes.forEach((innerNode) => {
              const innerLabel = reactFlowWrapper?.current?.querySelector<HTMLDivElement>(
                `[data-id="${innerNode.data?.insideLabel?.id}-content"]`
              );
              if (innerLabel) {
                const labelW = innerLabel.getBoundingClientRect().width / zoom + labelHorizontalPadding * 2;
                const labelH = innerLabel.getBoundingClientRect().height / zoom + labelVerticalPadding * 2;
                if (labelW > listNodeW) listNodeW = labelW;
                listNodeH += labelH;
              }
            });
            // Use the measured height or current node height, whichever is bigger
            totalChildHeight += Math.max(listNodeH, listNode.height ?? 0);
            if (listNodeW > maxChildWidth) maxChildWidth = listNodeW;
          });
          const nonListNodeChildrenHeight =
            allNodes
              ?.filter((n) => n.parentId === node.id && !n.data.isBorderNode && !isListData(n))
              .reduce((sum, n) => sum + (n.height ?? 0), 0) ?? 0;
          extraHeight = totalChildHeight + nonListNodeChildrenHeight;
          extraWidth = maxChildWidth;
        }

        return {
          labels: computeLabels(node, zoom, reactFlowWrapper),
          ports: ports.length > 0 ? ports : undefined,
          ...node,
          width: node.width ? Math.max(node.width, extraWidth) : extraWidth,
          height: node.height ? Math.max(node.height, extraHeight) : extraHeight,
        };
      }),
      edges: edges
        .filter((edge) => {
          const isSourceInNodeOrPort =
            nodes.some((n) => n.id === edge.source) ||
            allNodes?.some(
              (bn) => bn.id === edge.source && bn.data.isBorderNode && nodes.some((n) => n.id === bn.parentId)
            );
          const isTargetInNodeOrPort =
            nodes.some((n) => n.id === edge.target) ||
            allNodes?.some(
              (bn) => bn.id === edge.target && bn.data.isBorderNode && nodes.some((n) => n.id === bn.parentId)
            );
          return isSourceInNodeOrPort && isTargetInNodeOrPort;
        })
        .map((edge) => ({
          ...edge,
          sources: [edge.source],
          targets: [edge.target],
        })),
    };
    try {
      const layoutedGraph = await elk.layout(graph);
      return {
        nodes:
          layoutedGraph?.children?.map((node) => {
            const originalNode = nodes.find((node_1) => node_1.id === node.id);
            if (originalNode && originalNode.data.pinned) {
              return {
                ...originalNode,
                ...node,
                parentId: originalNode.parentId,
                type: originalNode.type,
                data: originalNode.data,
              };
            } else {
              return {
                ...originalNode,
                ...node,
                parentId: originalNode?.parentId,
                type: originalNode?.type,
                data: originalNode?.data,
                position: { x: node.x ?? 0, y: (node.y ?? 0) + headerVerticalFootprint },
                width: node.width,
                height: node.height,
              };
            }
          }) ?? [],
        layoutReturn: layoutedGraph,
      };
    } catch (message) {
      addErrorMessage('An error occurred during the arrange all elements ');
      return [];
    }
  };

  const applyElkOnSubNodes = async (
    subNodes: Map<string, Node<NodeData, string>[]>,
    allNodes: Node<NodeData, string>[],
    allEdges: Edge<EdgeData>[],
    layoutOptions: LayoutOptions
  ): Promise<{ nodes: Node<NodeData, string>[]; edges: Edge<EdgeData>[] }> => {
    let layoutedAllNodes: Node<NodeData, string>[] = [];
    const parentNodeWithNewSize: Node<NodeData>[] = [];
    // Copy edges to modify them
    let outEdges: Edge<EdgeData>[] = allEdges.map((e) => ({ ...e }));

    for (const [parentNodeId, nodes] of subNodes) {
      const parentNode = allNodes.find((node) => node.id === parentNodeId);
      const subGroupEdges: Edge<EdgeData>[] = [];
      const edgeCloneForElk = outEdges.map((e) => ({ ...e })); // We need a fresh clone for ELK iteration
      edgeCloneForElk.forEach((edge) => {
        const isTargetInside =
          nodes.some((node) => node.id === edge.target) ||
          allNodes.some(
            (bn) => bn.id === edge.target && bn.data.isBorderNode && nodes.some((n) => n.id === bn.parentId)
          );
        const isSourceInside =
          nodes.some((node) => node.id === edge.source) ||
          allNodes.some(
            (bn) => bn.id === edge.source && bn.data.isBorderNode && nodes.some((n) => n.id === bn.parentId)
          );
        if (isTargetInside && isSourceInside) {
          subGroupEdges.push(edge);
        }
        if (isTargetInside && !isSourceInside) {
          edge.target = parentNodeId;
        }
        if (!isTargetInside && isSourceInside) {
          edge.source = parentNodeId;
        }
      });
      if ((parentNode && isListData(parentNode)) || nodes.every((node) => node.data.isBorderNode)) {
        // No elk layout for child of container list or for border node
        layoutedAllNodes = [...layoutedAllNodes, ...nodes.reverse()];
        continue;
      }
      const zoom = store.getState().transform[2];
      const headerVerticalFootprint: number = computeHeaderVerticalFootprint(parentNode, zoom, reactFlowWrapper);
      const subGroupNodes: Node<NodeData>[] = nodes
        .filter((node) => !node.data.isBorderNode)
        .map((node) => {
          return parentNodeWithNewSize.find((layoutNode) => layoutNode.id === node.id) ?? node;
        });
      await getELKLayout(
        subGroupNodes,
        subGroupEdges,
        layoutOptions,
        parentNodeId,
        headerVerticalFootprint,
        allNodes
      ).then(({ nodes: layoutedSubNodes, layoutReturn }) => {
        const parentNode = allNodes.find((node) => node.id === parentNodeId);
        if (layoutReturn) {
          if (parentNode) {
            parentNode.width = layoutReturn.width;
            parentNode.height = layoutReturn.height + headerVerticalFootprint;
            parentNode.data = { ...parentNode.data, resizedByUser: true };
            parentNode.style = { width: `${parentNode.width}px`, height: `${parentNode.height}px` };
            parentNodeWithNewSize.push(parentNode);
          }
          const updatedBorderNodes = nodes
            .filter((node) => node.data.isBorderNode)
            .map((bn) => {
              let yPos = bn.position.y;
              layoutReturn.children?.forEach((elkNode) => {
                elkNode.ports?.forEach((elkPort) => {
                  if (elkPort.id === bn.id) {
                    yPos = (elkPort.y ?? 0) + headerVerticalFootprint;
                  }
                });
              });
              return {
                ...bn,
                position: { ...bn.position, y: yPos },
                data: { ...bn.data, resizedByUser: true },
              };
            });
          layoutedAllNodes = [...layoutedAllNodes, ...layoutedSubNodes, ...updatedBorderNodes];

          // Extract edge bend points from ELK layout and apply them to outEdges
          if (layoutReturn.edges) {
            // Accumulate the absolute position by walking up all ancestor nodes
            let rootX = 0;
            let rootY = 0;
            let ancestorNode = parentNode;
            while (ancestorNode) {
              rootX += ancestorNode.position?.x ?? 0;
              rootY += ancestorNode.position?.y ?? 0;
              const ancestorParentId = ancestorNode.parentId;
              ancestorNode = ancestorParentId ? allNodes.find((n) => n.id === ancestorParentId) : undefined;
            }
            layoutReturn.edges.forEach((elkEdge) => {
              const targetEdgeIndex = outEdges.findIndex((e) => e.id === elkEdge.id);
              if (targetEdgeIndex !== -1) {
                const edge = outEdges[targetEdgeIndex];
                if (elkEdge.sections && elkEdge.sections.length > 0 && edge && edge.data) {
                  const elkPoints = elkEdge.sections[0].bendPoints;
                  if (elkPoints && elkPoints.length > 0) {
                    const newBendingPoints = elkPoints.map((pt) => ({
                      x: pt.x + rootX,
                      y: pt.y + rootY + headerVerticalFootprint,
                    }));
                    outEdges[targetEdgeIndex] = {
                      ...edge,
                      data: {
                        ...edge.data,
                        bendingPoints: newBendingPoints,
                      },
                    };
                  }
                }
              }
            });
          }
        } else {
          layoutedAllNodes = nodes;
        }
      });
    }
    return { nodes: layoutedAllNodes, edges: outEdges };
  };

  const arrangeAll = async (layoutOptions: LayoutOptions): Promise<void> => {
    const nodes: Node<NodeData, string>[] = [...getNodes()] as Node<NodeData, DiagramNodeType>[];
    const initialEdges = getEdges();
    const subNodes: Map<string, Node<NodeData, string>[]> = reverseOrdreMap(getSubNodes(nodes));

    await applyElkOnSubNodes(subNodes, nodes, initialEdges, layoutOptions).then(async (res) => {
      const laidOutNodesWithElk: Node<NodeData, string>[] = res.nodes.reverse();
      laidOutNodesWithElk.filter((laidOutNode) => {
        const parentNode = nodes.find((node) => node.id === laidOutNode.parentId);
        return !parentNode || !isListData(parentNode);
      });

      const diagramToLayout: RawDiagram = {
        nodes: laidOutNodesWithElk,
        edges: res.edges,
      };
      const layoutPromise = new Promise<void>((resolve) => {
        layout(diagramToLayout, diagramToLayout, null, 'UNDEFINED', (laidOutDiagram) => {
          const overlapFreeLaidOutNodes: Node<NodeData, string>[] = resolveNodeOverlap(
            laidOutDiagram.nodes.filter((n) => !n.data.isBorderNode),
            'horizontal'
          ) as Node<NodeData, DiagramNodeType>[];
          const mappedNodes = laidOutNodesWithElk.map((node) => {
            const existingNode = overlapFreeLaidOutNodes.find((laidOutNode) => laidOutNode.id === node.id);
            if (existingNode) {
              return {
                ...node,
                position: existingNode.position,
                width: existingNode.width ?? node.width,
                height: existingNode.height ?? node.height,
                style: {
                  ...node.style,
                  width: `${existingNode.width ?? node.width}px`,
                  height: `${existingNode.height ?? node.height}px`,
                },
              };
            }
            return {
              ...node,
              style: {
                ...node.style,
                width: `${node.width}px`,
                height: `${node.height}px`,
              },
            };
          });
          setNodes(mappedNodes);
          setEdges(laidOutDiagram.edges);
          const finalDiagram: RawDiagram = {
            nodes: mappedNodes,
            edges: laidOutDiagram.edges,
          };
          fitView({ duration: 200, nodes: mappedNodes });
          synchronizeLayoutData(crypto.randomUUID(), 'layout', finalDiagram);
          resolve();
        });
      });
      await layoutPromise;
    });
  };

  return {
    arrangeAll,
  };
};
