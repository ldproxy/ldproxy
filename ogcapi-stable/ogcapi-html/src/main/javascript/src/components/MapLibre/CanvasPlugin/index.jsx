/* eslint-disable prefer-template */
import React from "react";
import { createRoot } from "react-dom/client";

import { useMaplibreUIEffect } from "react-maplibre-ui";

const CanvasPlugin = ({ children }) => {
  useMaplibreUIEffect(({ map, maplibre }) => {
    const canvas = map.getCanvasContainer();
    const wrapper = document.createElement("div");
    wrapper.className = "canvas-container";
    canvas.appendChild(wrapper);

    const childrenWithMap = React.cloneElement(children, { map, maplibre });

    createRoot(wrapper).render(<>{childrenWithMap}</>);
  }, []);

  return null;
};

CanvasPlugin.displayName = "CanvasPlugin";

CanvasPlugin.propTypes = {};

CanvasPlugin.defaultProps = {};

export default CanvasPlugin;
