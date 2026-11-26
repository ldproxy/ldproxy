/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.html;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.processes.domain.model.OgcDescriptionType;
import de.ii.ogcapi.processes.domain.model.OgcSchema;
import de.ii.ogcapi.processes.domain.model.OgcSchemaAndOccurrences;
import de.ii.ogcapi.processes.domain.model.Property.Type;
import de.ii.ogcapi.processes.domain.model.web.ProcessResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class ProcessView extends OgcApiView {
  public ProcessView() {
    super("process.mustache");
  }

  public abstract ProcessResponse process();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  /** An input or output of the process, prepared for the HTML page. */
  // a class, not a record: the documentation doclet does not support records
  public static final class Parameter {
    private final String id;
    private final String title;
    private final Optional<String> description;
    private final String schema;
    private final String occurrences;

    Parameter(
        String id, String title, Optional<String> description, String schema, String occurrences) {
      this.id = id;
      this.title = title;
      this.description = description;
      this.schema = schema;
      this.occurrences = occurrences;
    }

    public String id() {
      return id;
    }

    public String title() {
      return title;
    }

    public Optional<String> description() {
      return description;
    }

    public String schema() {
      return schema;
    }

    public String occurrences() {
      return occurrences;
    }
  }

  @Value.Derived
  public List<Parameter> inputs() {
    return parameters(process().getInputs());
  }

  @Value.Derived
  public List<Parameter> outputs() {
    return parameters(process().getOutputs());
  }

  private static <T extends OgcDescriptionType & OgcSchemaAndOccurrences>
      List<Parameter> parameters(Map<String, T> parameters) {
    return parameters.entrySet().stream()
        .map(
            entry ->
                new Parameter(
                    entry.getKey(),
                    entry.getValue().getTitle().orElse(entry.getKey()),
                    entry.getValue().getDescription(),
                    schema(entry.getValue().getSchema()),
                    occurrences(entry.getValue())))
        .toList();
  }

  // the JSON Schema keywords of the value, not translated
  private static String schema(OgcSchema schema) {
    List<String> keywords = new ArrayList<>();
    keywords.add("type: " + type(schema.getType()));
    schema.getItems().ifPresent(items -> keywords.add("items: " + type(items.getType())));
    schema.getFormat().ifPresent(format -> keywords.add("format: " + format));
    schema
        .getContentMediaType()
        .ifPresent(mediaType -> keywords.add("contentMediaType: " + mediaType));
    if (!schema.getEnum().isEmpty()) {
      keywords.add("enum: " + String.join(", ", schema.getEnum()));
    }
    schema.getMinimum().ifPresent(v -> keywords.add("minimum: " + number(v)));
    schema.getExclusiveMinimum().ifPresent(v -> keywords.add("exclusiveMinimum: " + number(v)));
    schema.getMaximum().ifPresent(v -> keywords.add("maximum: " + number(v)));
    schema.getExclusiveMaximum().ifPresent(v -> keywords.add("exclusiveMaximum: " + number(v)));
    schema.getPattern().ifPresent(pattern -> keywords.add("pattern: " + pattern));
    schema.getMinItems().ifPresent(v -> keywords.add("minItems: " + v));
    schema.getMaxItems().ifPresent(v -> keywords.add("maxItems: " + v));
    return String.join("; ", keywords);
  }

  private static String type(Type type) {
    return type.name().toLowerCase(Locale.ROOT);
  }

  private static String number(BigDecimal number) {
    return number.toPlainString();
  }

  private static String occurrences(OgcSchemaAndOccurrences parameter) {
    return parameter.getMinOccurs() == parameter.getMaxOccurs()
        ? String.valueOf(parameter.getMinOccurs())
        : parameter.getMinOccurs() + ".." + parameter.getMaxOccurs();
  }

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  // keywords

  @Value.Derived
  public String idTitle() {
    return i18n().get("idTitle", language());
  }

  @Value.Derived
  public String jobControlOptionsTitle() {
    return i18n().get("jobControlOptionsTitle", language());
  }

  @Value.Derived
  public String descriptionTitle() {
    return i18n().get("descriptionTitle", language());
  }

  @Value.Derived
  public String keywordsTitle() {
    return i18n().get("keywordsTitle", language());
  }

  @Value.Derived
  public String metadataTitle() {
    return i18n().get("metadataTitle", language());
  }

  @Value.Derived
  public String inputsTitle() {
    return i18n().get("processInputsTitle", language());
  }

  @Value.Derived
  public String outputsTitle() {
    return i18n().get("processOutputsTitle", language());
  }

  @Value.Derived
  public String schemaTitle() {
    return i18n().get("processValueSchemaTitle", language());
  }

  @Value.Derived
  public String occurrencesTitle() {
    return i18n().get("processOccurrencesTitle", language());
  }
}
