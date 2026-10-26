import { fileURLToPath } from 'node:url';
import { existsSync, readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { cesiumAssets, cesiumAssetsPath } from '@xtramaps/web-map-cesium/vite';
import { maplibreWorker } from '@xtramaps/web-map-maplibre/vite';
import { ogcapiHtmlMustachePlugin } from './vite-plugins/ogcapi-html-mustache.js';

const root = dirname(fileURLToPath(import.meta.url));

// NOTE: src/apps/common looks like a non-rendering leftover (just imports react/react-dom,
// no render call — it used to seed Neutrino/Webpack's splitChunks vendor chunk), but its
// generated app-common.mustache IS a real Mustache partial, included via `{{> app-common}}`
// from featureCollection.mustache (and transitively from landingPage.mustache). Dropping it
// broke every dataset landing/collection page with a 500 (MustacheNotFoundException) — a `grep`
// over src/main/java alone doesn't catch it, the reference lives in a .mustache file. Keep it.
const apps = readdirSync(resolve(root, 'src/apps'));
const styles = readdirSync(resolve(root, 'src/styles'));

// Phase F (TypeScript conversion) moves entries from .jsx/.js to .tsx/.ts one at a time, so
// both extensions have to resolve during the transition - picking whichever actually exists
// on disk avoids hardcoding a single extension that breaks as soon as a file is renamed.
const resolveEntry = (dir, extensions) => {
  const match = extensions.find((ext) => existsSync(resolve(root, `${dir}/index.${ext}`)));
  if (!match) {
    throw new Error(`No index.(${extensions.join('|')}) found in ${dir}`);
  }
  return resolve(root, `${dir}/index.${match}`);
};

const input = {};
apps.forEach((app) => {
  input[app] = resolveEntry(`src/apps/${app}`, ['tsx', 'jsx']);
});
styles.forEach((style) => {
  input[`style-${style}`] = resolveEntry(`src/styles/${style}`, ['ts', 'js']);
});

export default defineConfig({
  root,
  base: '/ogcapi-html/',
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
    cesiumAssets(),
    maplibreWorker(),
  ],
  define: {
    'process.env.APP': JSON.stringify(process.env.APP ?? null),
    'process.env.CESIUM_PATH': JSON.stringify(cesiumAssetsPath),
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
