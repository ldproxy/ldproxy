import "bootstrap/dist/css/bootstrap.min.css";
import "./style.css";
// ?no-inline: the favicon must be emitted as a file, since ogcapi-html-mustache.js links it by
// name; the tiny .ico would otherwise be inlined as a data URI and never reach the bundle.
import "./favicon.ico?no-inline";
