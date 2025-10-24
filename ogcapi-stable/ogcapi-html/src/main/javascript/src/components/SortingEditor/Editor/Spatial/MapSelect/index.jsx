/* eslint-disable prefer-template */
import React from "react";
import PropTypes from "prop-types";

import MapLibre, { CanvasPlugin } from "../../../../MapLibre";
import Resizer from "./Resizer";
import "./style.css";

// eslint-disable-next-line no-unused-vars
const MapSelect = ({ bounds, backgroundUrl, attribution, onChange }) => {
  return (
    <MapLibre
      backgroundUrl={backgroundUrl}
      attribution={attribution}
      bounds={bounds}
      fitBoundsOptions={{ padding: 50, maxZoom: 16, animate: false }}
      showCompass={false}
    >
      <CanvasPlugin>
        <Resizer bounds={bounds} onChange={onChange} />
      </CanvasPlugin>
    </MapLibre>
  );
};

MapSelect.displayName = "MapSelect";

MapSelect.propTypes = {
  bounds: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.number)),
  backgroundUrl: PropTypes.string,
  attribution: PropTypes.string,
  onChange: PropTypes.func.isRequired,
};

MapSelect.defaultProps = {
  bounds: [
    [0, 0],
    [0, 0],
  ],
  backgroundUrl: null,
  attribution: null,
};

export default MapSelect;
