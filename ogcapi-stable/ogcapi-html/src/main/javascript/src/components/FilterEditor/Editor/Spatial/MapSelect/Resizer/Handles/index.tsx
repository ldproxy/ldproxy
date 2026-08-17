import React, { useState, useEffect } from "react";
import type { Map as MaplibreMap, MapMouseEvent } from "maplibre-gl";

import { Direction } from "../constants";
import type { Movement } from "../calc";

export interface HandlesProps {
  map: MaplibreMap;
  onResize: (direction: Direction, movement: Movement) => void;
}

const Handles = ({ onResize, map }: HandlesProps) => {
  const [direction, setDirection] = useState<Direction | null>(null);
  const [mouseDown, setMouseDown] = useState<{ x: number; y: number } | null>(null);

  useEffect(() => {
    const onMouseMove = (e: MapMouseEvent) => {
      if (mouseDown) {
        e.preventDefault();

        if (!direction) {
          return;
        }

        const move = { x: e.point.x - mouseDown.x, y: e.point.y - mouseDown.y };

        onResize(direction, move);

        setMouseDown(e.point);
      }
    };

    const onMouseUp = (e: MapMouseEvent) => {
      if (mouseDown) {
        e.preventDefault();

        setMouseDown(null);
      }
    };

    const onMouseDown = (e: MapMouseEvent) => {
      const target = (e.originalEvent.target as HTMLElement).className;
      if (target.indexOf("handle") > -1) {
        e.preventDefault();

        setMouseDown(e.point);

        if (target.indexOf(Direction.TopLeft) > -1) {
          setDirection(Direction.TopLeft);
        } else if (target.indexOf(Direction.TopRight) > -1) {
          setDirection(Direction.TopRight);
        } else if (target.indexOf(Direction.BottomLeft) > -1) {
          setDirection(Direction.BottomLeft);
        } else if (target.indexOf(Direction.BottomRight) > -1) {
          setDirection(Direction.BottomRight);
        }
      }
    };

    map.on("mousedown", onMouseDown);
    map.on("mousemove", onMouseMove);
    map.on("mouseup", onMouseUp);

    return () => {
      map.off("mousedown", onMouseDown);
      map.off("mousemove", onMouseMove);
      map.off("mouseup", onMouseUp);
    };
  }, [map, direction, mouseDown, onResize]);

  return (
    <>
      <div className={`handle ${Direction.TopLeft}`} />
      <div className={`handle ${Direction.TopRight}`} />
      <div className={`handle ${Direction.BottomLeft}`} />
      <div className={`handle ${Direction.BottomRight}`} />
    </>
  );
};

Handles.displayName = "Handles";

export default Handles;
