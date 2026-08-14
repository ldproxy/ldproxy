import React from "react";
import { createRoot } from "react-dom/client";
import PropTypes from "prop-types/prop-types";
import { useMaplibreUIEffect } from "react-maplibre-ui";
import Control from "./Control";

const LayerControl = ({ opened, onlyLegend, preferStyle, entries }) => {
  useMaplibreUIEffect(({ map }) => {
    const container = document.createElement("div");

    map.addControl(
      {
        onAdd: () => {
          createRoot(container).render(
            <React.StrictMode>
              <Control
                opened={opened}
                onlyLegend={onlyLegend}
                preferStyle={preferStyle}
                entries={entries}
                map={map}
                maxHeight={map.getContainer().offsetHeight * 0.75}
              />
            </React.StrictMode>
          );
          return container;
        },
        onRemove: () => container.parentNode.removeChild(container),
      },
      "top-left"
    );
  }, []);

  return null;
};

const LayerObject = PropTypes.shape({
  id: PropTypes.string.isRequired,
  label: PropTypes.string,
  zoom: PropTypes.number,
  properties: PropTypes.objectOf(PropTypes.any),
});

const Layer = PropTypes.oneOfType([LayerObject, PropTypes.string]);

const RadioGroup = PropTypes.shape({
  id: PropTypes.string.isRequired,
  label: PropTypes.string,
  type: PropTypes.oneOf(["radio-group"]).isRequired,
  entries: PropTypes.arrayOf(Layer),
});

const MergeGroup = PropTypes.shape({
  id: PropTypes.string.isRequired,
  label: PropTypes.string,
  type: PropTypes.oneOf(["merge-group"]).isRequired,
  sourceLayer: PropTypes.string,
  entries: PropTypes.arrayOf(Layer),
});

const groupBase = {
  id: PropTypes.string.isRequired,
  label: PropTypes.string,
  type: PropTypes.oneOf(["group"]).isRequired,
  onlyLegend: PropTypes.bool,
};

groupBase.entries = PropTypes.arrayOf(
  PropTypes.oneOfType([Layer, MergeGroup, PropTypes.shape(groupBase)])
);

const Group = PropTypes.shape(groupBase);

LayerControl.propTypes = {
  opened: PropTypes.bool,
  onlyLegend: PropTypes.bool,
  preferStyle: PropTypes.bool,
  entries: PropTypes.arrayOf(PropTypes.oneOfType([Layer, MergeGroup, RadioGroup, Group])),
};

LayerControl.defaultProps = {
  opened: false,
  onlyLegend: false,
  preferStyle: true,
  entries: [],
};

LayerControl.displayName = "LayerControl";

export default LayerControl;
