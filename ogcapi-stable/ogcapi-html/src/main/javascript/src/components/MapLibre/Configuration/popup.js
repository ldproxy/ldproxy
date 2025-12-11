const changeCursor = (map, cursor) => {
  const canvas = map.getCanvas();
  canvas.style.cursor = cursor;
};

const firstCoordinate = (geometry) => {
  switch (geometry.type) {
    case "Point":
      return geometry.coordinates;
    case "LineString":
    case "MultiPoint":
      return geometry.coordinates[0];
    case "Polygon":
    case "MultiLineString":
      return geometry.coordinates[0][0];
    case "MultiPolygon":
      return geometry.coordinates[0][0][0];
    default:
      return null;
  }
};

const showPopup = (map, popup, featureTitles) => {
  let currentLngLat;
  return (e) => {
    const lngLat = firstCoordinate(e.features[0].geometry);
    if (currentLngLat !== lngLat) {
      currentLngLat = lngLat;
      changeCursor(map, "pointer");
      const description = featureTitles[e.features[0].id] || e.features[0].id;

      // Ensure that if the map is zoomed out such that multiple
      // copies of the feature are visible, the popup appears
      // over the copy being pointed to.
      while (Math.abs(e.lngLat.lng - lngLat[0]) > 180) {
        lngLat[0] += e.lngLat.lng > lngLat[0] ? 360 : -360;
      }

      if (lngLat && description) {
        popup.setLngLat(lngLat).setHTML(description).addTo(map);
      }
    }
  };
};

const getPopupContent = (e) => {
  const title = e.features[0].sourceLayer ||
          e.features[0].properties.featureType ||
          "feature";
  let description = `<h5>${title}</h5><hr/><table>`;

  Object.keys(e.features[0].properties)
    .sort()
    .forEach((prop) => {
      let val = e.features[0].properties[prop];
      if (typeof val === 'string' && /^https?:\/\/[^\s]+$/.test(val)) {
        val = `<a href="${val}" target="_blank">${val}</a>`;
      }
      description += `<tr><td title="${prop}" class="pr-4"><strong>${prop}</strong></td><td title="${e.features[0].properties[prop]}">${val}</td></tr>`;
    });

  description += "</table>";

  return Promise.resolve(description);
};

const showPopupProps = (map, popup) => (e) => {
  const lngLat = firstCoordinate(e.features[0].geometry);

  // Ensure that if the map is zoomed out such that multiple
  // copies of the feature are visible, the popup appears
  // over the copy being pointed to.
  while (Math.abs(e.lngLat.lng - lngLat[0]) > 180) {
    lngLat[0] += e.lngLat.lng > lngLat[0] ? 360 : -360;
  }

  // eslint-disable-next-line no-undef
  const description = globalThis.getPopupContent ? globalThis.getPopupContent(e, map) : getPopupContent(e);

  if (lngLat && description) {
    description.then(d => popup.setLngLat(lngLat).setHTML(d).addTo(map));
  }
};

const hidePopup = (map, popup) => () => {
  changeCursor(map, "");
  popup.remove();
};

export const addPopup = (map, maplibre, featureTitles = {}, layerIds = ["points"]) => {
  const popup = new maplibre.Popup({
    closeButton: false,
    closeOnClick: false,
  });

  layerIds.forEach((layerId) => {
    // Make sure to detect feature change for overlapping features and use mousemove instead of mouseenter event
    map.on('mousemove', layerId, showPopup(map, popup, featureTitles));
    map.on("mouseleave", layerId, hidePopup(map, popup));
  });
};

export const addPopupProps = (map, maplibre, layerIds = []) => {
  const popup = new maplibre.Popup({
    closeButton: true,
    closeOnClick: true,
    maxWidth: "50%",
    className: "popup-props",
  });

  layerIds.forEach((layerId) => {
    map.on("mouseenter", layerId, () => changeCursor(map, "pointer"));
    map.on("mouseleave", layerId, () => changeCursor(map, ""));
    map.on("click", layerId, showPopupProps(map, popup));
  });
};

/*
    style.mustache

    <style>
    {{#layerSwitcher}}
    #menu {
    background: #fff;
    position: absolute;
    z-index: 1;
    top: 10px;
    right: 50px;
    border-radius: 3px;
    border: 1px solid rgba(0, 0, 0, 0.4);
    font-family: 'Open Sans', sans-serif;
    }

    #menu a {
    font-size: 12px;
    color: #404040;
    display: block;
    margin: 0;
    padding: 0;
    padding: 5px;
    text-decoration: none;
    border-bottom: 1px solid rgba(0, 0, 0, 0.25);
    text-align: center;
    }

    #menu a:last-child {
    border: none;
    }

    #menu a:hover {
    background-color: #f8f8f8;
    color: #404040;
    }

    #menu a.active {
    background-color: #3887be;
    color: #ffffff;
    }

    #menu a.active:hover {
    background: #3074a4;
    }
    {{/layerSwitcher}}
    </style>
*/

/*
    style.mustache

    <script>

    {{#layerSwitcher}}
    var toggleableLayerIdMap = {{{layerIds}}};

    // set up the corresponding toggle button for each layer
    for (var tileLayerId of Object.keys(toggleableLayerIdMap)) {
        var link = document.createElement('a');
        link.href = '#';
        link.className = 'active';
        link.textContent = tileLayerId;

        link.onclick = function (e) {
            var clickedLayer = this.textContent;
            e.preventDefault();
            e.stopPropagation();

            var styleLayerIds = toggleableLayerIdMap[clickedLayer];
            var obj = this;

            styleLayerIds.forEach(function(styleLayerId) {
                var visibility = map.getLayoutProperty(styleLayerId, 'visibility');

                // toggle layer visibility by changing the layout object's visibility property
                if (visibility === 'visible') {
                    obj.className = '';
                    map.setLayoutProperty(styleLayerId, 'visibility', 'none');
                } else {
                    obj.className = 'active';
                    map.setLayoutProperty(styleLayerId, 'visibility', 'visible');
                }
            })
        };

        var layers = document.getElementById('menu');
        layers.appendChild(link);
    }
    {{/layerSwitcher}}
    </script>

    {{#layerSwitcher}}
    <nav id="menu"></nav>
    {{/layerSwitcher}}
*/
