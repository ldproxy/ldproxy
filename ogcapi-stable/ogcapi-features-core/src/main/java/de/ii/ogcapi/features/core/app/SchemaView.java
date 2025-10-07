/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.ImmutableSchemaProperty;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaArray;
import de.ii.ogcapi.features.core.domain.JsonSchemaBoolean;
import de.ii.ogcapi.features.core.domain.JsonSchemaGeometry;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.JsonSchemaNumber;
import de.ii.ogcapi.features.core.domain.JsonSchemaObject;
import de.ii.ogcapi.features.core.domain.JsonSchemaRef;
import de.ii.ogcapi.features.core.domain.JsonSchemaString;
import de.ii.ogcapi.features.core.domain.SchemaProperty;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@Value.Immutable
@Value.Style(builder = "new", visibility = ImplementationVisibility.PUBLIC)
public abstract class SchemaView extends OgcApiView implements FormatHtml {

  public SchemaView() {
    super("schema.mustache");
  }

  public abstract String collectionId();

  @Override
  public abstract String title();

  @Override
  public abstract String description();

  public abstract Optional<String> typeLabel();

  public abstract Optional<String> typeDescription();

  @Override
  public List<NavigationDTO> breadCrumbs() {
    String rootTitle = i18n().get("root", language());
    String collectionsTitle = i18n().get("collectionsTitle", language());
    String schemaTitle = i18n().get(type().toString() + "Title", language());
    URICustomizer resourceUri = uriCustomizer().copy().clearParameters();

    return new ImmutableList.Builder<NavigationDTO>()
        .add(
            new NavigationDTO(
                rootTitle,
                homeUrl(apiData())
                    .orElse(
                        resourceUri
                            .copy()
                            .removeLastPathSegments(apiData().getSubPath().size() + 3)
                            .toString())))
        .add(
            new NavigationDTO(
                apiData().getLabel(), resourceUri.copy().removeLastPathSegments(3).toString()))
        .add(
            new NavigationDTO(
                collectionsTitle, resourceUri.copy().removeLastPathSegments(2).toString()))
        .add(
            new NavigationDTO(
                apiData().getCollections().get(collectionId()).getLabel(),
                resourceUri.copy().removeLastPathSegments(1).toString()))
        .add(new NavigationDTO(schemaTitle))
        .build();
  }

  @Value.Derived
  public List<SchemaProperty> schemaProperties() {
    Map<String, JsonSchema> properties = schema().getProperties();
    ImmutableList.Builder<SchemaProperty> builder = ImmutableList.builder();
    properties.entrySet().stream()
        .sorted(Comparator.comparingInt(entry -> entry.getValue().getPropertySeq().orElse(0)))
        .forEach(entry -> build(builder, "", entry.getKey(), entry.getValue()));
    return builder.build();
  }

