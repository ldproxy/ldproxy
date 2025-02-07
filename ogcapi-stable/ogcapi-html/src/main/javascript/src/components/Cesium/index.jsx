import {
  Ion,
  Camera,
  Credit,
  WebMapTileServiceImageryProvider,
  Rectangle,
  CesiumTerrainProvider,
  IonResource,
  Cesium3DTileset,
  Cesium3DTileStyle,
  Matrix4,
  Cartesian3,
  Color,
  ImageryLayer,
} from "@cesium/engine";
import { Viewer } from "@cesium/widgets";

// TODO: @cesium/widgets/Source/widgets.css does not work because of relative engine import, this is a copy of its contents
import "@cesium/widgets/Source/shared.css";
import "@cesium/widgets/Source/Animation/Animation.css";
import "@cesium/widgets/Source/BaseLayerPicker/BaseLayerPicker.css";
/* eslint-disable */
import "@cesium/engine/Source/Widget/CesiumWidget.css";
import "@cesium/widgets/Source/CesiumInspector/CesiumInspector.css";
import "@cesium/widgets/Source/Cesium3DTilesInspector/Cesium3DTilesInspector.css";
import "@cesium/widgets/Source/VoxelInspector/VoxelInspector.css";
import "@cesium/widgets/Source/FullscreenButton/FullscreenButton.css";
import "@cesium/widgets/Source/VRButton/VRButton.css";
import "@cesium/widgets/Source/Geocoder/Geocoder.css";
import "@cesium/widgets/Source/InfoBox/InfoBox.css";
import "@cesium/widgets/Source/SceneModePicker/SceneModePicker.css";
import "@cesium/widgets/Source/ProjectionPicker/ProjectionPicker.css";
import "@cesium/widgets/Source/PerformanceWatchdog/PerformanceWatchdog.css";
import "@cesium/widgets/Source/NavigationHelpButton/NavigationHelpButton.css";
import "@cesium/widgets/Source/SelectionIndicator/SelectionIndicator.css";
import "@cesium/widgets/Source/Timeline/Timeline.css";
import "@cesium/widgets/Source/Viewer/Viewer.css";

const Cesium = ({
  container,
  backgroundUrl,
  attribution,
  extent,
  accessToken,
  tileset,
  terrainProvider,
  additionalStyleUrl,
}) => {
  let viewer;

  Ion.defaultAccessToken = accessToken;

  if (extent) {
    Camera.DEFAULT_VIEW_RECTANGLE = Rectangle.fromDegrees(
      extent.minLon,
      extent.minLat,
      extent.maxLon,
      extent.maxLat
    );
    Camera.DEFAULT_VIEW_FACTOR = 0;
  }

  if (terrainProvider) {
    const { url, credit, ...options } = terrainProvider;
    viewer = new Viewer(container, {
      baseLayer: new ImageryLayer(
        new WebMapTileServiceImageryProvider({
          url: backgroundUrl,
          layer: "Base",
          style: "default",
          tileMatrixSetID: "WebMercatorQuad",
        })
      ),
      terrainProvider: new CesiumTerrainProvider({
        ...options,
        url: url || IonResource.fromAssetId(1),
        credit: credit ? new Credit(credit, true) : undefined,
      }),
      animation: false,
      baseLayerPicker: false,
      homeButton: false,
      geocoder: false,
      timeline: false,
      scene3DOnly: true,
      fullscreenButton: false,
      navigationInstructionsInitiallyVisible: false,
    });
  } else {
    viewer = new Viewer(container, {
      baseLayer: new ImageryLayer(
        new WebMapTileServiceImageryProvider({
          url: backgroundUrl,
          layer: "Base",
          style: "default",
          tileMatrixSetID: "WebMercatorQuad",
        })
      ),
      animation: false,
      baseLayerPicker: false,
      homeButton: true,
      geocoder: false,
      timeline: false,
      scene3DOnly: true,
      fullscreenButton: false,
      navigationInstructionsInitiallyVisible: false,
    });
  }

  if (attribution) {
    const credit = new Credit(attribution);
    viewer.scene.frameState.creditDisplay.addStaticCredit(credit);
  }

  if (tileset && tileset.url) {
    const { url, terrainHeightDifference, outlineColor } = tileset;
    const options = {
      outlineColor: outlineColor ? Color[outlineColor] : Color.DIMGREY,
    };
    if (terrainHeightDifference) {
      const { difference, centerLon, centerLat, centerHeight } = terrainHeightDifference;
      options.modelMatrix = Matrix4.fromTranslation(
        Cartesian3.subtract(
          Cartesian3.fromRadians(centerLon, centerLat, centerHeight + difference),
          Cartesian3.fromRadians(centerLon, centerLat, centerHeight),
          new Cartesian3()
        )
      );
    }

    Cesium3DTileset.fromUrl(url, options).then((tileset) => {
      if (additionalStyleUrl) {
        fetch(additionalStyleUrl)
            .then((response) => response.json())
            .then((style) => {
              tileset.style = new Cesium3DTileStyle(style);
              viewer.scene.primitives.add(tileset);
              viewer.flyTo(tileset);
            });
      } else {
        tileset.style = new Cesium3DTileStyle({});
        viewer.scene.primitives.add(tileset);
        viewer.flyTo(tileset);
      }
    });
  }
};

export default Cesium;
