import { defineUserConfig, defaultTheme } from "vuepress";
import { docsearchPlugin } from "@vuepress/plugin-docsearch";
import { mdEnhancePlugin } from "vuepress-plugin-md-enhance";
import { themeDocs, createGroups } from "vuepress-plugin-theme-extensions";

const sidebar = (lang) => {
  const group = createGroups(__dirname);
  const prefix = lang == "en" ? "" : "de/";

  return [
    group({ en: "Getting Started", de: "Erste Schritte" }[lang], prefix, { ignoreReadme: true }),
    group({ en: "Application", de: "Applikation" }[lang], prefix + "application", {
      children: [
        group(
          { en: "Configuration", de: "Konfiguration" }[lang],
          prefix + "application/20-configuration",
          {
            collapsible: true,
            headerReadme: true,
          }
        ),
      ],
    }),
    group({ en: "APIs", de: "APIs" }[lang], prefix + "services", {
      ignoreReadme: true,
      children: [
        group({ en: "OGC API", de: "OGC API" }[lang], prefix + "services", {
          headerReadme: true,
          children: [
            group(
              { en: "Building Blocks", de: "Bausteine" }[lang],
              prefix + "services/building-blocks",
              { collapsible: true, headerReadme: true }
            ),
          ],
        }),
      ],
    }),
    group({ en: "Data Providers", de: "Daten-Provider" }[lang], prefix + "providers", {
      children: [
        group({ en: "Features", de: "Features" }[lang], prefix + "providers/feature", {
          headerReadme: true,
          children: [
            group(
              { en: "Extensions", de: "Erweiterungen" }[lang],
              prefix + "providers/feature/90-extensions",
              { collapsible: true, headerReadme: true }
            ),
          ],
        }),
        group({ en: "Tiles", de: "Tiles" }[lang], prefix + "providers/tile", {
          headerReadme: true,
        }),
        group({ en: "3D Tiles", de: "3D Tiles" }[lang], prefix + "providers/tile3d", {
          headerReadme: true,
        }),
      ],
    }),
    group({ en: "Auxiliaries", de: "Zubehör" }[lang], prefix + "auxiliaries", {
      ignoreReadme: true,
    }),
    group({ en: "Tools", de: "Tools" }[lang], prefix + "tools", {
      children: [
        group({ en: "Editor", de: "Editor" }[lang], prefix + "tools/editor", {
          headerReadme: true,
        }),
        group({ en: "xtracfg", de: "xtracfg" }[lang], prefix + "tools/xtracfg", {
          collapsible: true,
          headerReadme: true,
        }),
        group({ en: "xtractl", de: "xtractl" }[lang], prefix + "tools/xtractl", {
          collapsible: true,
          headerReadme: true,
        }),
        group({ en: "xtratiler", de: "xtratiler" }[lang], prefix + "tools/xtratiler", {
          collapsible: true,
          headerReadme: true,
        }),
      ],
    }),
    group({ en: "References", de: "Referenzen" }[lang], prefix + "references"),
  ];
};

const navbar = (lang) => {
  const versions = {
    "v4.x": "https://docs.ldproxy.net",
    "v3.x": "https://v3.docs.ldproxy.net",
    next: "https://next.docs.ldproxy.net",
  };
  const active = process.env.DOCS_VERSION;

  if (!Object.hasOwn(versions, active)) {
    console.error(
      `[ERROR] DOCS_VERSION is not set or contains an unknown version [${active}]. Valid versions are [${Object.keys(
        versions
      )}].`
    );
    process.exit(1);
  }

  return [
    {
      text: { en: "Documentation", de: "Dokumentation" }[lang],
      link: "/",
      activeMatch: "/",
    },
    {
      text: "Demo",
      link: "https://demo.ldproxy.net",
    },
    {
      text: active,
      children: Object.keys(versions).map((version) => ({
        text: version,
        link: version === active ? "" : versions[version],
        activeMatch: version === active ? "/" : undefined,
      })),
      group: "start",
    },
  ];
};

export default defineUserConfig({
  base: "/",
  locales: {
    "/": {
      lang: "en-US",
      title: "ldproxy",
      description: "ldproxy documentation",
    },
    "/de/": {
      lang: "de-DE",
      title: "ldproxy",
      description: "ldproxy Dokumentation",
    },
  },
  plugins: [
    docsearchPlugin({
      appId: "TNOB61BGZX",
      apiKey: "75d89f7da0b2ccdd1078b38607739c2b",
      indexName: "ldproxy",
    }),
    mdEnhancePlugin({
      //container: true,
      alert: true,
      gfm: true,
      hint: true,
      mermaid: true,
      checkLinks: {status: "always"},
    }),
    /*['@vuepress/plugin-shiki', {
      //theme: 'material-lighter',
      theme: 'hc-light',
    }],*/
  ],
  markdown: {
    code: {
      lineNumbers: false,
    },
  },
  theme: themeDocs({
    navbar: true,
    tableRowHeight: true,
    onThisPage: true,
    repo: "interactive-instruments/ldproxy",
    //logo: 'https://vuejs.org/images/logo.png',
    editLink: false,
    colorModeSwitch: false,
    themePlugins: {
      //prismjs: false,
    },
    locales: {
      "/": {
        selectLanguageText: "EN",
        selectLanguageName: "English",
        navbar: navbar("en"),
        sidebar: sidebar("en"),
        themeExtensions: {
          legalNoticeUrl: "https://www.interactive-instruments.de/en/about/impressum/",
          privacyNoticeUrl: "https://www.interactive-instruments.de/en/about/datenschutzerklarung/",
        },
      },
      "/de/": {
        selectLanguageText: "DE",
        selectLanguageName: "Deutsch",
        navbar: navbar("de"),
        sidebar: sidebar("de"),
        themeExtensions: {
          onThisPageLabel: "Auf dieser Seite",
          legalNoticeLabel: "Impressum",
          legalNoticeUrl: "https://www.interactive-instruments.de/de/about/impressum/",
          privacyNoticeLabel: "Datenschutzerklärung",
          privacyNoticeUrl: "https://www.interactive-instruments.de/de/about/datenschutzerklarung/",
        },
      },
    },
  }),
  host: "127.0.0.1",
});
