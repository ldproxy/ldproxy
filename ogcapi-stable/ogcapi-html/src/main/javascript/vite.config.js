import { fileURLToPath } from 'node:url';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve, relative, dirname, join, sep } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteStaticCopy } from 'vite-plugin-static-copy';
import { ogcapiHtmlMustachePlugin } from './vite-plugins/ogcapi-html-mustache.js';

const root = dirname(fileURLToPath(import.meta.url));

// Replaces the old @neutrinojs/copy step: Cesium loads its Workers/Assets/ThirdParty/Widgets
// via plain runtime fetch()/Worker() calls under CESIUM_BASE_URL, not via JS imports, so no
// bundler can pick them up on its own — they have to be copied to the output verbatim.
const cesiumEngineDir = dirname(resolve(root, 'node_modules/@cesium/engine/package.json'));
const cesiumWidgetsDir = dirname(resolve(root, 'node_modules/@cesium/widgets/package.json'));
const cesiumVersion = JSON.parse(
  readFileSync(join(cesiumWidgetsDir, 'package.json'), 'utf-8')
).version;
const cesiumPath = `cesium/${cesiumVersion}`;

// maplibre-gl needs its worker script (all vector tile fetching/parsing runs off the main
// thread) at a URL it computes itself at runtime from `import.meta.url` of wherever its own
// bundled code ends up — not via a statically analyzable `new Worker(new URL(...))` call Vite's
// bundler could detect and emit automatically. Without this copy, that request 404s silently:
// raster layers (loaded directly on the main thread) still render, but vector layers never do,
// with no console error to point at it.
const maplibreGlDir = dirname(resolve(root, 'node_modules/maplibre-gl/package.json'));

// rename.stripBase needs the exact number of path segments to strip so only the subtree
// *below* `dir` survives under `dest` (a plain `stripBase: true` strips everything down to
// the bare filename, losing subdirectories Cesium's runtime depends on, e.g.
// Assets/IAU2006_XYS/IAU2006_XYS_18.json or Widgets/Images/NavigationHelp/*.svg). Computed
// from `root` instead of hardcoded so it stays correct regardless of the exact node_modules
// layout (hoisting, nested installs, etc.).
const stripBaseCount = (dir) => relative(root, dir).split(sep).length;

// NOTE: src/apps/common looks like a non-rendering leftover (just imports react/react-dom,
// no render call — it used to seed Neutrino/Webpack's splitChunks vendor chunk), but its
// generated app-common.mustache IS a real Mustache partial, included via `{{> app-common}}`
// from featureCollection.mustache (and transitively from landingPage.mustache). Dropping it
// broke every dataset landing/collection page with a 500 (MustacheNotFoundException) — a `grep`
// over src/main/java alone doesn't catch it, the reference lives in a .mustache file. Keep it.
const apps = readdirSync(resolve(root, 'src/apps'));
const styles = readdirSync(resolve(root, 'src/styles'));

const input = {};
apps.forEach((app) => {
  input[app] = resolve(root, `src/apps/${app}/index.jsx`);
});
styles.forEach((style) => {
  input[`style-${style}`] = resolve(root, `src/styles/${style}/index.js`);
});

