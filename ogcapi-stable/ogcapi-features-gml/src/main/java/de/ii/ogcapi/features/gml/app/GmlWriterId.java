/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Role;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class GmlWriterId implements GmlWriter {

  private static final DateTimeFormatter TEMPORAL_SUFFIX_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'X'");

  @Inject
  public GmlWriterId() {}

  @Override
  public GmlWriterId create() {
    return new GmlWriterId();
  }

  @Override
  public int getSortPriority() {
    return 10;
  }

  @Override
  public void onValue(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {

    if (context.schema().isPresent() && Objects.nonNull(context.value())) {
      FeatureSchema currentSchema = context.schema().get();

      if (currentSchema.isId()) {
        String id = context.encoding().getGmlIdPrefix().orElse("") + context.value();
        context.encoding().setCurrentGmlId(id);
      } else if (context.encoding().getAppendTemporalSuffixToGmlId()
          && isPrimaryTemporal(currentSchema)) {
        String suffix = formatTemporalSuffix(context.value());
        if (suffix != null) {
          context.encoding().appendGmlIdSuffix(suffix);
        }
      }
    }

    next.accept(context);
  }

  private static boolean isPrimaryTemporal(FeatureSchema schema) {
    return schema
        .getRole()
        .filter(r -> r == Role.PRIMARY_INSTANT || r == Role.PRIMARY_INTERVAL_START)
        .isPresent();
  }

  static String formatTemporalSuffix(String isoValue) {
    try {
      return OffsetDateTime.parse(isoValue).format(TEMPORAL_SUFFIX_FORMATTER);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return java.time.LocalDateTime.parse(isoValue)
          .atOffset(ZoneOffset.UTC)
          .format(TEMPORAL_SUFFIX_FORMATTER);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return java.time.LocalDate.parse(isoValue)
          .atStartOfDay()
          .atOffset(ZoneOffset.UTC)
          .format(TEMPORAL_SUFFIX_FORMATTER);
    } catch (DateTimeParseException ignored) {
    }
    return null;
  }
}
