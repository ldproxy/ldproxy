const airbnb = require("@neutrinojs/airbnb");
const react = require("@neutrinojs/react");
const mocha = require("@neutrinojs/mocha");
const copy = require("@neutrinojs/copy");
const xtraplatform = require("@xtraplatform/neutrino");
const path = require("path");
const fs = require("fs");

const mains = {};
fs.readdirSync(path.join(__dirname, "src/apps")).forEach(
  (app) =>
    (mains[app === "common" ? "ignore" : app] = {
      name: app,
      entry: `apps/${app}/index`,
      filename: `templates/app-${app}.mustache`,
      template: "mustache.ejs",
      minify: false,
      inject: false,
      scriptLoading: "defer",
      publicPath: "/ogcapi-html",
    })
);
fs.readdirSync(path.join(__dirname, "src/styles")).forEach(
  (style) =>
    (mains[`style-${style}`] = {
      name: `style-${style}`,
      entry: `styles/${style}/index`,
      filename: `templates/style-${style}.mustache`,
      template: "mustache.ejs",
      minify: false,
      inject: false,
      scriptLoading: "defer",
      publicPath: "/ogcapi-html",
      templateParameters: (compilation, assets, assetTags, options) => {
        const favicon = Object.keys(compilation.assets).find(
          (key) => key.indexOf("assets/favicon.") === 0
        );
        const files = favicon ? { ...assets, favicon: `${options.publicPath}/${favicon}` } : assets;

        return {
          compilation: compilation,
          webpackConfig: compilation.options,
          htmlWebpackPlugin: {
            tags: assetTags,
            files: files,
            options: options,
          },
        };
      },
    })
);

const cesiumEngine = path.dirname(require.resolve("@cesium/engine"));
const cesiumWidgets = path.dirname(require.resolve("@cesium/widgets"));
const cesiumVersion = JSON.parse(
  fs.readFileSync(path.join(cesiumWidgets, "package.json"), "utf-8")
).version;
const cesiumPath = path.join("cesium", cesiumVersion);
const cesiumTarget = path.join("assets", cesiumPath);

module.exports = {
  options: {
    root: __dirname,
    output: "../../../build/generated/src/main/resources/de/ii/ogcapi/html",
    mains: process.env.APP === undefined ? mains : undefined,
  },
  use: [
    airbnb(),
    react({
      html: {
        title: `FOO`,
      },
      publicPath: "",
      targets: {
        browsers: ["defaults"],
      },
    }),
    mocha(),
    copy({
      patterns: [
        { from: path.join(cesiumEngine, "Build/Workers"), to: path.join(cesiumTarget, "Workers") },
        { from: path.join(cesiumEngine, "Source/Assets"), to: path.join(cesiumTarget, "Assets") },
        {
          from: path.join(cesiumEngine, "Source/ThirdParty"),
          to: path.join(cesiumTarget, "ThirdParty"),
        },
        {
          from: path.join(cesiumEngine, "Source/Widget"),
          to: path.join(cesiumTarget, "Widgets/CesiumWidget"),
        },
        { from: path.join(cesiumWidgets, "Source"), to: path.join(cesiumTarget, "Widgets") },
      ],
    }),
    xtraplatform({
      lib: false,
      modulePrefixes: ["ogcapi", "@ogcapi"],
    }),
    (neutrino) => {
      neutrino.config.optimization.merge({
        runtimeChunk: {
          name: "common",
        },
        splitChunks: {
          chunks: "all",
          name: true,
          cacheGroups: {
            default: false,
            vendors: false,
            defaultVendors: false,
            common: {
              name: "common",
              minChunks: 2,
            },
          },
        },
      });

      neutrino.config.module.rule("font").test(/\.(eot|ttf|woff|woff2|ico)(\?v=\d+\.\d+\.\d+)?$/);

      neutrino.config.performance.maxEntrypointSize(2048000).maxAssetSize(1024000);

      //for cesium/c137
      neutrino.config.module
        .rule("importmeta")
        .test(new RegExp(`^.*?\\/\\.yarn\\/cache\\/@cesium-[a-z]*.*?$`))
        .use("im")
        .loader(require.resolve("@open-wc/webpack-import-meta-loader"));
      neutrino.config.module
        .rule("compile")
        .use("babel")
        .tap((options) => ({
          ...options,
          plugins: [...options.plugins, "@babel/plugin-proposal-nullish-coalescing-operator"],
        }));
      neutrino.config.module
        .rule("compile")
        .include.add(new RegExp(`^.*?\\/\\.yarn\\/cache\\/@cesium-[a-z]*.*?$`)); //TODO: add cache in addition to $$virtual in @xtraplatform/neutrino, use modulePrefixes

      neutrino.config.module
        .rule("compile")
        .include.add(new RegExp(`^.*?\\/\\.yarn\\/(cache|__virtual__)\\/react-i18next.*?$`)); // react-i18next
      neutrino.config.module
        .rule("compile")
        .include.add(new RegExp(`^.*?\\/\\.yarn\\/(cache|__virtual__)\\/i18next.*?$`)); // i18next

      //for dev server
      neutrino.config
        .plugin("env")
        .use(require.resolve("webpack/lib/EnvironmentPlugin"), [
          { APP: process.env.APP, CESIUM_PATH: cesiumPath },
        ]);
    },
  ],
};