export default defineConfig({
  root,
  base: '/ogcapi-html/',
  resolve: {
    // The @xtramaps/* packages are file:-linked from a sibling repo (its own separate npm
    // project, own node_modules) — Node resolves their own `react`/`react-dom` from xtramaps'
    // node_modules, not ours, even though it's the same version. Two physically different
    // copies of React loaded at once breaks hooks ("Cannot read properties of null (reading
    // 'useState')"); dedupe forces every resolution to our single copy. `ol` carries its own
    // global registry state (projections, EPSG codes, `setupProjections()`) that would silently
    // split across two copies the same way; `rlayers` gets deduped alongside it for consistency
    // since it wraps `ol` + React context providers of its own. `@cesium/engine`/`@cesium/widgets`
    // get the same treatment - `Ion.defaultAccessToken`/`Camera.DEFAULT_VIEW_RECTANGLE` are
    // global statics on the Cesium API itself, not per-viewer state.
    dedupe: ['react', 'react-dom', 'ol', 'rlayers', '@cesium/engine', '@cesium/widgets'],
  },
  plugins: [
    // classic, not automatic (the @vitejs/plugin-react default): the automatic JSX runtime's
    // jsx()/jsxs() helpers never resolve `Component.defaultProps` for function components —
    // only React.createElement does. The old Babel/webpack build used the classic transform,
    // so every component that still relies on defaultProps (MapLibre, OpenLayers, Cesium —
    // deliberately deferred to Phase D) silently got `undefined` props instead under the
    // automatic runtime. Every file already does `import React from "react"`, so classic mode
    // is a drop-in fix with no source changes.
    react({ jsxRuntime: 'classic' }),
    ogcapiHtmlMustachePlugin({ apps, styles }),
    viteStaticCopy({
      // Two independent fixes needed on every target below, both invisible until Cesium was
      // actually exercised deeply (terrain, 3D Tiles, NavigationHelp/InfoBox overlays):
      // 1. A trailing `/*` only matches files, not directories - fast-glob doesn't recurse into
      //    matched subdirectories on its own, so nested assets (Textures/SkyBox/*.jpg,
      //    IAU2006_XYS/*.json, Widgets/Images/NavigationHelp/*.svg, Widgets/InfoBox/*.css) were
      //    never even matched, let alone copied. Changed to `/**/*` (recursive) for that.
      // 2. rename.stripBase: vite-plugin-static-copy only flattens a glob match into `dest` when
      //    it sits at a path segment boundary it can strip automatically - for src paths reaching
      //    into node_modules like these, it instead nests the entire matched path (e.g.
      //    `Assets/node_modules/@cesium/engine/Source/Assets/...`) under dest, leaving the real
      //    destination empty.
      // Symptom without both fixes: a wall of 404s for the affected files and Cesium's own
      // "An error occurred while rendering. Rendering has stopped." - not a partial degradation.
      targets: [
        {
          src: `${cesiumEngineDir}/Build/Workers/**/*`,
          dest: `assets/${cesiumPath}/Workers`,
          rename: { stripBase: stripBaseCount(`${cesiumEngineDir}/Build/Workers`) },
        },
        {
          src: `${cesiumEngineDir}/Source/Assets/**/*`,
          dest: `assets/${cesiumPath}/Assets`,
          rename: { stripBase: stripBaseCount(`${cesiumEngineDir}/Source/Assets`) },
        },
        {
          src: `${cesiumEngineDir}/Source/ThirdParty/**/*`,
          dest: `assets/${cesiumPath}/ThirdParty`,
          rename: { stripBase: stripBaseCount(`${cesiumEngineDir}/Source/ThirdParty`) },
        },
        {
          src: `${cesiumEngineDir}/Source/Widget/**/*`,
          dest: `assets/${cesiumPath}/Widgets/CesiumWidget`,
          rename: { stripBase: stripBaseCount(`${cesiumEngineDir}/Source/Widget`) },
        },
        {
          src: `${cesiumWidgetsDir}/Source/**/*`,
          dest: `assets/${cesiumPath}/Widgets`,
          rename: { stripBase: stripBaseCount(`${cesiumWidgetsDir}/Source`) },
        },
        {
          // The worker script itself imports `./maplibre-gl-shared.mjs` (code shared between
          // main thread and worker) as a plain relative specifier, so it has to sit right next
          // to it — same reasoning as the worker file itself, just one hop further.
          src: `${maplibreGlDir}/dist/maplibre-gl-{worker,shared}.mjs`,
          dest: `assets`,
          rename: { stripBase: true },
        },
      ],
    }),
  ],
  define: {
    'process.env.APP': JSON.stringify(process.env.APP ?? null),
    'process.env.CESIUM_PATH': JSON.stringify(cesiumPath),
    // Webpack 4 shimmed the Node `global` to the browser global object by default; several
    // app entries rely on that (`global._map`/`global._filter` alongside `globalThis.*`).
    // Vite/esbuild don't do this, so replicate it rather than touching the affected app files.
    global: 'globalThis',
  },
  build: {
    outDir: resolve(root, '../../../build/generated/src/main/resources/de/ii/ogcapi/html'),
    emptyOutDir: true,
    // favicon.ico must stay a real emitted asset (its URL is referenced by name in
    // ogcapi-html-mustache.js), never inlined as a data URI even though it is tiny.
    assetsInlineLimit: (filePath) => (filePath.endsWith('.ico') ? false : undefined),
    rollupOptions: {
      input,
      output: {
        entryFileNames: 'assets/[name].[hash].js',
        chunkFileNames: 'assets/[name].[hash].js',
        assetFileNames: 'assets/[name].[hash][extname]',
      },
    },
  },
});
