import { join } from 'node:path';

// Replaces Neutrino's HtmlWebpackPlugin + mustache.ejs pipeline: for every app/style entry,
// emit a templates/<key>.mustache snippet containing only that entry's own CSS/JS tags
// (never the other entries'), with {{assetsPrefix}} left as a placeholder for the Java side
// to fill in at request time. See mustache.ejs (old) for the exact tag-filtering behaviour
// this is meant to reproduce.
// Walks the static chunk-import graph from an entry chunk and unions every reachable chunk's
// own importedCss. Vite/Rolldown only track importedCss per chunk (the CSS belongs to whichever
// chunk the importing module ended up in, e.g. a shared non-entry chunk like "MapLibre.js"),
// they don't roll it up onto the entry themselves — so without this walk, CSS owned by a shared
// chunk silently never ends up in any generated template.
function collectCss(entryChunk, bundle) {
  const byFileName = new Map(Object.values(bundle).map((item) => [item.fileName, item]));
  const seen = new Set();
  const css = new Set();
  const queue = [entryChunk.fileName];

  while (queue.length > 0) {
    const fileName = queue.shift();
    if (seen.has(fileName)) continue;
    seen.add(fileName);

    const chunk = byFileName.get(fileName);
    if (!chunk || chunk.type !== 'chunk') continue;

    (chunk.viteMetadata?.importedCss ?? []).forEach((file) => css.add(file));
    (chunk.imports ?? []).forEach((imported) => queue.push(imported));
  }

  return [...css];
}

export function ogcapiHtmlMustachePlugin({ apps, styles }) {
  return {
    name: 'ogcapi-html-mustache',
    // Vite's own css-post plugin aggregates each entry's transitive importedCss (across
    // shared/vendor chunks) in its generateBundle hook; without enforce: 'post' this plugin
    // would run first and only see each entry's own, not-yet-aggregated CSS.
    enforce: 'post',
    generateBundle(_options, bundle) {
      // Only style-* templates get the favicon link — matches the original mustache.ejs +
      // .neutrinorc.js split, where `templateParameters` (and thus `files.favicon`) was only
      // ever wired up for the style entries, never for the app entries.
      const entries = [
        ...apps.map((name) => ({ key: name, templateName: `app-${name}`, includeFavicon: false })),
        ...styles.map((name) => ({
          key: `style-${name}`,
          templateName: `style-${name}`,
          includeFavicon: true,
        })),
      ];

      const favicon = Object.values(bundle).find(
        (asset) =>
          asset.type === 'asset' &&
          (asset.names ?? [asset.name]).some((name) => name?.startsWith('favicon.'))
      );

      for (const { key, templateName, includeFavicon } of entries) {
        const chunk = Object.values(bundle).find(
          (item) => item.type === 'chunk' && item.isEntry && item.name === key
        );
        if (!chunk) {
          this.warn(`ogcapi-html-mustache: no emitted chunk found for entry "${key}"`);
          continue;
        }

        const cssFiles = collectCss(chunk, bundle);
        const lines = [];

        if (includeFavicon && favicon) {
          lines.push(
            `<link rel="shortcut icon" type="image/x-icon" href="{{assetsPrefix}}${favicon.fileName}">`
          );
        }

        cssFiles.forEach((file) => {
          lines.push(`<link rel="stylesheet" href="{{assetsPrefix}}${file}">`);
        });
        // type="module": these chunks are real ESM (cross-chunk `import`s resolved natively
        // by the browser's module loader) — a classic script would throw a SyntaxError.
        lines.push(`<script type="module" src="{{assetsPrefix}}${chunk.fileName}"></script>`);

        const outPath = join('templates', `${templateName}.mustache`);
        this.emitFile({ type: 'asset', fileName: outPath, source: `${lines.join('\n')}\n` });
      }
    },
  };
}
