import React, { useCallback, useRef, useEffect } from "react";
import PropTypes from "prop-types";

import { RMap, RLayerTile, RLayerVectorTile, RControl } from "rlayers";
import { fromLonLat } from "ol/proj";
import { boundingExtent, getCenter } from "ol/extent";
import { MVT } from "ol/format";
import { setupProjections } from "./proj";
import DynamicView from "./DynamicView";
import DynamicSource from "./DynamicSource";

import "ol/ol.css";
import "./custom.css";

setupProjections();

const OpenLayers = ({
  backgroundUrl,
  bounds,
  attribution,
  dataUrl,
  dataType,
  tileMatrixSets,
  styleUrl,
}) => {
  const effectiveStyleUrl = styleUrl && styleUrl.trim() ? styleUrl : null;

  const [currentFeature, setCurrentFeature] = React.useState(null);
  const [currentTileMatrixSet, setCurrentTileMatrixSet] = React.useState(
    tileMatrixSets[0] ? tileMatrixSets[0].tileMatrixSet : null
  );
  const [styleConfig, setStyleConfig] = React.useState(null);
  const prevTMSRef = useRef();
  useEffect(() => {
    prevTMSRef.current = currentTileMatrixSet;
  });
  const previousTileMatrixSet = prevTMSRef.current;

  useEffect(() => {
    if (effectiveStyleUrl) {
      fetch(effectiveStyleUrl)
        .then((response) => response.json())
        .then((style) => {
          console.log("Fetched style:", style);
          const config = {
            center: style.center,
            zoom: style.zoom,
          };
          if (style.sources) {
            const sourceWithBounds = Object.values(style.sources).find((s) => s.bounds);
            if (sourceWithBounds && sourceWithBounds.bounds) {
              config.bounds = sourceWithBounds.bounds;
            }
          }

          const sourcesCount = style.sources ? Object.keys(style.sources).length : 0;
          const layersCount = style.layers ? style.layers.length : 0;
          if ((sourcesCount >= 2 || layersCount >= 2) && style.layers) {
            const rasterLayer = style.layers.find((l) => l.type === "raster");
            if (
              rasterLayer &&
              rasterLayer.source &&
              style.sources &&
              style.sources[rasterLayer.source]
            ) {
              const { tiles, attribution: rasterAttribution } = style.sources[rasterLayer.source];
              const rasterBackgroundUrl = tiles ? tiles[0] : undefined;
              config.backgroundUrl = rasterBackgroundUrl;
              config.attribution = rasterAttribution;
            }
          }

          setStyleConfig(config);
        })
        .catch((error) => {
          console.error("[OpenLayers] Failed to load style configuration:", error);
        });
    }
  }, [effectiveStyleUrl]);

  // eslint-disable-next-line no-undef, no-underscore-dangle
  globalThis._map.setCurrentTileMatrixSet = setCurrentTileMatrixSet;

  const baseUrl = backgroundUrl.indexOf("{s}")
    ? backgroundUrl.replace(/\{s\}/, "{a-c}")
    : backgroundUrl;
  let initial = null;
  let extent = null;

  const tms = tileMatrixSets.find((tms1) => tms1.tileMatrixSet === currentTileMatrixSet);

  if (effectiveStyleUrl && styleConfig) {
    if (styleConfig.center) {
      initial = {
        center: fromLonLat(styleConfig.center),
        zoom: styleConfig.zoom || 0,
      };
    } else if (styleConfig.bounds && styleConfig.bounds.length === 4) {
      const boundsExtent = boundingExtent([
        fromLonLat([styleConfig.bounds[0], styleConfig.bounds[1]]),
        fromLonLat([styleConfig.bounds[2], styleConfig.bounds[3]]),
      ]);
      initial = {
        center: getCenter(boundsExtent),
        zoom: styleConfig.zoom || 0,
      };
    } else {
      initial = {
        center: fromLonLat([0, 0]),
        zoom: styleConfig.zoom || 0,
      };
    }
  } else if (tms) {
    initial = {
      center: fromLonLat([tms.defaultCenterLon, tms.defaultCenterLat]),
      zoom: tms.defaultZoomLevel,
    };
  } else if (bounds) {
    extent = boundingExtent([fromLonLat(bounds[0]), fromLonLat(bounds[1])]);
    initial = { center: getCenter(extent), zoom: 6 };
  }

  return (
    <>
      <RMap width="100%" height="100%" initial={initial} noDefaultControls>
        <DynamicView tileMatrixSet={tms} update={previousTileMatrixSet !== currentTileMatrixSet} />
        <RLayerTile
          properties={{ label: "Base map" }}
          url={styleConfig?.backgroundUrl || baseUrl}
          attributions={styleConfig?.attribution || attribution}
        />
        {dataType === "raster" && (
          <RLayerTile properties={{ label: "Vector tiles" }} url={dataUrl}>
            <DynamicSource
              tileMatrixSet={tms}
              dataUrl={dataUrl}
              dataType={dataType}
              styleUrl={effectiveStyleUrl}
              update={previousTileMatrixSet !== currentTileMatrixSet}
            />
          </RLayerTile>
        )}
        {dataType === "vector" && (
          <RLayerVectorTile
            properties={{ label: "Vector tiles" }}
            url={dataUrl}
            format={new MVT()}
            // eslint-disable-next-line react-hooks/rules-of-hooks
            onPointerEnter={useCallback((e) => setCurrentFeature(e.target), [setCurrentFeature])}
            // eslint-disable-next-line react-hooks/rules-of-hooks
            onPointerLeave={useCallback(
              (e) => currentFeature === e.target && setCurrentFeature(null),
              [currentFeature, setCurrentFeature]
            )}
          >
            <DynamicSource
              tileMatrixSet={tms}
              dataUrl={dataUrl}
              dataType={dataType}
              styleUrl={effectiveStyleUrl}
              update={previousTileMatrixSet !== currentTileMatrixSet}
            />
          </RLayerVectorTile>
        )}
        <RControl.RZoom />
        <RControl.RAttribution collapsible={false} />
      </RMap>
      <div id="map-info" style={{ opacity: currentFeature === null ? 0 : 1 }}>
        {currentFeature &&
          // eslint-disable-next-line no-underscore-dangle
          currentFeature.properties_ &&
          JSON.stringify(
            // eslint-disable-next-line no-underscore-dangle
            { id: currentFeature.id_, ...currentFeature.properties_ },
            null,
            2
          )}
      </div>
    </>
  );
};

OpenLayers.displayName = "OpenLayers";

OpenLayers.propTypes = {
  backgroundUrl: PropTypes.string,
  attribution: PropTypes.string,
  bounds: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.number)),
  dataUrl: PropTypes.string,
  dataType: PropTypes.string,
  tileMatrixSets: PropTypes.arrayOf(PropTypes.objectOf(PropTypes.string)),
  styleUrl: PropTypes.string,
};

OpenLayers.defaultProps = {
  backgroundUrl: "https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
  bounds: null,
  dataUrl: null,
  dataType: null,
  tileMatrixSets: [],
  styleUrl: null,
};

export default OpenLayers;
