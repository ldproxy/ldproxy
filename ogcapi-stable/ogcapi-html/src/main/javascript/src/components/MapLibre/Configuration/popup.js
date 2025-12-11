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

const featureHtml = (feature, idx, total) => {
  const title = feature.sourceLayer || feature.properties.featureType || "feature";
  const header = `
    <div style="display:flex;align-items:center;justify-content:space-between;margin-top:10px;gap:20px;">
      <h5 style="margin:0;">${title}</h5>
      ${
        total > 1
          ? `
          <div class="popup-navigation">
            <a href="#" id="popup-prev-${idx}" class="popup-nav-btn">&lt;</a>
            <span id="popup-index-${idx}" class="popup-index">${idx + 1}/${total}</span>
            <a href="#" id="popup-next-${idx}" class="popup-nav-btn">&gt;</a>
          </div>
      `
          : ""
      }
    </div>
  `;
  let description = `${header}<hr/><table style="width: 100%;">`;
  Object.keys(feature.properties)
    .sort()
    .forEach((prop) => {
      let val = feature.properties[prop];
      if (typeof val === "string" && /^https?:\/\/[^\s]+$/.test(val)) {
        val = `<a href="${val}" target="_blank">${val}</a>`;
      }
      description += `<tr><td title="${prop}" class="pr-4"><strong>${prop}</strong></td><td title="${feature.properties[prop]}">${val}</td></tr>`;
    });
  description += "</table>";
  return `<div class="popup-feature" id="popup-feature-${idx}" style="display:${
    idx === 0 ? "block" : "none"
  }">${description}</div>`;
};

const getPopupContent = ({ features }) => {
  if (!features || features.length === 0) {
    return Promise.resolve("");
  }

  if (features.length === 1) {
    return Promise.resolve(featureHtml(features[0], 0, 1));
  }

  const allFeaturesHtml = features.map((f, i) => featureHtml(f, i, features.length)).join("");
  return Promise.resolve(allFeaturesHtml);
};

const showPopupProps = (map, popup) => (e) => {
  const allFeatures = map.queryRenderedFeatures(e.point);

  if (!allFeatures || allFeatures.length === 0) {
    return;
  }

  // Deduplicate features based on id and sourceLayer
  const featuresMap = new Map();
  allFeatures.forEach((f, index) => {
    const featureId = f.id !== undefined ? f.id : `idx-${index}`;
    const layerId = f.sourceLayer || f.layer?.id || "";
    const key = `${featureId}-${layerId}`;
    if (!featuresMap.has(key)) {
      featuresMap.set(key, f);
    }
  });
  const features = Array.from(featuresMap.values());

  if (features.length === 0) {
    return;
  }

  // Use the click location instead of the first coordinate of the geometry
  // This is better for polygons and lines where the first coordinate might be far from the click
  const lngLat = [e.lngLat.lng, e.lngLat.lat];

  /* eslint-disable no-undef */
  const description = globalThis.getPopupContent
    ? globalThis.getPopupContent(features, map)
    : getPopupContent({ features });
  /* eslint-enable no-undef */

  if (lngLat && description) {
    description.then((d) => {
      popup.setLngLat(lngLat).setHTML(d).addTo(map);

      if (features.length > 1) {
        let idx = 0;
        const total = features.length;

        const update = (newIdx) => {
          if (newIdx < 0 || newIdx >= total) return;
          document.getElementById(`popup-feature-${idx}`).style.display = "none";
          idx = newIdx;
          document.getElementById(`popup-feature-${idx}`).style.display = "block";
          const indexElement = document.getElementById(`popup-index-${idx}`);
          if (indexElement) {
            indexElement.textContent = `${idx + 1}/${total}`;
          }
          const prevBtn = document.getElementById(`popup-prev-${idx}`);
          const nextBtn = document.getElementById(`popup-next-${idx}`);
          if (prevBtn) {
            prevBtn.onclick = (evt) => {
              evt.preventDefault();
              update(idx - 1);
            };
          }
          if (nextBtn) {
            nextBtn.onclick = (evt) => {
              evt.preventDefault();
              update(idx + 1);
            };
          }
        };

        const initialPrevBtn = document.getElementById(`popup-prev-${idx}`);
        const initialNextBtn = document.getElementById(`popup-next-${idx}`);
        if (initialPrevBtn) {
          initialPrevBtn.onclick = (evt) => {
            evt.preventDefault();
            update(idx - 1);
          };
        }
        if (initialNextBtn) {
          initialNextBtn.onclick = (evt) => {
            evt.preventDefault();
            update(idx + 1);
          };
        }
      }
    });
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
    map.on("mousemove", layerId, showPopup(map, popup, featureTitles));
    map.on("mouseleave", layerId, hidePopup(map, popup));
  });
};

export const addPopupProps = (map, maplibre, layerIds = []) => {
  const popup = new maplibre.Popup({
    closeButton: true,
    closeOnClick: true,
    maxWidth: "50%",
    className: "popup-props",
    anchor: "top",
  });

  map.on("click", showPopupProps(map, popup));

  layerIds.forEach((layerId) => {
    map.on("mouseenter", layerId, () => changeCursor(map, "pointer"));
    map.on("mouseleave", layerId, () => changeCursor(map, ""));
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