  private void build(
      ImmutableList.Builder<SchemaProperty> builder, String path, String key, JsonSchema value) {
    String fullPath = path.isEmpty() ? key : path + "." + key;
    ImmutableSchemaProperty.Builder builder2 = ImmutableSchemaProperty.builder().id(fullPath);
    builder2
        .title(value.getTitle())
        .description(value.getDescription())
        .readOnly(value.isReadOnly())
        .writeOnly(value.isWriteOnly());

    boolean isArray = false;

    if (value instanceof JsonSchemaArray array) {
      isArray = true;
      // In case of prefixItems, this takes the first item as representative
      value = array.getItemSchema();
    }
    builder2.isArray(isArray);

    if (value instanceof JsonSchemaString) {
      Optional<String> format = ((JsonSchemaString) value).getFormat();
      if (format.isPresent()) {
        if (format.get().equals("date-time")) {
          builder2.type("date-time");
        } else if (format.get().equals("date")) {
          builder2.type("date");
        } else {
          builder2.type("string");
        }
      } else {
        builder2.type("string");
      }
      ((JsonSchemaString) value).getEnums().ifPresent(builder2::values);
    } else if (value instanceof JsonSchemaNumber) {
      builder2.type("number");
    } else if (value instanceof JsonSchemaInteger) {
      builder2.type("integer");
      ((JsonSchemaInteger) value)
          .getEnums()
          .ifPresent(enums -> builder2.values(enums.stream().map(String::valueOf).toList()));
    } else if (value instanceof JsonSchemaBoolean) {
      builder2.type("boolean");
    } else if (value instanceof JsonSchemaGeometry) {
      builder2.type(
          ((JsonSchemaGeometry) value).getFormat().replace("geometry-", "geometry (") + ")");
    } else if (value instanceof JsonSchemaRef) {
      JsonSchemaObject def = (JsonSchemaObject) ((JsonSchemaRef) value).getDef();

      ImmutableSchemaProperty.Builder group =
          ImmutableSchemaProperty.builder()
              .id(fullPath)
              .title(def.getTitle())
              .description(def.getDescription())
              .type("object")
              .isArray(isArray)
              .readOnly(def.isReadOnly())
              .writeOnly(def.isWriteOnly());
      builder.add(group.inSchemaType(type()).build());

      def.getProperties().entrySet().stream()
          .sorted(Comparator.comparingInt(entry -> entry.getValue().getPropertySeq().orElse(0)))
          .forEach(
              childEntry -> build(builder, fullPath, childEntry.getKey(), childEntry.getValue()));

      return;
    } else {
      builder2.type("string");
    }

    Optional<String> role = value.getRole().or(value::getEmbeddedRole);

    if (role.filter("id"::equals).isPresent()) {
      builder2.role(i18n().get("idTitle", language()));
    } else if (role.filter("primary-geometry"::equals).isPresent()) {
      builder2.role(i18n().get("primaryGeometryTitle", language()));
    } else if (role.filter("secondary-geometry"::equals).isPresent()) {
      builder2.role(i18n().get("secondaryGeometryTitle", language()));
    } else if (role.filter("primary-instant"::equals).isPresent()) {
      builder2.role(i18n().get("primaryInstantTitle", language()));
    } else if (role.filter("primary-interval-start"::equals).isPresent()) {
      builder2.role(i18n().get("primaryIntervalStartTitle", language()));
    } else if (role.filter("primary-interval-end"::equals).isPresent()) {
      builder2.role(i18n().get("primaryIntervalEndTitle", language()));
    } else if (role.filter("reference"::equals).isPresent()) {
      value
          .getRefCollectionId()
          .filter(collectionId -> apiData().isCollectionEnabled(collectionId))
          .ifPresent(
              collectionId -> {
                String text;
                String label =
                    apiData()
                        .getCollectionData(collectionId)
                        .map(FeatureTypeConfiguration::getLabel)
                        .orElse(collectionId);
                try {
                  URI uri =
                      uriCustomizer()
                          .copy()
                          .setPathSegments(apiData().getSubPath().toArray(new String[0]))
                          .appendPathSegments("collections", collectionId)
                          .clearParameters()
                          .build();
                  text =
                      String.format(
                          "<a href=\"%s\" target=\"_blank\">%s</a>", uri.toString(), label);
                } catch (URISyntaxException ignore) {
                  // ignore
                  text = label;
                }
                builder2.addRefCollectionIds(text);
              });
    }

    if (value.getCodelistId().isPresent()) {
      builder2.codelistId(value.getCodelistId().get());
      value.getCodelistUri().ifPresent(builder2::codelistUri);
    }

    builder.add(builder2.inSchemaType(type()).build());
  }

  @Value.Derived
  public Optional<String> propertiesTitle() {
    return Optional.of(i18n().get("propertiesTitle", language()));
  }

  @Value.Derived
  public Optional<String> typeTitle() {
    return Optional.of(i18n().get("typeTitle", language()));
  }

  @Value.Derived
  public Optional<String> enumTitle() {
    return Optional.of(i18n().get("enumTitle", language()));
  }

  @Value.Derived
  public Optional<String> referenceTitle() {
    return Optional.of(i18n().get("referenceTitle", language()));
  }

  @Value.Derived
  public Optional<String> codelistTitle() {
    return Optional.of(i18n().get("codelistTitle", language()).replace(" '{{codelist}}'", ""));
  }

  @Value.Derived
  public Optional<String> roleTitle() {
    return Optional.of(i18n().get("roleTitle", language()));
  }

  public abstract Optional<Boolean> hasEnum();

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  // sum of idCols+descCols must be 12 for bootstrap
  @Value.Derived
  public Integer idCols() {
    int maxIdLength =
        this.schemaProperties().stream()
            .map(SchemaProperty::getId)
            .filter(Objects::nonNull)
            .mapToInt(String::length)
            .max()
            .orElse(0);
    return Math.min(Math.max(2, 1 + maxIdLength / 8), 6);
  }

  @Value.Derived
  public Integer descCols() {
    // idCols will be calculated first
    if (idCols() != null) {
      return 12 - idCols();
    }
    return 12;
  }

  public abstract JsonSchemaObject schema();

  public abstract SchemaType type();

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();
}
