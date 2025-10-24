// eslint-disable-next-line no-unused-vars
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import { Direction } from "../constants";

const Handles = ({ onResize, map }) => {
  const [direction, setDirection] = useState(null);
  const [mouseDown, setMouseDown] = useState(false);

  useEffect(() => {
    const onMouseMove = (e) => {
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

    const onMouseUp = (e) => {
      if (mouseDown) {
        e.preventDefault();

        setMouseDown(null);
      }
    };

    const onMouseDown = (e) => {
      const target = e.originalEvent.target.className;
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

Handles.propTypes = {
  // eslint-disable-next-line react/forbid-prop-types
  map: PropTypes.object.isRequired,
  onResize: PropTypes.func.isRequired,
};

Handles.defaultProps = {};

export default Handles;
