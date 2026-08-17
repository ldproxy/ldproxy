import { fileURLToPath } from 'node:url';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
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
    // 'useState')"); dedupe forces every resolution to our single copy.
    dedupe: ['react', 'react-dom'],
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
      targets: [
        { src: `${cesiumEngineDir}/Build/Workers/*`, dest: `assets/${cesiumPath}/Workers` },
        { src: `${cesiumEngineDir}/Source/Assets/*`, dest: `assets/${cesiumPath}/Assets` },
        {
          src: `${cesiumEngineDir}/Source/ThirdParty/*`,
          dest: `assets/${cesiumPath}/ThirdParty`,
        },
        {
          src: `${cesiumEngineDir}/Source/Widget/*`,
          dest: `assets/${cesiumPath}/Widgets/CesiumWidget`,
        },
        { src: `${cesiumWidgetsDir}/Source/*`, dest: `assets/${cesiumPath}/Widgets` },
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
