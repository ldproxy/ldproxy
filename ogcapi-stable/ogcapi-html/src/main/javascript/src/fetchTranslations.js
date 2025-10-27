import { translations } from "./i18nTranslations";

export function fetchTranslations(lang) {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(translations[lang]);
    });
  });
}
