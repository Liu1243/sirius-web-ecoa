import { ServerContext, ServerContextValue } from '@eclipse-sirius/sirius-components-core';
import { useStore } from '@xyflow/react';
import { CSSProperties, MouseEvent, useContext } from 'react';
import { FreeFormNodeData } from '../node/FreeFormNode.types';

type MiniMapNodeProps = {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  style?: CSSProperties;
  color?: string;
  strokeColor?: string;
  strokeWidth?: number;
  borderRadius?: number;
  className?: string;
  shapeRendering?: string;
  selected?: boolean;
  onClick?: (event: MouseEvent<Element, globalThis.MouseEvent>, id: string) => void;
};

export const MiniMapNode = ({
  id,
  x,
  y,
  width,
  height,
  style,
  color,
  strokeColor,
  strokeWidth,
  borderRadius,
  className,
  shapeRendering,
  selected,
  onClick,
}: MiniMapNodeProps) => {
  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);
  const imageURL = useStore((state) => {
    const node = state.nodeLookup.get(id)?.internals.userNode;
    return (node?.data as FreeFormNodeData | undefined)?.imageURL ?? null;
  });

  const resolvedImageURL = imageURL ? httpOrigin + imageURL : null;
  const fillColor =
    typeof color === 'string'
      ? color
      : typeof style?.backgroundColor === 'string'
      ? style.backgroundColor
      : typeof style?.background === 'string'
      ? style.background
      : undefined;

  if (resolvedImageURL) {
    return (
      <g className={className} onClick={onClick ? (event) => onClick(event, id) : undefined}>
        <image
          x={x}
          y={y}
          width={width}
          height={height}
          href={resolvedImageURL}
          preserveAspectRatio="none"
          opacity={style?.opacity ? String(style.opacity) : undefined}
        />
        <rect
          className={selected ? 'selected' : undefined}
          x={x}
          y={y}
          rx={borderRadius}
          ry={borderRadius}
          width={width}
          height={height}
          style={{
            fill: 'transparent',
            stroke: strokeColor,
            strokeWidth,
          }}
          shapeRendering={shapeRendering}
        />
      </g>
    );
  }

  return (
    <rect
      className={className}
      x={x}
      y={y}
      rx={borderRadius}
      ry={borderRadius}
      width={width}
      height={height}
      style={{
        fill: fillColor,
        stroke: strokeColor,
        strokeWidth,
      }}
      shapeRendering={shapeRendering}
      onClick={onClick ? (event) => onClick(event, id) : undefined}
    />
  );
};
