// eslint-disable-next-line no-unused-vars
import React, { useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";

import Handles from "./Handles";
import { boundsToRect, rectToBounds, recalc } from "./calc";

const Resizer = ({ map, maplibre, bounds, onChange }) => {
  const boxRef = useRef();
  const [rect, setRect] = useState({ top: 0, left: 0, height: 0, width: 0 });

  useEffect(() => {
    const { top, left, bottom, right } = boundsToRect(map, bounds);

    setRect({ top, left, height: bottom - top, width: right - left });
  }, [map, bounds]);

  useEffect(() => {
    const update = () => onChange(rectToBounds(map, maplibre, rect));

    map.on("idle", update);

    return () => {
      map.off("idle", update);
    };
  }, [onChange, map, maplibre, rect]);

  useEffect(() => {
    const box = boxRef.current;
    if (!box) return;

    const { top, left, height, width } = rect;

    box.style.top = `${top}px`;
    box.style.left = `${left}px`;
    box.style.height = `${height}px`;
    box.style.width = `${width}px`;
    box.style.display = "block";
  }, [boxRef, rect]);

  const onResize = (direction, movement) => {
    const box = boxRef.current;
    if (!box) return;

    const next = recalc(rect, direction, movement);

    onChange(rectToBounds(map, maplibre, next));
    setRect(next);
  };

  return (
    <div className="boxdraw" ref={boxRef}>
      <Handles map={map} onResize={onResize} />
    </div>
  );
};

Resizer.displayName = "Resizer";

Resizer.propTypes = {
  // eslint-disable-next-line react/forbid-prop-types
  map: PropTypes.object.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  maplibre: PropTypes.object.isRequired,
  bounds: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.number)),
  onChange: PropTypes.func.isRequired,
};

Resizer.defaultProps = {
  bounds: [
    [0, 0],
    [0, 0],
  ],
};

export default Resizer;
