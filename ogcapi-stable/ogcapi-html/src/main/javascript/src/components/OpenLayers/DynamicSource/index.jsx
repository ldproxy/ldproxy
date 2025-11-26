import React from "react";
import PropTypes from "prop-types";
import { RContext } from "rlayers";
import { stylefunction } from "ol-mapbox-style";
import { VectorTile as VectorTileSource, XYZ as XYZSource } from "ol/source";
import TileGrid from "ol/tilegrid/TileGrid";
import { MVT } from "ol/format";

const DynamicView = ({ tileMatrixSet, dataUrl, dataType, update, styleObject }) => (
  <RContext.Consumer>
    {({ layer }) => {
      if (tileMatrixSet) {
        if (update) {
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
        }
        if (styleObject) {
          const sourceName = Object.entries(styleObject.sources).find(
            ([, source]) => source.type === "vector"
          )?.[0];
          if (sourceName) {
            stylefunction(layer, styleObject, sourceName, JSON.parse(tileMatrixSet.resolutions));
          }
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
  styleObject: PropTypes.shape({
    sources: PropTypes.objectOf(PropTypes.any),
  }),
};

DynamicView.defaultProps = {
  tileMatrixSet: null,
  dataUrl: "",
  dataType: null,
  update: false,
  styleObject: null,
};

export default DynamicView;
