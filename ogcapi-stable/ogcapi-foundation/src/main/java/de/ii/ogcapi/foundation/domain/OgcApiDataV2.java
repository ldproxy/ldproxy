/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.docs.DocFile;
import de.ii.xtraplatform.docs.DocStep;
import de.ii.xtraplatform.docs.DocStep.Step;
import de.ii.xtraplatform.docs.DocTable;
import de.ii.xtraplatform.docs.DocTable.ColumnSet;
import de.ii.xtraplatform.docs.DocVar;
import de.ii.xtraplatform.entities.domain.EntityDataBuilder;
import de.ii.xtraplatform.entities.domain.EntityDataDefaults;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.entities.domain.maptobuilder.BuildableMap;
import de.ii.xtraplatform.services.domain.ServiceData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @langEn # OGC API
 *     <p>Each API represents a deployment of a single OGC Web API, i.e., an API that implements
 *     conformance classes from OGC API standards.
 *     <p>## General rules
 *     <p>### Response encoding
 *     <p>For operations that return a response, the encoding is chosen using standard HTTP content
 *     negotiation with `Accept` headers.
 *     <p>GET operations that support more than one encoding additionally support the query
 *     parameter `f`, which allows to explicitly choose the encoding and override the result of the
 *     content negotiation. The supported encodings depend on the affected resource and the
 *     configuration.
 *     <p>### Response profiles
 *     <p>Some resources may be represented in with variations in the same format (media type),
 *     depending on the intended use of the representation.
 *     <p>These variations are called "profiles". A profile is defined not to alter the semantics of
 *     the resource representation itself, but to allow clients to learn about additional semantics
 *     (constraints, conventions, extensions) that are associated with the resource representation,
 *     in addition to those defined by the media type and possibly other mechanisms. See [IETF RFC
 *     6906](https://www.rfc-editor.org/rfc/rfc6906.html) for details.
 *     <p>To request one or more profiles, a query parameter "profile" can be used on resources that
 *     are associated with profiles (currently: feature and schema resources).
 *     <p>The supported profiles depend on the resource type and, in some cases, on the media type.
 *     <p>Profiles are organized in profile sets. Whether a profile set applies to a resource
 *     representation, depends on the resource and potentially the media type of the representation.
 *     From each applicable profile set, at most one profile applies to the resource representation.
 *     <p>The profiles are negotiated as follows:
 *     <p><code>
 * - If a profile is specified via the "profile" query parameter and the profile is applicable to the resource, the profile is selected for the specific profile set;
 * - If no profile is explicitly requested for an applicable profile set, a default profile from the profile set is selected. Depending on the nature of the profile set, the default profiles can be specified either in the building block of the resource and/or in the building block of the format via the `defaultProfiles` option. A default specified for a format overrides the default profile specified for the resource.
 * </code>
 *     <p>### Response language
 *     <p>For operations that return a response, the language for linguistic texts is chosen using
 *     standard HTTP content negotiation with `Accept-Language` headers.
 *     <p>If enabled in [Common Core](building-blocks/common_core.md), GET operations additionally
 *     support the query parameter `lang`, which allows to explicitly choose the language and
 *     override the result of the content negotiation. The supported languages depend on the
 *     affected resource and the configuration. Support for multilingualism is currently limited.
 *     There are four possible sources for linguistic texts:
 *     <p><code>
 * - Static texts: For example link labels or static texts in HTML representations. Currently the languages English (`en`) and German (`de`) are supported.
 * - Texts contained in the data: Currently not supported.
 * - Texts set in the configuration: Currently not supported.
 * - Error messages: These are always in english, the messages are currently hard-coded.
 * </code>
 *     <p>### Resource paths
 *     <p>All resource paths in this documentation are relative to the base URI of the API. For
 *     example given the base URI `https://example.com/pfad/zu/apis/{apiId}` and the relative
 *     resource path `collections`, the full path would be
 *     `https://example.com/pfad/zu/apis/{apiId}/collections`.
 * @langDe # OGC API
 *     <p>Jede API stellt eine OGC Web API bereit, d.h., eine API, die Konformitätsklassen aus
 *     OGC-API-Standards implementiert.
 *     <p>## Grundsätzliche Regeln
 *     <p>### Auswahl des Antwortformats
 *     <p>Bei Operationen, die eine Antwort zurückliefern, wird das Format nach den
 *     Standard-HTTP-Regeln standardmäßig über Content-Negotiation und den `Accept`-Header
 *     ermittelt.
 *     <p>Alle GET-Operationen mit mehr als einem Ausgabeformat unterstützen zusätzlich den
 *     Query-Parameter `f`. Über diesen Parameter kann das Ausgabeformat der Antwort auch direkt
 *     ausgewählt werden. Wenn kein Wert angegeben wird, gelten die Standard-HTTP-Regeln, d.h. der
 *     `Accept`-Header wird zur Bestimmung des Formats verwendet. Die unterstützten Formate hängen
 *     von der Ressource und von der API-Konfiguration ab.
 *     <p>### Auswahl von Profilen
 *     <p>Einige Ressourcen können in verschiedenen Varianten desselben Formats (Media Types)
 *     repräsentiert werden, je nach Verwendungszweck der Antwort.
 *     <p>Diese Variationen werden als "Profile" bezeichnet. Ein Profil ändert nicht die Semantik
 *     der Ressourcedarstellung ansich, sondern ermöglicht den Clients zusätzliche Semantiken
 *     (Einschränkungen, Konventionen, Erweiterungen) zu erfahren, die mit der Ressourcendarstellung
 *     verbunden sind, zusätzlich zu denen, die durch den Medientyp und möglicherweise andere
 *     Mechanismen definiert sind. Siehe [IETF RFC
 *     6906](https://www.rfc-editor.org/rfc/rfc6906.html) für Details.
 *     <p>Um ein oder mehrere Profile anzufordern, kann ein Abfrageparameter "profile" für
 *     Ressourcen verwendet werden, die mit Profilen verbunden sind (derzeit: Feature- und
 *     Schemaressourcen).
 *     <p>Die unterstützten Profile hängen vom Ressourcentyp und in einigen Fällen auch vom
 *     Media-Type ab.
 *     <p>Profile sind in Profilsätzen organisiert. Ob ein Profilsatz für eine Ressourcendarstellung
 *     gilt, hängt von der Ressource und möglicherweise vom Media-Type der Darstellung ab. Von jedem
 *     anwendbaren Profilsatz wird maximal ein Profil bei der Darstellung der Ressource angewendet.
 *     <p>Die Profile werden wie folgt ausgehandelt:
 *     <p><code>
 * - Wenn ein Profil über den Abfrageparameter "profile" angegeben wird und das Profil auf die Ressource anwendbar ist, wird das Profil für das spezifische Profilset ausgewählt;
 * - Wird für einen anwendbaren Profilsatz kein Profil explizit angefordert, wird das Standardprofil für dem Profilsatz ausgewählt. Je nach Art des Profilsatzes wird das Standardprofile entweder im Baustein der Ressource und/oder im Baustein des Formats über die Option `defaultProfiles` angegeben. Ein für ein Format angegebenes Standardprofil hat Vorrang vor dem für die Ressource angegebenen Standardprofil.
 * </code>
 *     <p>### Auswahl der Antwortsprache
 *     <p>Bei Operationen, die eine Antwort zurückliefern, wird die verwendete Sprache bei
 *     linguistischen Texten nach den Standard-HTTP-Regeln standardmäßig über Content-Negotiation
 *     und den `Accept-Language`-Header ermittelt.
 *     <p>Sofern die entsprechende Option im Baustein "Common Core" aktiviert ist, unterstützen alle
 *     GET-Operationen zusätzlich den Query-Parameter `lang`. Über diesen Parameter kann die Sprache
 *     auch direkt ausgewählt werden. Wenn kein Wert angegeben wird, gelten die
 *     Standard-HTTP-Regeln, wie oben beschrieben. Die erlaubten Werte hängen von der Ressource und
 *     von der API-Konfiguration ab. Die Unterstüzung für Mehrsprachigkeit ist derzeit begrenzt. Es
 *     gibt vier Arten von Quellen für Texte:
 *     <p><code>
 * - Texte zu festen Elementen der API: Diese werden von ldproxy erzeugt, z.B. die Texte der Titel von Links oder feste Textbausteine in der HTML-Ausgabe. Derzeit werden die Sprachen "Deutsch" (de) und "Englisch" (en) unterstützt.
 * - Texte aus Attributen in den Daten: Hier gibt es noch keine Unterstützung, wie die Rückgabe bei mehrsprachigen Daten in Abhängigkeit von der Ausgabesprache gesteuert werden kann.
 * - Texte aus der API-Konfiguration, insbesondere zum Datenschema: Hier gibt es noch keine Unterstützung, wie die Rückgabe bei mehrsprachigen Daten in Abhängigkeit von der Ausgabesprache gesteuert werden kann.
 * - Fehlermeldungen der API: Diese sind immer in Englisch, die Meldungen sind aktuell Bestandteil des Codes.
 * </code>
 *     <p>### Pfadangaben
 *     <p>Alle Pfadangaben in dieser Dokumentation sind relativ zur Basis-URI der API. Ist dies zum
 *     Beispiel `https://example.com/pfad/zu/apis/{apiId}` und lautet der relative Pfad einer
 *     Ressource `collections` dann ist die URI der Ressource
 *     `https://example.com/pfad/zu/apis/{apiId}/collections`.
 * @langEn ## Configuration
 *     <p>Details regarding the API building blocks can be found [here](building-blocks/README.md),
 *     see `api` in the table below.
 *     <p>{@docTable:properties}
 * @langDe ## Konfiguration
 *     <p>Informationen zu den einzelnen API-Bausteinen finden Sie
 *     [hier](building-blocks/README.md), siehe `api` in der nachfolgenden Tabelle.
 *     <p>{@docTable:properties}
 * @langEn ### Collection
 *     <p>Every collection corresponds to a feature type defined in the feature provider (only
 *     *Feature Collections* are currently supported).
 *     <p>{@docTable:collectionProperties}
 * @langDe ### Collection
 *     <p>Jedes Collection-Objekt beschreibt eine Objektart aus einem Feature Provider (derzeit
 *     werden nur Feature Collections von ldproxy unterstützt). Es setzt sich aus den folgenden
 *     Eigenschaften zusammen:
 *     <p>{@docTable:collectionProperties}
 * @langEn ### Building Blocks
 *     <p>Building blocks might be configured for the API or for single collections. The final
 *     configuration is formed by merging the following sources in this order:
 *     <p><code>
 * - The building block defaults, see [Building Blocks](building-blocks/README.md).
 * - Optional deployment defaults in the `defaults` directory.
 * - API level configuration.
 * - Collection level configuration.
 * - Optional deployment overrides in the `overrides` directory.
 * </code>
 *     <p>
 * @langDe ### Bausteine
 *     <p>Ein Array dieser Baustein-Konfigurationen steht auf der Ebene der gesamten API und für
 *     jede Collection zur Verfügung. Die jeweils gültige Konfiguration ergibt sich aus der
 *     Priorisierung:
 *     <p><code>
 * - Ist nichts angegeben, dann gelten die im ldproxy-Code vordefinierten Standardwerte. Diese sind bei den jeweiligen [Bausteinen](building-blocks/README.md) spezifiziert.
 * - Diese systemseitigen Standardwerte können von den Angaben im Verzeichnis `defaults` überschrieben werden.
 * - Diese deploymentweiten Standardwerte können von den Angaben in der API-Definition auf Ebene der API überschrieben werden.
 * - Diese API-weiten Standardwerte können bei den Collection-Ressourcen und untergeordneten Ressourcen von den Angaben in der API-Definition auf Ebene der Collection überschrieben werden.
 * - Diese Werte können durch Angaben im Verzeichnis `overrides` überschrieben werden.
 * </code>
 *     <p>
 * @langEn ### Metadata
 *     <p>{@docVar:metadata}
 *     <p>{@docTable:metadataProperties}
 * @langDe ### Metadaten
 *     <p>{@docVar:metadata}
 *     <p>{@docTable:metadataProperties}
 * @langEn ### External document
 *     <p>{@docVar:externalDocument}
 *     <p>{@docTable:externalDocumentProperties}
 * @langDe ### Externes Dokument
 *     <p>{@docVar:externalDocument}
 *     <p>{@docTable:externalDocumentProperties}
 * @langEn ### Default extent
 *     <p>{@docVar:extent}
 *     <p>{@docTable:extentProperties}
 * @langDe ### Ausdehnung der Daten
 *     <p>{@docVar:extent}
 *     <p>{@docTable:extentProperties}
 * @langAll ### Caching
 *     <p>{@docVar:caching}
 *     <p>{@docTable:cachingProperties}
 * @langEn ### Access Control
 *     <p>{@docVar:security}
 *     <p>#### Configuration
 *     <p>{@docTable:securityProperties}
 *     <p>{@docVar:policies}
 *     <p>{@docTable:policies}
 * @langDe ### Access Control
 *     <p>{@docVar:security}
 *     <p>#### Konfiguration
 *     <p>{@docTable:securityProperties}
 *     <p>{@docVar:policies}
 *     <p>{@docTable:policies}
 * @langEn ### Examples
 *     <p>See the [API
 *     configuration](https://github.com/interactive-instruments/ldproxy/blob/master/demo/vineyards/store/entities/services/vineyards.yml)
 *     of the API [Vineyards in Rhineland-Palatinate, Germany](https://demo.ldproxy.net/vineyards).
 * @langDe ### Beispiele
 *     <p>Als Beispiel siehe die
 *     [API-Konfiguration](https://github.com/interactive-instruments/ldproxy/blob/master/demo/vineyards/store/entities/services/vineyards.yml)
 *     der API [Weinlagen in Rheinland-Pfalz](https://demo.ldproxy.net/vineyards).
 * @langEn ## Storage
 *     <p>API configurations reside in the [Store](../application/20-configuration/10-store-new.md)
 *     as entities with type `services`.
 * @langDe ## Speicherung
 *     <p>API-Konfigurationen liegen im [Store](../application/20-configuration/10-store-new.md) als
 *     Entities mit Typ `services`.
 * @ref:cfgProperties {@link de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2}
 * @ref:cfgProperties:collection {@link
 *     de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi}
 * @ref:metadata {@link de.ii.ogcapi.foundation.domain.ApiMetadata}
 * @ref:metadataProperties {@link de.ii.ogcapi.foundation.domain.ImmutableApiMetadata}
 * @ref:externalDocument {@link de.ii.ogcapi.foundation.domain.ExternalDocumentation}
 * @ref:externalDocumentProperties {@link
 *     de.ii.ogcapi.foundation.domain.ImmutableExternalDocumentation}
 * @ref:extent {@link de.ii.ogcapi.foundation.domain.CollectionExtent}
 * @ref:extentProperties {@link de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent}
 * @ref:caching {@link de.ii.ogcapi.foundation.domain.CachingConfiguration}
 * @ref:cachingProperties {@link de.ii.ogcapi.foundation.domain.ImmutableCaching}
 * @ref:security {@link de.ii.ogcapi.foundation.domain.ApiSecurity}
 * @ref:securityProperties {@link de.ii.ogcapi.foundation.domain.ImmutableApiSecurity}
 * @ref:policies {@link de.ii.ogcapi.foundation.domain.ApiSecurity.Policies}
 * @ref:policiesTable {@link de.ii.ogcapi.foundation.domain.ImmutablePolicies}
 */
@DocFile(
    path = "services",
    name = "README.md",
    tables = {
      @DocTable(
          name = "properties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:cfgProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "collectionProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:cfgProperties:collection}"),
            @DocStep(type = Step.JSON_PROPERTIES)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "metadataProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:metadataProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES),
            @DocStep(type = Step.UNMARKED)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "externalDocumentProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:externalDocumentProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES),
            @DocStep(type = Step.UNMARKED)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "extentProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:extentProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES),
            @DocStep(type = Step.UNMARKED)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "cachingProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:cachingProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES),
            @DocStep(type = Step.UNMARKED)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "securityProperties",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:securityProperties}"),
            @DocStep(type = Step.JSON_PROPERTIES),
            @DocStep(type = Step.UNMARKED)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
      @DocTable(
          name = "policies",
          rows = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:policiesTable}"),
            @DocStep(type = Step.JSON_PROPERTIES)
          },
          columnSet = ColumnSet.JSON_PROPERTIES),
    },
    vars = {
      @DocVar(
          name = "metadata",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:metadata}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
      @DocVar(
          name = "externalDocument",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:externalDocument}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
      @DocVar(
          name = "extent",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:extent}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
      @DocVar(
          name = "caching",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:caching}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
      @DocVar(
          name = "security",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:security}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
      @DocVar(
          name = "policies",
          value = {
            @DocStep(type = Step.TAG_REFS, params = "{@ref:policies}"),
            @DocStep(type = Step.TAG, params = "{@bodyBlock}")
          }),
    })
@Value.Immutable(prehash = true)
@JsonDeserialize(builder = ImmutableOgcApiDataV2.Builder.class)
public interface OgcApiDataV2 extends ServiceData, ExtendableConfiguration {

  String SERVICE_TYPE = "OGC_API";

  static OgcApiDataV2 replaceOrAddExtensions(
      OgcApiDataV2 apiDataOld, ExtensionConfiguration... extensions) {
    List<ExtensionConfiguration> extensionsNew =
        ExtensionConfiguration.replaceOrAddExtensions(apiDataOld.getExtensions(), extensions);

    return new ImmutableOgcApiDataV2.Builder().from(apiDataOld).extensions(extensionsNew).build();
  }

  abstract class Builder implements EntityDataBuilder<OgcApiDataV2> {

    // jackson should append to instead of replacing extensions
    @JsonIgnore
    public abstract Builder extensions(Iterable<? extends ExtensionConfiguration> elements);

    @JsonProperty("api")
    public abstract Builder addAllExtensions(Iterable<? extends ExtensionConfiguration> elements);

    public abstract ImmutableOgcApiDataV2.Builder id(String id);

    @Override
    public EntityDataBuilder<OgcApiDataV2> fillRequiredFieldsWithPlaceholders() {
      return this.id(EntityDataDefaults.PLACEHOLDER).serviceType(EntityDataDefaults.PLACEHOLDER);
    }
  }

  /**
   * @langEn Always `OGC_API`.
   * @langDe Immer `OGC_API`.
   * @default `OGC_API`
   */
  @Value.Default
  @Override
  default String getServiceType() {
    return SERVICE_TYPE;
  }

  /**
   * @langEn General [Metadata](#metadata) for the API.
   * @langDe Allgemeine [Metadaten](#metadaten) für die API.
   * @default {}
   */
  Optional<ApiMetadata> getMetadata();

  /**
   * @langEn Link to a [document or website](#external-document) with more information about this
   *     API.
   * @langDe Verweis auf [ein Dokument oder eine Website](#externes-dokument) mit weiteren
   *     Informationen über diese API.
   * @default {}
   * @since v2.1
   */
  Optional<ExternalDocumentation> getExternalDocs();

  /**
   * @langEn By default, the spatial and temporal extent of data is derived from the data when
   *     starting the API, but the [Default Extent](#default-extent) can also be configured.
   * @langDe Die räumliche und zeitliche Ausdehnung der Daten wird normalerweise aus den Daten
   *     während des Starts der API abgeleitet, aber die [Angaben](#ausdehnung-der-daten) können
   *     auch in der Konfiguration gesetzt werden.
   * @default { "spatialComputed": true, "temporalComputed": true }
   */
  @JsonMerge(OptBoolean.FALSE)
  Optional<CollectionExtent> getDefaultExtent();

  /**
   * @langEn Sets fixed values for [HTTP Caching Headers](#caching) for the resources.
   * @langDe Setzt feste Werte für [HTTP-Caching-Header](#caching) für die Ressourcen.
   * @default {}
   * @since v3.1
   */
  Optional<Caching> getDefaultCaching();

  /**
   * @langEn [Access Control](#access-control) configuration.
   * @langDe [Access Control](#access-control) konfigurieren.
   * @default {}
   * @since v3.3
   */
  Optional<ApiSecurity> getAccessControl();

  /**
   * @langEn During startup of an API, the configuration can be validated. The supported values are
   *     `NONE`, `LAX`, and `STRICT`. `STRICT` will block the start of an API with warnings, while
   *     an API with warnings, but no errors will start with `LAX`. If the value is set to `NONE`,
   *     no validation will occur. Warnings are issued for problems in the configuration that can
   *     affect the use of the API while errors are issued for cases where the API cannot be used.
   *     Typically, API validation during startup will only be used in development and testing
   *     environments since the API validating results in a slower startup time and should not be
   *     necessary in production environments.
   * @langDe Beim Start einer API kann die Konfiguration validiert werden. Die unterstützten Werte
   *     sind `NONE`, `LAX` und `STRICT`. Bei `STRICT` wird der Start einer API mit Warnungen
   *     blockiert, während eine API mit Warnungen, aber ohne Fehler mit `LAX` startet. Wenn der
   *     Wert auf `NONE` gesetzt wird, findet keine Überprüfung statt. Warnungen werden für Probleme
   *     in der Konfiguration ausgegeben, die die Verwendung der API beeinträchtigen können, während
   *     Fehler für Fälle ausgegeben werden, in denen die API nicht verwendet werden kann.
   *     Normalerweise wird die API-Validierung während des Starts nur in Entwicklungs- und
   *     Testumgebungen verwendet, da die API-Validierung zu einer langsameren Startzeit führt und
   *     in Produktionsumgebungen nicht notwendig sein sollte.
   * @default NONE
   * @since v2.1
   */
  @Nullable
  MODE getApiValidation();

  /**
   * @langEn Tags for this API. Every tag is a string without white space. Tags are shown in the
   *     *API Catalog* and can be used to filter the catalog response with the query parameter
   *     `tags`, e.g. `tags=INSPIRE`.
   * @langDe Ordnet der API die aufgelisteten Tags zu. Die Tags müssen jeweils Strings ohne
   *     Leerzeichen sein. Die Tags werden im API-Katalog angezeigt und können über den
   *     Query-Parameter `tags` zur Filterung der in der API-Katalog-Antwort zurückgelieferten APIs
   *     verwendet werden, z.B. `tags=INSPIRE`.
   * @default []
   * @since v2.1
   */
  Set<String> getTags();

  /**
   * @langEn [API Building Blocks](#building-blocks) configuration.
   * @langDe [API-Bausteine](#bausteine) der API konfigurieren.
   * @default []
   */
  @JsonProperty("api")
  @JsonMerge
  @Override
  List<ExtensionConfiguration> getExtensions();

  /**
   * @langEn Collection configurations, the key is the collection id, for the value see
   *     [Collection](#collection) below.
   * @langDe Ein Objekt mit der spezifischen Konfiguration zu jeder Objektart, der Name der
   *     Objektart ist der Schlüssel, der Wert ein [Collection-Objekt](#collection).
   * @default {}
   */
  // behaves exactly like Map<String, FeatureTypeConfigurationOgcApi>,
  // but supports mergeable builder deserialization
  // (immutables attributeBuilder does not work with maps yet)
  BuildableMap<FeatureTypeConfigurationOgcApi, ImmutableFeatureTypeConfigurationOgcApi.Builder>
      getCollections();

  default Optional<FeatureTypeConfigurationOgcApi> getCollectionData(String collectionId) {
    return Optional.ofNullable(getCollections().get(collectionId));
  }

  @Value.Check
  default OgcApiDataV2 mergeBuildingBlocks() {
    List<ExtensionConfiguration> distinctExtensions = getMergedExtensions();

    // remove duplicates, stable order
    if (!Objects.equals(
        ExtendableConfiguration.discriminators(getExtensions()),
        ExtendableConfiguration.discriminators(distinctExtensions))) {
      return new ImmutableOgcApiDataV2.Builder().from(this).extensions(distinctExtensions).build();
    }

    boolean shouldUpdateParentExtensions =
        getCollections().values().stream()
            .anyMatch(
                collection ->
                    !Objects.equals(collection.getParentExtensions(), distinctExtensions));

    if (shouldUpdateParentExtensions) {
      Map<String, FeatureTypeConfigurationOgcApi> mergedCollections =
          new LinkedHashMap<>(getCollections());

      mergedCollections
          .values()
          .forEach(
              featureTypeConfigurationOgcApi ->
                  mergedCollections.put(
                      featureTypeConfigurationOgcApi.getId(),
                      featureTypeConfigurationOgcApi
                          .getBuilder()
                          .parentExtensions(getMergedExtensions())
                          .build()));

      return new ImmutableOgcApiDataV2.Builder().from(this).collections(mergedCollections).build();
    }

    return this;
  }

  default boolean isCollectionEnabled(final String collectionId) {
    return getCollections().containsKey(collectionId)
        && Objects.requireNonNull(getCollections().get(collectionId)).getEnabled();
  }

  @JsonIgnore
  @Value.Derived
  default boolean isDataset() {
    // return false if there no collection or no collection that is enabled
    return getCollections().values().stream().anyMatch(FeatureTypeConfigurationOgcApi::getEnabled);
  }

  /**
   * Determine extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @return the extent
   */
  default Optional<CollectionExtent> getExtent(String collectionId) {
    return getCollections().values().stream()
        .filter(featureTypeConfiguration -> featureTypeConfiguration.getId().equals(collectionId))
        .filter(FeatureTypeConfigurationOgcApi::getEnabled)
        .findFirst()
        .flatMap(FeatureTypeConfigurationOgcApi::getExtent)
        .flatMap(
            collectionExtent -> mergeExtents(getDefaultExtent(), Optional.of(collectionExtent)))
        .or(this::getDefaultExtent);
  }

  private Optional<CollectionExtent> mergeExtents(
      Optional<CollectionExtent> defaultExtent, Optional<CollectionExtent> collectionExtent) {
    if (defaultExtent.isEmpty()) {
      return collectionExtent;
    } else if (collectionExtent.isEmpty()) {
      return defaultExtent;
    }

    return Optional.of(
        new ImmutableCollectionExtent.Builder()
            .from(defaultExtent.get())
            .from(collectionExtent.get())
            .build());
  }

  default <T extends ExtensionConfiguration> Optional<T> getExtension(
      Class<T> clazz, String collectionId) {
    if (isCollectionEnabled(collectionId)) {
      return Objects.requireNonNull(getCollections().get(collectionId)).getExtension(clazz);
    }
    return getExtension(clazz);
  }
}
