/* eslint-disable no-param-reassign */
export const getLabel = (entry) => (typeof entry === "string" ? entry : entry.label || entry.id);

export const getId = (entry) => (typeof entry === "string" ? entry : entry.id);

export const asLayer = (entry) =>
  typeof entry === "string"
    ? { id: entry, isLayer: true, type: "layer" }
    : { ...entry, isLayer: true, type: "layer" };

export const getRadioGroups = (groups, selected) => {
  const radioGroups = {};

  groups
    .filter((g) => g.isBasemap === true)
    .forEach((g) => {
      if (selected) {
        radioGroups[g.id] = g.entries && g.entries.length > 0 ? g.entries[0].id : null;
      } else {
        radioGroups[g.id] = g.entries ? g.entries.map((e) => e.id) : [];
      }
    });

  return radioGroups;
};

const getRadioGroupLayers = (groups, selectedOnly) => {
  const radioGroups = {};

  groups
    .filter((g) => g.type === "radio-group")
    .forEach((g) => {
      if (selectedOnly) {
        radioGroups[g.id] = g.entries && g.entries.length > 0 ? getId(g.entries[0]) : null;
      } else {
        radioGroups[g.id] = g.entries ? g.entries.map((e) => getId(e)) : [];
      }
    });

  return radioGroups;
};

export const getIds = (entries, types) => {
  const ids = [];

  entries.forEach((e) => {
    if (!types || types.includes(e.type)) {
      ids.push(e.id);
    }
    if (e.entries) {
      getIds(e.entries, types).forEach((id) => ids.push(id));
    }
  });

  return ids;
};

export const getDeps = (groups, parents = []) => {
  const deps = {};

  groups.forEach((g) => {
    const children = g.entries || g.subLayers || g.layers || [];
    const childDeps = getDeps(children, parents.concat(g.id ? [g.id] : [g]));
    deps[g.id || g] = [
      ...new Set(
        parents
          .concat(g.id ? [g.id] : [g])
          .concat(Object.values(childDeps).flatMap((c) => c.id || c))
      ),
    ];
    Object.keys(childDeps).forEach((c) => {
      deps[c] = childDeps[c];
    });
  });

  return deps;
};

export const getParentDeps = (groups, parents = []) => {
  const deps = {};

  groups.forEach((g) => {
    const id = g.id || g;
    const children = g.entries || g.subLayers || g.layers || [];
    const childDeps = getParentDeps(children, [id].concat(parents));
    deps[id] = parents.filter((id2) => id2 !== id);
    Object.keys(childDeps).forEach((c) => {
      deps[c] = childDeps[c];
    });
  });

  return deps;
};

export const getChildDeps = (groups, tmp) => {
  const deps = { clean: {}, tmp: {} };

  groups.forEach((g) => {
    const id = g.id || g;
    const children = g.entries || g.subLayers || g.layers || [];
    const childDeps = getChildDeps(children, true);
    deps.tmp[id] =
      Object.keys(childDeps.tmp).length > 0
        ? [...new Set([id].concat(Object.values(childDeps.tmp).flatMap((c) => c.id || c)))]
        : [id];
    deps.clean[id] = deps.tmp[id].filter((id2) => id2 !== id);
    Object.keys(childDeps.clean).forEach((c) => {
      deps.clean[c] = childDeps.clean[c];
    });
  });

  return tmp ? deps : deps.clean;
};

const getLayers = (style) =>
  style && style.layers
    ? style.layers.reduce((layers, layer, index) => {
        layers[layer.id] = {
          id: layer.id,
          isLayer: true,
          type: "layer",
          sourceLayer: layer["source-layer"],
          index,
        };
        return layers;
      }, {})
    : {};

const getLayersFor = (sourceLayer, layers) =>
  Object.values(layers)
    .filter((layer) => layer.sourceLayer === sourceLayer)
    .toSorted((layer) => layer.index);

const enrichLayer = (target, layers) =>
  Object.hasOwn(layers, target.id) ? { ...target, ...layers[target.id] } : target;

const mergeLayers = (from, into) => {
  return from.map((layer) => {
    const matching = into.find((layer2) => layer2.id === layer.id);
    if (matching) {
      return { ...matching, ...layer };
    }
    return layer;
  });
};

const types = ["merge-group", "radio-group", "group", "layer", undefined];

