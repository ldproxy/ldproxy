import React from "react";
import { createRoot } from "react-dom/client";
import FilterEditor from "./components/FilterEditor";
import SortingEditor from "./components/SortingEditor";
import MapLibre from "@xtramaps/web-map-maplibre-react";
import "@xtramaps/web-map-maplibre-react/dist/index.css";

// TODO: enable other apps for dev server
const Component = process.env.APP === "maplibre" ? MapLibre : FilterEditor;
const Component2 = process.env.APP === "maplibre" ? MapLibre : SortingEditor;

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Component />
    <Component2 />
  </React.StrictMode>,
);
