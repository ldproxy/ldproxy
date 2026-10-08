import $ from "jquery";
import "bootstrap";

$(() => {
  $('[data-toggle="tooltip"]').tooltip();
});

globalThis.$ = $;
