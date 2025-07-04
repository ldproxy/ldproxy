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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
    properties.forEach(
        (key, value) -> {
          build(builder, "", key, value);
        });
    return builder.build();
  }

  private void build(
      ImmutableList.Builder<SchemaProperty> builder, String path, String key, JsonSchema value) {
    String fullPath = path.isEmpty() ? key : path + "." + key;
    ImmutableSchemaProperty.Builder builder2 = ImmutableSchemaProperty.builder().id(fullPath);
    builder2.title(value.getTitle()).description(value.getDescription());

    boolean isArray = false;

    if (value instanceof JsonSchemaArray) {
      isArray = true;
      value = ((JsonSchemaArray) value).getItems();
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
      builder2.values(((JsonSchemaString) value).getEnums());
    } else if (value instanceof JsonSchemaNumber) {
      builder2.type("number");
    } else if (value instanceof JsonSchemaInteger) {
      builder2.type("integer");
      builder2.values(
          ((JsonSchemaInteger) value)
              .getEnums().stream().map(String::valueOf).collect(Collectors.toList()));
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
              .isArray(isArray);
      builder.add(group.inSchemaType(type()).build());

      def.getProperties()
          .forEach((childKey, childVal) -> build(builder, fullPath, childKey, childVal));

      return;
    } else {
      builder2.type("string");
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
