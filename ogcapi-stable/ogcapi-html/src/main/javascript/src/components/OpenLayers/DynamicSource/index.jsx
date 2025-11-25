import React from "react";
import PropTypes from "prop-types";
import { RContext } from "rlayers";
import { stylefunction } from "ol-mapbox-style";
import { VectorTile as VectorTileSource, XYZ as XYZSource } from "ol/source";
import TileGrid from "ol/tilegrid/TileGrid";
import { MVT } from "ol/format";

const DynamicView = ({ tileMatrixSet, dataUrl, dataType, update, styleUrl }) => (
  <RContext.Consumer>
    {({ layer }) => {
      if (update && tileMatrixSet) {
        layer.set(
          "source",
          dataType === "raster"
            ? new XYZSource({
                url: dataUrl.replace("/WebMercatorQuad/", `/${tileMatrixSet.tileMatrixSet}/`),
                maxZoom: tileMatrixSet.maxLevel,
                projection: tileMatrixSet.projection,
                tileGrid: new TileGrid({
                  extent: JSON.parse(tileMatrixSet.extent),
                  resolutions: JSON.parse(tileMatrixSet.resolutions),
                  sizes: JSON.parse(tileMatrixSet.sizes),
                }),
              })
            : new VectorTileSource({
                url: dataUrl.replace("/WebMercatorQuad/", `/${tileMatrixSet.tileMatrixSet}/`),
                format: new MVT(),
                maxZoom: tileMatrixSet.maxLevel,
                projection: tileMatrixSet.projection,
                tileGrid: new TileGrid({
                  extent: JSON.parse(tileMatrixSet.extent),
                  resolutions: JSON.parse(tileMatrixSet.resolutions),
                  sizes: JSON.parse(tileMatrixSet.sizes),
                }),
              })
        );

        if (styleUrl) {
          const updatedStyleUrl =
            tileMatrixSet.tileMatrixSet !== "WebMercatorQuad"
              ? `${styleUrl}${styleUrl.includes("?") ? "&" : "?"}tile-matrix-set=${
                  tileMatrixSet.tileMatrixSet
                }`
              : styleUrl;

          fetch(updatedStyleUrl)
            .then((response) => {
              if (!response.ok) {
                throw new Error(`Failed to fetch style: ${response.statusText}`);
              }
              return response.json();
            })
            .then((glStyle) => {
              const sourceName = Object.entries(glStyle.sources).find(
                ([, source]) => source.type === "vector"
              )?.[0];
              if (sourceName) {
                stylefunction(layer, glStyle, sourceName, JSON.parse(tileMatrixSet.resolutions));
              }
            })
            .catch((error) => {
              throw new Error(`Failed to fetch or apply style: ${error.message}`);
            });
        }
      }
    }}
  </RContext.Consumer>
);
DynamicView.displayName = "DynamicView";

DynamicView.propTypes = {
  tileMatrixSet: PropTypes.objectOf(PropTypes.string),
  dataUrl: PropTypes.string,
  dataType: PropTypes.string,
  update: PropTypes.bool,
  styleUrl: PropTypes.string,
};

DynamicView.defaultProps = {
  tileMatrixSet: null,
  dataUrl: "",
  dataType: null,
  update: false,
  styleUrl: "",
};

export default DynamicView;
