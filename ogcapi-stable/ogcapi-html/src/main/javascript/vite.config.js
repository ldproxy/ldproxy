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
  plugins: [
    react(),
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
