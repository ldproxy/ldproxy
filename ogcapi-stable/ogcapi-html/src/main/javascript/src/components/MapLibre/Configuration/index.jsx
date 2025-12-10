// eslint-disable-next-line no-unused-vars
import React from "react";
import PropTypes from "prop-types";

import { useMaplibreUIEffect } from "react-maplibre-ui";
import MapboxDraw from "@mapbox/mapbox-gl-draw";
import combine from "@turf/combine";
import { geoJsonLayers, hoverLayers, vectorLayers, isDataLayer } from "../styles";
import { getBounds, getFeaturesWithIdAsProperty, idProperty } from "../geojson";
import { addPopup, addPopupProps } from "./popup";

import "@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css";

const setStyleGeoJson = (
  map,
  maplibre,
  styleUrl,
  removeZoomLevelConstraints,
  popup,
  featureTitles
) => {
  // eslint-disable-next-line no-undef
  fetch(styleUrl)
    .then((response) => response.json())
    .then((style) => {
      let baseStyle = { sources: {} };
      try {
        baseStyle = map.getStyle();
      } catch {
        // ignore
      }
      const dataStyle = {
        ...style,
        sources: {
          ...style.sources,
          ...baseStyle.sources,
        },
        layers: style.layers.map((layer) => {
          if (isDataLayer(layer)) {
            const newLayer = {
              ...layer,
              source: "data",
            };
            delete newLayer["source-layer"];
            if (removeZoomLevelConstraints) {
              delete newLayer.minzoom;
              delete newLayer.maxzoom;
            }
            return newLayer;
          }

          return layer;
        }),
      };

      if (dataStyle.sources.data) {
        dataStyle.sources.data.attribution = style.layers
          .filter(
            (layer) => isDataLayer(layer) && style.sources[layer.source].attribution
          )
          .map((layer) => style.sources[layer.source].attribution)
          .filter((v, i, a) => a.indexOf(v) === i)
          .join(" | ");
      }

      delete dataStyle.terrain;

      map.setStyle(dataStyle, { diff: false });

      if (popup === "HOVER_ID") {
        addPopup(
          map,
          maplibre,
          featureTitles,
          dataStyle.layers
            .filter((layer) => isDataLayer(layer) === true)
            .map((layer) => layer.id)
        );
      }
    });
};

const setStyleVector = (
  map,
  maplibre,
  styleUrl,
  removeZoomLevelConstraints,
  popup,
  sourceUrl,
  sourceLayers
) => {
  // eslint-disable-next-line no-undef
  fetch(styleUrl)
    .then((response) => response.json())
    .then((style) => {
      let baseStyle = { sources: {} };
      try {
        baseStyle = map.getStyle();
      } catch {
        // ignore
      }
      let dataStyle = style;

      if (sourceUrl) {
        const newSources = {};
        Object.keys(style.sources).forEach((source) => {
          if (style.sources[source].type === "vector") {
            newSources[source] = {
              ...style.sources[source],
              tiles: [sourceUrl],
            };
          } else {
            newSources[source] = style.sources[source];
          }
        });

        dataStyle = {
          ...style,
          sources: {
            ...newSources,
            ...baseStyle.sources,
          },
          layers: style.layers
            .filter(
              (layer) =>
                !isDataLayer(layer) ||
                (layer["source-layer"] &&
                  (sourceLayers.length === 0 ||
                    sourceLayers.includes(layer["source-layer"])))
            )
            .map((layer) => {
              if (layer.type === "vector") {
                const newLayer = {
                  ...layer,
                };
                if (removeZoomLevelConstraints) {
                  delete newLayer.minzoom;
                  delete newLayer.maxzoom;
                }
                return newLayer;
              }
              return layer;
            }),
        };
      }

      map.setStyle(dataStyle, { diff:false });

      if (popup === "CLICK_PROPERTIES") {
        addPopupProps(
          map,
          maplibre,
          dataStyle.layers
            .filter((layer) => isDataLayer(layer) === true)
            .map((layer) => layer.id)
        );
      }
    });
};

