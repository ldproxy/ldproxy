/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.styles.app.MbStyleExpressionDeserializer;

@JsonDeserialize(using = MbStyleExpressionDeserializer.class)
public sealed interface MbStyleExpression
    permits StringValue, NumberValue, BooleanValue, ArrayValue, ObjectValue, NullValue {

  default <T> T accept(MbStyleExpressionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
