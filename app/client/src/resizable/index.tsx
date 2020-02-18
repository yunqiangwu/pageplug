import React, { ReactNode, useState, useEffect } from "react";
import styled, { FlattenSimpleInterpolation } from "styled-components";
import { useDrag } from "react-use-gesture";
import { Spring } from "react-spring/renderprops";

const ResizeWrapper = styled.div`
  position: absolute;
  display: block;
`;

const getSnappedValues = (
  x: number,
  y: number,
  snapGrid: { x: number; y: number },
) => {
  return {
    x: Math.round(x / snapGrid.x) * snapGrid.x,
    y: Math.round(y / snapGrid.y) * snapGrid.y,
  };
};

type ResizableHandleProps = {
  dragCallback: (x: number, y: number) => void;
  component: FlattenSimpleInterpolation;
  onStart: Function;
  onStop: Function;
  snapGrid: {
    x: number;
    y: number;
  };
};

const ResizableHandle = (props: ResizableHandleProps) => {
  const bind = useDrag(
    ({ first, last, dragging, movement: [mx, my], memo }) => {
      const snapped = getSnappedValues(mx, my, props.snapGrid);
      if (dragging && memo && (snapped.x !== memo.x || snapped.y !== memo.y)) {
        props.dragCallback(snapped.x, snapped.y);
      }
      if (first) {
        props.onStart();
      }
      if (last) {
        props.onStop();
      }
      return snapped;
    },
  );
  const HandleComponent = styled.div`
    ${props.component}
  `;
  return <HandleComponent {...bind()} />;
};

type ResizableProps = {
  handles: {
    left: FlattenSimpleInterpolation;
    top: FlattenSimpleInterpolation;
    bottom: FlattenSimpleInterpolation;
    right: FlattenSimpleInterpolation;
    bottomRight: FlattenSimpleInterpolation;
  };
  componentWidth: number;
  componentHeight: number;
  children: ReactNode;
  onStart: Function;
  onStop: Function;
  snapGrid: { x: number; y: number };
  enable: boolean;
  isColliding: Function;
};

export const Resizable = (props: ResizableProps) => {
  const [newDimensions, set] = useState({
    width: props.componentWidth,
    height: props.componentHeight,
    x: 0,
    y: 0,
  });

  const setNewDimensions = (rect: {
    width: number;
    height: number;
    x: number;
    y: number;
  }) => {
    const { width, height, x, y } = rect;
    const isColliding = props.isColliding({ width, height }, { x, y });
    if (!isColliding) {
      set(rect);
    }
  };

  useEffect(() => {
    set({
      width: props.componentWidth,
      height: props.componentHeight,
      x: 0,
      y: 0,
    });
  }, [props.componentHeight, props.componentWidth]);

  const handles = [
    {
      dragCallback: (x: number) => {
        setNewDimensions({
          width: props.componentWidth - x,
          height: newDimensions.height,
          x,
          y: newDimensions.y,
        });
      },
      component: props.handles.left,
    },
    {
      dragCallback: (x: number) => {
        setNewDimensions({
          width: props.componentWidth + x,
          height: newDimensions.height,
          x: newDimensions.x,
          y: newDimensions.y,
        });
      },
      component: props.handles.right,
    },
    {
      dragCallback: (x: number, y: number) => {
        setNewDimensions({
          width: newDimensions.width,
          height: props.componentHeight - y,
          y: y,
          x: newDimensions.x,
        });
      },
      component: props.handles.top,
    },
    {
      dragCallback: (x: number, y: number) => {
        setNewDimensions({
          width: newDimensions.width,
          height: props.componentHeight + y,
          x: newDimensions.x,
          y: newDimensions.y,
        });
      },
      component: props.handles.bottom,
    },
    {
      dragCallback: (x: number, y: number) => {
        setNewDimensions({
          width: props.componentWidth + x,
          height: props.componentHeight + y,
          x: newDimensions.x,
          y: newDimensions.y,
        });
      },
      component: props.handles.bottomRight,
    },
  ];

  const onResizeStop = () => {
    props.onStop(
      {
        width: newDimensions.width,
        height: newDimensions.height,
      },
      {
        x: newDimensions.x,
        y: newDimensions.y,
      },
    );
  };

  const renderHandles = handles.map((handle, index) => (
    <ResizableHandle
      {...handle}
      key={index}
      onStart={props.onStart}
      onStop={onResizeStop}
      snapGrid={props.snapGrid}
    />
  ));

  return (
    <Spring
      from={{
        width: props.componentWidth,
        height: props.componentHeight,
      }}
      to={{
        width: newDimensions.width,
        height: newDimensions.height,
        transform: `translate3d(${newDimensions.x}px,${newDimensions.y}px,0)`,
      }}
      config={{
        clamp: true,
        friction: 0,
        tension: 999,
      }}
    >
      {_props => (
        <ResizeWrapper style={_props}>
          {props.children}
          {props.enable && renderHandles}
        </ResizeWrapper>
      )}
    </Spring>
  );
};

export default Resizable;