const validate = (entry) => {
  if (typeof entry !== "string" && !Object.hasOwn(entry, "id")) {
    // eslint-disable-next-line no-console
    console.error("Invalid entry, only strings or objects with id allowed.", entry);
    return false;
  }
  if (!types.includes(entry.type)) {
    // eslint-disable-next-line no-console
    console.error("Invalid entry, unknown type:", entry.type);
    return false;
  }
  return true;
};

const hydrate = (entries, layers) => {
  return entries
    .map((entry) => {
      if (!validate(entry)) {
        return null;
      }
      if (entry.type === "merge-group" && entry.sourceLayer) {
        const matching = getLayersFor(entry.sourceLayer, layers);
        return {
          ...entry,
          entries: entry.entries ? mergeLayers(matching, hydrate(entry.entries, layers)) : matching,
        };
      }
      if (entry.entries) {
        return {
          ...entry,
          entries: hydrate(entry.entries, layers),
        };
      }

      if (entry.layers || entry.subLayers) {
        return entry;
      }

      return enrichLayer(asLayer(entry), layers);
    })
    .filter((entry) => entry !== null);
};

const loadImage = (url) => {
  let cancelled = false;
  const promise = new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "Anonymous";
    img.onload = () => {
      if (!cancelled) resolve(img);
    };
    img.onerror = (e) => {
      if (!cancelled) reject(e);
    };
    img.src = url;
  });
  promise.cancel = () => {
    cancelled = true;
  };
  return promise;
};

const loadJson = (url) => {
  return fetch(url).then((res) => res.json());
};

const loadSprite = (url) => {
  return Promise.allSettled([loadImage(`${url}@2x.png`), loadJson(`${url}@2x.json`)]).then(
    ([image, json]) => ({
      image,
      json,
    })
  );
};

const loadMultipleSprites = (sprites) => {
  const spritePromises = sprites.map((sprite) =>
    Promise.allSettled([loadImage(`${sprite.url}@2x.png`), loadJson(`${sprite.url}@2x.json`)]).then(
      ([image, json]) => ({
        id: sprite.id,
        image,
        json,
      })
    )
  );

  return Promise.all(spritePromises).then((loadedSprites) => {
    const mergedJson = {};

    loadedSprites.forEach((sprite) => {
      if (sprite.json.status === "fulfilled") {
        Object.assign(mergedJson, sprite.json.value);
      }
    });

    // first image as fallback for compatibility, but main.js will search through sprites if available
    const firstImage = loadedSprites.find((sprite) => sprite.image.status === "fulfilled")?.image
      .value;

    return {
      image: { status: "fulfilled", value: firstImage },
      json: { status: "fulfilled", value: mergedJson },
      sprites: loadedSprites,
    };
  });
};

export const parse = (style, entriesCfg, preferStyle) => {
  const cfg =
    preferStyle && style.metadata && style.metadata["ldproxy:layerControl"]
      ? style.metadata["ldproxy:layerControl"]
      : {};

  const { opened, onlyLegend, entries = entriesCfg } = cfg;
  const layers = getLayers(style);
  const hydrated = hydrate(entries, layers);

  const config = {
    opened,
    onlyLegend,
    entries: hydrated,
    allIds: getIds(hydrated),
    layerIds: getIds(hydrated, ["layer"]),
    groupIds: getIds(hydrated, ["group", "radio-group"]),
    radioIds: getRadioGroupLayers(hydrated, true),
    radioGroups: getRadioGroupLayers(hydrated),
    deps: getDeps(hydrated),
    depsParent: getParentDeps(hydrated),
    depsChild: getChildDeps(hydrated),
    style,
  };

  if (style.sprite) {
    const multipleSprites = Array.isArray(style.sprite);

    if (multipleSprites) {
      return loadMultipleSprites(style.sprite).then((sprite) => {
        style.spriteLoaded = { json: sprite.json.value, image: sprite.image.value };
        return config;
      });
    }
    return loadSprite(style.sprite).then((sprite) => {
      style.spriteLoaded = { json: sprite.json.value, image: sprite.image.value };
      return config;
    });
  }
  return Promise.resolve(config);
};

export const initialCfg = {
  entries: [],
  allIds: [],
  layerIds: [],
  groupIds: [],
  radioIds: {},
  radioGroups: {},
  deps: {},
  depsParent: {},
  depsChild: {},
  style: null,
};
