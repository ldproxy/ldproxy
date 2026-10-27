/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public abstract class AbstractQueryParameterDatetime extends OgcApiQueryParameterBase {

  private static final String NOW_REGEX = "(?:[nN][oO][wW])";
  private static final String OPEN_REGEX = "(?:\\.\\.)?";
  // the day exists in the month, February 29 only in a leap year
  private static final String LOCAL_DATE_REGEX =
      "(?:\\d{4}-(?:(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-8])|(?:0[13-9]|1[0-2])-(?:29|30)"
          + "|(?:0[13578]|1[02])-31)|(?:\\d{2}(?:0[48]|[2468][048]|[13579][26])"
          + "|(?:[02468][048]|[13579][26])00)-02-29)";
  private static final String LOCAL_DATE_OR_OPEN_REGEX =
      "(?:" + LOCAL_DATE_REGEX + "|" + OPEN_REGEX + "|" + NOW_REGEX + ")";
  private static final String LOCAL_DATE_OPEN_INTERVAL_REGEX =
      LOCAL_DATE_OR_OPEN_REGEX + "/" + LOCAL_DATE_OR_OPEN_REGEX;
  private static final String LOCAL_DATE_CLOSED_INTERVAL_REGEX =
      LOCAL_DATE_REGEX + "/" + LOCAL_DATE_REGEX;
  // leap seconds are not supported, the second is in the range 00 to 59 with at most nanoseconds;
  // the offset is at most 18 hours
  private static final String OFFSET_DATE_TIME_REGEX =
      LOCAL_DATE_REGEX
          + "T(?:[01][0-9]|2[0-3]):(?:[0-5][0-9]):(?:[0-5][0-9])(?:\\.[0-9]{1,9})?(Z|(\\+|-)(?:(?:0[0-9]|1[0-7]):[0-5][0-9]|18:00))";
  private static final String OFFSET_DATE_TIME_OR_OPEN_REGEX =
      "(?:" + OFFSET_DATE_TIME_REGEX + "|" + OPEN_REGEX + "|" + NOW_REGEX + ")";
  private static final String OFFSET_DATE_TIME_OPEN_INTERVAL_REGEX =
      OFFSET_DATE_TIME_OR_OPEN_REGEX + "/" + OFFSET_DATE_TIME_OR_OPEN_REGEX;
  private static final String OFFSET_DATE_TIME_CLOSED_INTERVAL_REGEX =
      OFFSET_DATE_TIME_REGEX + "/" + OFFSET_DATE_TIME_REGEX;
  private static final String DATETIME_OPEN_REGEX =
      "^"
          + LOCAL_DATE_REGEX
          + "$|^"
          + NOW_REGEX
          + "$|^"
          + LOCAL_DATE_OPEN_INTERVAL_REGEX
          + "$|^"
          + OFFSET_DATE_TIME_REGEX
          + "$|^"
          + OFFSET_DATE_TIME_OPEN_INTERVAL_REGEX
          + "$";
  private static final String DATETIME_CLOSED_REGEX =
      "^"
          + LOCAL_DATE_REGEX
          + "$|^"
          + NOW_REGEX
          + "$|^"
          + LOCAL_DATE_CLOSED_INTERVAL_REGEX
          + "$|^"
          + OFFSET_DATE_TIME_REGEX
          + "$|^"
          + OFFSET_DATE_TIME_CLOSED_INTERVAL_REGEX
          + "$";
  private static final String DATETIME_INSTANT_REGEX =
      "^" + LOCAL_DATE_REGEX + "$|^" + NOW_REGEX + "$|^" + OFFSET_DATE_TIME_REGEX + "$";

  private final Schema<?> baseSchema;
  protected final SchemaValidator schemaValidator;

  public AbstractQueryParameterDatetime(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
    this.baseSchema = new StringSchema().pattern(DATETIME_OPEN_REGEX);
  }

  /** Subclasses build a fresh instance to add per-API or per-collection facets (default, etc.). */
  protected static Schema<?> getCopyOfBaseSchema() {
    return new StringSchema().pattern(DATETIME_OPEN_REGEX);
  }

  /** Instant-only schema variant (no interval forms) for single-feature endpoints. */
  protected static Schema<?> getCopyOfInstantBaseSchema() {
    return new StringSchema().pattern(DATETIME_INSTANT_REGEX);
  }

  @Override
  public String getName() {
    return "datetime";
  }

  @Override
  public String getDescription() {
    return "Either a date-time or an interval. Date and time expressions adhere to RFC 3339.\n"
        + "Intervals may be bounded or half-bounded (double-dots at start or end).\n"
        + "Examples:\n\n"
        + "* A date-time: \"2018-02-12T23:20:50Z\"\n"
        + "* A bounded interval: \"2018-02-12T00:00:00Z/2018-03-18T12:31:12Z\"\n"
        + "* Half-bounded intervals: \"2018-02-12T00:00:00Z/..\" or \"../2018-03-18T12:31:12Z\"\n\n"
        + "Only features that have a temporal property that intersects the value of `datetime` are selected.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return baseSchema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return baseSchema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  /**
   * @param instant a value of the parameter: a timestamp, a date, ".." or "now"
   * @return a timestamp with an offset in UTC, as a timestamp in CQL2 is in UTC, otherwise the
   *     value
   */
  protected static String inUtc(String instant) {
    try {
      return OffsetDateTime.parse(instant).toInstant().toString();
    } catch (DateTimeParseException e) {
      return instant;
    }
  }
}
