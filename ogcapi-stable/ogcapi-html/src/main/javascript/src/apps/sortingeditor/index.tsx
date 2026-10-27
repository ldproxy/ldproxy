import React from "react";
import { createRoot } from "react-dom/client";
import SortingEditor from "../../components/SortingEditor";

if (globalThis._sortingfilter && globalThis._sortingfilter.sortingcontainer) {
  createRoot(document.getElementById(globalThis._sortingfilter.sortingcontainer)!).render(
    <React.StrictMode>
      <SortingEditor />
    </React.StrictMode>,
  );
}
