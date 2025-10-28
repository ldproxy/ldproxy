import i18n from "i18next";
import { initReactI18next } from "react-i18next";

// eslint-disable-next-line no-undef, no-underscore-dangle
const { language } = globalThis._sortingfilter;

i18n.use(initReactI18next).init({
  lng: language,
  fallbackLng: "en",
  interpolation: { escapeValue: false },
  resources: {},
});

export default i18n;
