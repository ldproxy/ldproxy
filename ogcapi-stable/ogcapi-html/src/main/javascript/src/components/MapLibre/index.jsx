import React from "react";
import PropTypes from "prop-types";

import { Map } from "react-maplibre-ui";
import "maplibre-gl/dist/maplibre-gl.css";
import "./custom.css";

import Configuration from "./Configuration";
import CanvasPlugin from "./CanvasPlugin";
import LayerControl from "./LayerControl";
import { baseStyle, emptyStyle } from "./styles";
import { polygonFromBounds } from "./geojson";

export { Configuration, CanvasPlugin, polygonFromBounds };

const MapLibre = ({
  styleUrl,
  removeZoomLevelConstraints,
  backgroundUrl,
  center,
  zoom,
  bounds,
  attribution,
  dataUrl,
  dataType,
  dataLayers,
  interactive,
  savePosition,
  drawBounds,
  defaultStyle,
  fitBoundsOptions,
  popup,
  featureTitles,
  custom,
  showCompass,
  layerGroupControl,
  children,
}) => {
  const style = styleUrl
    ? emptyStyle()
    : baseStyle(
        backgroundUrl,
        attribution,
        MapLibre.defaultProps.backgroundUrl,
        MapLibre.defaultProps.attribution
      );

  const data = drawBounds && bounds ? polygonFromBounds(bounds) : dataUrl;

  return (
    <Map
      mapStyle={style}
      defaultCenter={center}
      defaultZoom={zoom}
      style={{
        height: "100%",
        width: "100%",
      }}
      customParameters={{
        bounds,
        fitBoundsOptions,
        attributionControl: false,
        interactive,
        hash: savePosition, // ? 'position' : false,
      }}
    >
      <Configuration
        styleUrl={styleUrl}
        removeZoomLevelConstraints={removeZoomLevelConstraints}
        data={data}
        dataType={dataType}
        dataLayers={dataLayers}
        controls={interactive}
        showCompass={showCompass}
        defaultStyle={defaultStyle}
        fitBounds={!drawBounds && bounds}
        popup={popup}
        featureTitles={featureTitles}
        custom={custom}
      />
      {layerGroupControl && layerGroupControl.length > 0 && (
        <LayerControl entries={layerGroupControl} />
      )}
      {children}
    </Map>
  );
};

MapLibre.displayName = "MapLibre";

MapLibre.propTypes = {
  styleUrl: PropTypes.string,
  removeZoomLevelConstraints: PropTypes.bool,
  backgroundUrl: PropTypes.string,
  attribution: PropTypes.string,
  center: PropTypes.arrayOf(PropTypes.number),
  zoom: PropTypes.number,
  bounds: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.number)),
  interactive: PropTypes.bool,
  savePosition: PropTypes.bool,
  dataUrl: PropTypes.string,
  drawBounds: PropTypes.bool,
  // eslint-disable-next-line react/forbid-prop-types
  defaultStyle: PropTypes.object,
  // eslint-disable-next-line react/forbid-prop-types
  fitBoundsOptions: PropTypes.object,
  layerGroupControl: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.object, PropTypes.string])),
  ...Configuration.propTypes,
};

MapLibre.defaultProps = {
  styleUrl: null,
  removeZoomLevelConstraints: false,
  backgroundUrl: "https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
  center: [0, 0],
  zoom: 0,
  bounds: null,
  interactive: true,
  savePosition: false,
  drawBounds: false,
  dataUrl: null,
  defaultStyle: undefined,
  fitBoundsOptions: {
    padding: 30,
    maxZoom: 16,
    animate: false,
  },
  layerGroupControl: null,
  ...Configuration.defaultProps,
};

export default MapLibre;
