/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import FilterEditor from "./components/FilterEditor";
import SortingEditor from "./components/SortingEditor";
import MapLibre from "./components/MapLibre";

// TODO: enable other apps for dev server
const Component = process.env.APP === "maplibre" ? MapLibre : FilterEditor;
const Component2 = process.env.APP === "maplibre" ? MapLibre : SortingEditor;

ReactDOM.render(
  <React.StrictMode>
    <Component />
    <Component2 />
  </React.StrictMode>,
  document.getElementById("root")
);