const addData = (
  map,
  maplibre,
  styleUrl,
  removeZoomLevelConstraints,
  data,
  dataType,
  dataLayers,
  defaultStyle,
  fitBounds,
  popup,
  featureTitles
) => {
  if (dataType === "geojson") {
    const features = getFeaturesWithIdAsProperty(data);

    map.addSource("data", {
      type: "geojson",
      data: features,
      promoteId: idProperty,
    });

    if (fitBounds) {
      const bounds = getBounds(data);

      map.fitBounds(bounds, {
        padding: 50,
        maxZoom: 16,
        duration: 500,
      });
    }

    if (styleUrl) {
      setStyleGeoJson(map, maplibre, styleUrl, removeZoomLevelConstraints, popup, featureTitles);
    } else {
      const defaultLayers = geoJsonLayers(defaultStyle);

      defaultLayers.forEach((layer) => map.addLayer(layer));

      if (popup === "HOVER_ID") {
        addPopup(map, maplibre, featureTitles, hoverLayers);
      } else if (popup === "CLICK_PROPERTIES") {
        addPopupProps(
          map,
          maplibre,
          defaultLayers.map((l) => l.id)
        );
      }
    }
  } else if (dataType === "vector") {
    if (styleUrl) {
      setStyleVector(
        map,
        maplibre,
        styleUrl,
        removeZoomLevelConstraints,
        popup,
        data,
        Object.keys(dataLayers)
      );
    } else {
      map.addSource("data", {
        type: "vector",
        tiles: [data],
      });

      const layers = Object.keys(dataLayers).flatMap((layer) =>
        vectorLayers(layer, dataLayers[layer], defaultStyle)
      );

      layers.forEach((vectorLayer) => map.addLayer(vectorLayer));

      if (popup === "CLICK_PROPERTIES") {
        addPopupProps(
          map,
          maplibre,
          layers.map((l) => l.id)
        );
      }
    }
  } else if (dataType === "raster") {
    map.addSource("data", {
      type: "raster",
      tiles: [data],
    });
    map.addLayer({
      id: "data",
      type: "raster",
      source: "data",
    });
  }
};

const MapLibreConfiguration = ({
  styleUrl,
  removeZoomLevelConstraints,
  data,
  dataType,
  dataLayers,
  controls,
  defaultStyle,
  fitBounds,
  popup,
  featureTitles,
  custom,
  showCompass,
}) => {
  useMaplibreUIEffect(({ map, maplibre }) => {
    map.addControl(
      new maplibre.AttributionControl({
        compact: false,
      })
    );
    map.addControl(new maplibre.ScaleControl());
    if (controls) {
      map.addControl(new maplibre.NavigationControl({ showCompass }));
    }
    if (data) {
      const style = {
        ...MapLibreConfiguration.defaultProps.defaultStyle,
        ...defaultStyle,
      };

      if (dataType === "geojson" && typeof data === "string") {
        // eslint-disable-next-line no-undef
        fetch(data)
          .then((response) => response.json())
          .then((json) => {
            addData(
              map,
              maplibre,
              styleUrl,
              removeZoomLevelConstraints,
              json,
              dataType,
              dataLayers,
              style,
              fitBounds,
              popup,
              featureTitles
            );
          });
      } else {
        addData(
          map,
          maplibre,
          styleUrl,
          false,
          data,
          dataType,
          dataLayers,
          style,
          fitBounds,
          popup,
          featureTitles
        );
      }
    } else if (styleUrl) {
      setStyleVector(
        map,
        maplibre,
        styleUrl,
        removeZoomLevelConstraints,
        popup
      );
    }
    if (custom) {
      custom(map, maplibre, MapboxDraw, { combine });
    }
  }, []);

  return null;
};

MapLibreConfiguration.displayName = "MapLibreConfiguration";

MapLibreConfiguration.propTypes = {
  styleUrl: PropTypes.string,
  removeZoomLevelConstraints: PropTypes.bool,
  data: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
  dataType: PropTypes.string,
  dataLayers: PropTypes.objectOf(PropTypes.arrayOf(PropTypes.string)),
  controls: PropTypes.bool,
  defaultStyle: PropTypes.shape({
    color: PropTypes.string,
    opacity: PropTypes.number,
    circleRadius: PropTypes.number,
    circleMinZoom: PropTypes.number,
    circleMaxZoom: PropTypes.number,
    lineWidth: PropTypes.number,
    lineMinZoom: PropTypes.number,
    lineMaxZoom: PropTypes.number,
    fillOpacity: PropTypes.number,
    outlineWidth: PropTypes.number,
    polygonMinZoom: PropTypes.number,
    polygonMaxZoom: PropTypes.number,
  }),
  fitBounds: PropTypes.bool,
  popup: PropTypes.oneOf(["HOVER_ID", "CLICK_PROPERTIES"]),
  featureTitles: PropTypes.objectOf(PropTypes.string),
  custom: PropTypes.func,
  showCompass: PropTypes.bool,
};

MapLibreConfiguration.defaultProps = {
  styleUrl: null,
  removeZoomLevelConstraints: false,
  data: null,
  dataType: "geojson",
  dataLayers: {},
  controls: true,
  defaultStyle: {
    color: "#1D4E89",
    opacity: 1,
    circleRadius: 8,
    circleMinZoom: 0,
    circleMaxZoom: 24,
    lineWidth: 4,
    lineMinZoom: 0,
    lineMaxZoom: 24,
    fillOpacity: 0.2,
    outlineWidth: 2,
    polygonMinZoom: 0,
    polygonMaxZoom: 24,
  },
  fitBounds: true,
  popup: null,
  featureTitles: {},
  custom: null,
  showCompass: true,
};

export default MapLibreConfiguration;
