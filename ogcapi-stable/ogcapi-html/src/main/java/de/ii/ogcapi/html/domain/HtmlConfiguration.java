/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @buildingBlock HTML
 * @langEn ### Customization
 *     <p>The HTML encoding is implemented using [Mustache templates](https://mustache.github.io/)
 *     which can be overridden by the user. Any template may be overridden but doing so for
 *     non-empty templates is strongly discouraged since it will most certainly lead to issues on
 *     updates.
 *     <p>There are predefined empty templates prefixed with `custom-` that should cover most use
 *     cases:
 *     <p><code>
 * - `custom-head.mustache`: add something to the `<head>` element, e.g. CSS styles
 * - `custom-body-start.mustache`: add something at the start of the `<body>` element
 * - `custom-body-end.mustache`: add something at the end of the `<body>` element
 * - `custom-navbar-start.mustache`: add something at the left side of the navbar
 * - `custom-navbar-end.mustache`: add something at the right side of the navbar
 * - `custom-footer-start.mustache`: add something at the start of the footer
 * - `custom-footer-end.mustache`: add something at the end of footer
 * - `custom-footer-url.mustache`: add a link to the list at the right of the footer
 *     </code>
 *     <p>These templates have to reside in the
 *     [Store](../../application/20-configuration/10-store-new.md) as resources with type
 *     `html/templates`.
 *     <p>#### Custom assets
 *     <p>It is also possible to publish any custom files, e.g. CSS files that can then be included
 *     in `custom-head.mustache`. The files have to reside in the
 *     [Store](../../application/20-configuration/10-store-new.md) as resources with type
 *     `html/assets`.
 *     <p>For example the CSS file `resources/html/assets/my.css` could be included in
 *     `custom-head.mustache` with `<link href="{{urlPrefix}}/custom/assets/my.css"
 *     rel="stylesheet">`.
 *     <p>### Login Provider
 *     <p>For APIs with [restricted access](../README.md#access-control) using an [identity
 *     provider](../../application/20-configuration/40-auth.md) with login capabilities, the
 *     `loginProvider` option can be set to enable automatic redirects to the login form of the
 *     identity provider for restricted HTML pages. The logged-in user will also be shown on all
 *     HTML pages along with a logout button.
 *     <p>::: warning This functionality uses cookies to retain the login information when
 *     navigating between HTML pages. The cookies are neither processed nor passed on to any third
 *     party.
 *     <p>In regard to the European GDPR and the German TTDSG we would deem these cookies as
 *     technically required. That means if you publish an API with this functionality, you would be
 *     required to mention these cookies in the privacy policy. :::6
 *     <p>::: info If the identity provider uses `https` (which it should), this feature only works
 *     if the API is also published using `https`. The only exception is accessing an API on
 *     `localhost`. :::
 * @langDe ### Benutzerdefinierte Anpassungen
 *     <p>Die HTML-Ausgabe ist mittels [Mustache-Templates](https://mustache.github.io/)
 *     implementiert welche vom Nutzer überschrieben werden können. Jedes Template kann
 *     überschrieben werden, aber für nicht leere Templates wird stark davon abgeraten, da dies bei
 *     Updates häufig zu Problemen führt.
 *     <p>Es gibt vordefinierte leere Templates mit dem Präfix `custom-` welche die meisten
 *     Anwendungsfälle abdecken sollten:
 *     <p><code>
 * - `custom-head.mustache`: etwas zum `<head>` Element hinzufügen, z.B. CSS Styles
 * - `custom-body-start.mustache`: etwas am Anfang des `<body>` Elements hinzufügen
 * - `custom-body-end.mustache`: etwas am Ende des `<body>` Elements hinzufügen
 * - `custom-navbar-start.mustache`: etwas auf der linken Seite der Navbar hinzufügen
 * - `custom-navbar-end.mustache`: etwas auf der rechten Seite der Navbar hinzufügen
 * - `custom-footer-start.mustache`: etwas am Anfang des Footers hinzufügen
 * - `custom-footer-end.mustache`: etwas am Ende des Footers hinzufügen
 * - `custom-footer-url.mustache`: einen Link zur Liste auf der rechten Seite des Footers hinzufügen
 *     </code>
 *     <p>Diese Templates liegen im [Store](../../application/20-configuration/10-store-new.md) als
 *     Ressourcen mit Typ `html/templates`.
 *     <p>#### Benutzerdefinierte Dateien
 *     <p>Es können auch beliebige Dateien veröffentlicht werden, z.B. CSS Dateien, die dann in
 *     `custom-head.mustache` eingebunden werden. Diese Dateien liegen im
 *     [Store](../../application/20-configuration/10-store-new.md) als Ressourcen mit Typ
 *     `html/assets`.
 *     <p>Zum Beispiel könnte die CSS-Datei `resources/html/assets/my.css` in `custom-head.mustache`
 *     eingebunden werden mit `<link href="{{urlPrefix}}/custom/assets/my.css" rel="stylesheet">`.
 *     <p>### Login Provider
 *     <p>Für APIs mit [beschränktem Zugriff](../README.md#access-control) die einen
 *     [Identity-Provider](../../application/20-configuration/40-auth.md) mit Login-Fähigkeiten
 *     verwenden, kann die Option `loginProvider` gesetzt werden, um für abgesicherte HTML-Seiten
 *     automatische Redirects zum Login-Formular des Identity-Providers zu aktivieren. Der
 *     eingeloggte User wird auf allen HTML-Seiten angezeigt ebenso wie ein Logout-Button.
 *     <p>::: warning Diese Funktionalität verwendet Cookies um die Login-Information beim
 *     Navigieren zwischen HTML-Seiten zu bewahren. Diese Cookies werden weder verarbeitet noch an
 *     Dritte weitergegeben.
 *     <p>In Hinsicht auf die europäische DSGVO und das deutsche TTDSG würden wir diese Cookies als
 *     technisch notwendig erachten. Das heißt wenn eine API mit dieser Funktionalität
 *     veröffentlicht wird, müssen diese Cookies in der Datenschutzerklärung erwähnt werden. :::
 *     <p>::: info Wenn der Identity-Provider `https` verwendet (was er sollte), funktioniert dieses
 *     Feature nur, wenn die API ebenfalls mit `https` veröffentlich wird. Die einzige Ausnahme ist
 *     der Zugriff auf die API mittels `localhost`. :::
 * @examplesEn Example of the specifications in the configuration file (from the API for
 *     [Topographic data in Daraa, Syria](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: HTML
 *   enabled: true
 *   noIndexEnabled: true
 *   schemaOrgEnabled: true
 *   defaultStyle: topographic
 * ```
 *     </code>
 *     <p>Example of the specifications in the configuration file (from the API for Vineyards in
 *     Rhineland-Palatinate](https://demo.ldproxy.net/vineyards)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: HTML
 *   enabled: true
 *   noIndexEnabled: false
 *   schemaOrgEnabled: true
 *   collectionDescriptionsInOverview: true
 *   legalName: Legal notice
 *   legalUrl: https://www.interactive-instruments.de/en/about/impressum/
 *   privacyName: Privacy notice
 *   privacyUrl: https://www.interactive-instruments.de/en/about/datenschutzerklarung/
 *   basemapUrl: https://sg.geodatenzentrum.de/wmts_topplus_open/tile/1.0.0/web_grau/default/WEBMERCATOR/{z}/{y}/{x}.png
 *   basemapAttribution: '&copy; <a href="https://www.bkg.bund.de" target="_new">Bundesamt f&uuml;r Kartographie und Geod&auml;sie</a> (2020), <a href="https://sg.geodatenzentrum.de/web_public/Datenquellen_TopPlus_Open.pdf" target="_new">Datenquellen</a>'
 *   defaultStyle: default
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei (aus der API für [Topographische
 *     Daten in Daraa, Syrien](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: HTML
 *   enabled: true
 *   noIndexEnabled: true
 *   schemaOrgEnabled: true
 *   defaultStyle: topographic
 * ```
 *     </code>
 *     <p>Beispiel für die Angaben in der Konfigurationsdatei (aus der API für [Weinlagen in
 *     Rheinland-Pfalz](https://demo.ldproxy.net/vineyards)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: HTML
 *   enabled: true
 *   noIndexEnabled: false
 *   schemaOrgEnabled: true
 *   collectionDescriptionsInOverview: true
 *   legalName: Legal notice
 *   legalUrl: https://www.interactive-instruments.de/en/about/impressum/
 *   privacyName: Privacy notice
 *   privacyUrl: https://www.interactive-instruments.de/en/about/datenschutzerklarung/
 *   basemapUrl: https://sg.geodatenzentrum.de/wmts_topplus_open/tile/1.0.0/web_grau/default/WEBMERCATOR/{z}/{y}/{x}.png
 *   basemapAttribution: '&copy; <a href="https://www.bkg.bund.de" target="_new">Bundesamt f&uuml;r Kartographie und Geod&auml;sie</a> (2020), <a href="https://sg.geodatenzentrum.de/web_public/Datenquellen_TopPlus_Open.pdf" target="_new">Datenquellen</a>'
 *   defaultStyle: default
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "HTML")
@JsonDeserialize(builder = ImmutableHtmlConfiguration.Builder.class)
public interface HtmlConfiguration extends ExtensionConfiguration {
  Logger LOGGER = LoggerFactory.getLogger(HtmlConfiguration.class);

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @langEn Set `noIndex` for all sites to prevent search engines from indexing.
   * @langDe Steuert, ob in allen Seiten `noIndex` gesetzt wird und Suchmaschinen angezeigt wird,
   *     dass sie die Seiten nicht indizieren sollen.
   * @default true
   */
  @Nullable
  Boolean getNoIndexEnabled();

  /**
   * @langEn Enable [schema.org](https://schema.org) annotations for all sites, which are used e.g.
   *     by search engines. The annotations are embedded as JSON-LD.
   * @langDe Steuert, ob in die HTML-Ausgabe schema.org-Annotationen, z.B. für Suchmaschinen,
   *     eingebettet sein sollen, sofern. Die Annotationen werden im Format JSON-LD eingebettet.
   * @default true
   */
  @Nullable
  Boolean getSchemaOrgEnabled();

  /**
   * @langEn Show collection descriptions in the HTML representation of the *Feature Collections*
   *     resource.
   * @langDe Steuert, ob in der HTML-Ausgabe der Feature-Collections-Ressource für jede Collection
   *     die Beschreibung ausgegeben werden soll.
   * @default false
   */
  @Nullable
  Boolean getCollectionDescriptionsInOverview();

  /**
   * @langEn Suppress collections without items in the HTML representation of the *Feature
   *     Collections* resource.
   * @langDe Steuert, ob in der HTML-Ausgabe der Feature-Collections-Ressource Collections ohne
   *     Daten unterdrückt sein sollen.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getSuppressEmptyCollectionsInOverview();

  @Nullable
  Boolean getSendEtags();

  /**
   * @langEn Label for optional legal notice link on every site.
   * @langDe Auf jeder HTML-Seite kann ein ggf. rechtlich erforderlicher Link zu einem Impressum
   *     angezeigt werden. Diese Eigenschaft spezfiziert den anzuzeigenden Text.
   * @default Legal notice
   */
  @Nullable
  String getLegalName();

  /**
   * @langEn URL for optional legal notice link on every site.
   * @langDe Auf jeder HTML-Seite kann ein ggf. rechtlich erforderlicher Link zu einem Impressum
   *     angezeigt werden. Diese Eigenschaft spezfiziert die URL des Links.
   * @default null
   */
  @Nullable
  String getLegalUrl();

  /**
   * @langEn Label for optional privacy notice link on every site.
   * @langDe Auf jeder HTML-Seite kann ein ggf. rechtlich erforderlicher Link zu einer
   *     Datenschutzerklärung angezeigt werden. Diese Eigenschaft spezfiziert den anzuzeigenden
   *     Text.
   * @default Privacy notice
   */
  @Nullable
  String getPrivacyName();

  /**
   * @langEn URL for optional privacy notice link on every site.
   * @langDe Auf jeder HTML-Seite kann ein ggf. rechtlich erforderlicher Link zu einer
   *     Datenschutzerklärung angezeigt werden. Diese Eigenschaft spezfiziert die URL des Links.
   * @default null
   */
  @Nullable
  String getPrivacyUrl();

  /**
   * @langEn A default style in the style repository that is used in maps in the HTML representation
   *     of the feature and tile resources. If `NONE`, a simple wireframe style will be used with
   *     OpenStreetMap as a basemap, if the map client is MapLibre; for Cesium, the default 3D Tiles
   *     styling will be used with the basemap. If the value is not `NONE` and the map client is
   *     MapLibre, the API landing page (or the collection page) will also contain a link to a web
   *     map with the style for the dataset (or the collection).
   * @langDe Ein Style im Style-Repository, der standardmäßig in Karten mit Feature- und
   *     Tile-Ressourcen verwendet werden soll. Bei `NONE` wird ein einfacher Style mit
   *     OpenStreetMap als Basiskarte verwendet, wenn MapLibre der Map-Client ist; bei Cesium wird
   *     das Standard-3D-Tiles-Styling verwendet. Wenn der Wert nicht `NONE` ist und MapLibre der
   *     Map_Client ist, enthält die "Landing Page" bzw. die "Feature Collection" auch einen Link zu
   *     einer Webkarte mit dem Stil für den Datensatz bzw. die Feature Collection. Der Style sollte
   *     alle Daten abdecken und muss im Format Mapbox Style verfügbar sein.
   * @default NONE
   */
  @Nullable
  String getDefaultStyle();

  /**
   * @langEn URL template for background map tiles.
   * @langDe Das URL-Template für die Kacheln einer Hintergrundkarte.
   * @default https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png
   */
  @Nullable
  String getBasemapUrl();

  /**
   * @langEn Source attribution for background map.
   * @langDe Die Quellenangabe für die Hintergrundkarte.
   * @default &copy; <a href='http://osm.org/copyright'>OpenStreetMap</a> contributors
   */
  @Nullable
  String getBasemapAttribution();

  /**
   * @langEn Additional text shown in footer of every site.
   * @langDe Zusätzlicher Text, der auf jeder HTML-Seite im Footer angezeigt wird.
   * @default null
   */
  @Nullable
  String getFooterText();

  /**
   * @langEn Option to enable automatic redirects to the login form of an identity provider. The
   *     value is the id of a provider with login capabilities in the [global
   *     configuration](../../application/20-configuration/40-auth.md). Also see [Login
   *     Provider](#login-provider).
   * @langDe Option um automatische Redirects zum Login-Formular eines Identity-Providers zu
   *     aktivieren. Der Wert ist die Id eines Provider mit Login-Fähigkeiten in der [globalen
   *     Konfiguration](../../application/20-configuration/40-auth.md). Siehe auch [Login
   *     Provider](#login-provider).
   * @default null
   */
  @Nullable
  String getLoginProvider();

  /**
   * @langEn Use a different URL for the home link on every HTML page.
   * @langDe Verwendung einer anderen URL für den Home-Link auf jeder HTML-Seite.
   * @default null
   */
  @Nullable
  String getHomeUrl();

  @Override
  default Builder getBuilder() {
    return new ImmutableHtmlConfiguration.Builder();
  }

  default String getStyle(
      Optional<String> requestedStyle,
      Optional<String> collectionId,
      String serviceUrl,
      MapClient.Type mapClientType) {
    String f =
        mapClientType == Type.MAP_LIBRE ? "mbs" : mapClientType == Type.CESIUM ? "3dtiles" : null;
    URL styleUrl = null;

    if (Objects.nonNull(f)) {
      styleUrl =
          requestedStyle
              .map(
                  s ->
                      s.equals("DEFAULT")
                          ? Objects.requireNonNullElse(getDefaultStyle(), "NONE")
                          : s)
              .filter(s -> !s.equals("NONE"))
              .map(
                  s -> {
                    try {
                      URI serviceUri = new URI(serviceUrl);
                      String path =
                          collectionId.isEmpty()
                              ? String.format("%s/styles/%s", serviceUri.getPath(), s)
                              : String.format(
                                  "%s/collections/%s/styles/%s",
                                  serviceUri.getPath(), collectionId.get(), s);
                      String query = String.format("f=%s", f);
                      return new URI(
                              serviceUri.getScheme(), serviceUri.getAuthority(), path, query, null)
                          .toURL();
                    } catch (URISyntaxException | MalformedURLException e) {
                      return null;
                    }
                  })
              .orElse(null);
    }

    // Check that the style exists
    if (Objects.isNull(styleUrl)) {
      return null;
    }

    // TODO we currently test for the availability of the style using a HTTP request to
    //      avoid a dependency to STYLES. Once OGC API Styles is stable, we should consider to
    //      separate the StyleRepository from the endpoints. The StyleRepository could be part
    //      of FOUNDATION or its own module
    try {
      HttpURLConnection http = (HttpURLConnection) styleUrl.openConnection();
      http.setRequestMethod("HEAD");
      if (http.getResponseCode() == 404 && collectionId.isPresent()) {
        // Try fallback to the dataset style, if we have a collection style
        return getStyle(requestedStyle, Optional.empty(), serviceUrl, mapClientType);
      } else if (http.getResponseCode() != 200) {
        LOGGER.error(
            "Could not access style '{}', falling back to style 'NONE'. Response code: '{}'. Message: {}",
            styleUrl,
            http.getResponseCode(),
            http.getResponseMessage());
        return null;
      }
      http.disconnect();
    } catch (Exception e) {
      LOGGER.error(
          "Could not access style '{}', falling back to style 'NONE'. Reason: {}",
          styleUrl,
          e.getMessage());
      if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace: ", e);
      }
      return null;
    }

    return styleUrl.toString();
  }
}
