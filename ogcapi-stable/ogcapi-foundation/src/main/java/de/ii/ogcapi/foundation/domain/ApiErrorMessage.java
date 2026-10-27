/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiErrorMessage extends io.dropwizard.jersey.errors.ErrorMessage {

  private String instance;
  private String type;

  public ApiErrorMessage(String title) {
    super(title);
  }

  public ApiErrorMessage(int code, String title) {
    super(code, code == 422 && title.isBlank() ? "Unprocessable Entity" : title);
  }

  public ApiErrorMessage(int code, String title, String detail) {
    super(code, code == 422 && title.isBlank() ? "Unprocessable Entity" : title, detail);
  }

  public ApiErrorMessage(int code, String title, String detail, String instance) {
    super(code, code == 422 && title.isBlank() ? "Unprocessable Entity" : title, detail);
    this.instance = instance;
  }

  public ApiErrorMessage(int code, String title, String detail, String instance, String type) {
    super(code, code == 422 && title.isBlank() ? "Unprocessable Entity" : title, detail);
    this.instance = instance;
    this.type = type;
  }

  @JsonProperty("title")
  @Override
  public String getMessage() {
    return super.getMessage();
  }

  @JsonProperty("status")
  @Override
  public Integer getCode() {
    return super.getCode();
  }

  @JsonProperty("detail")
  @Override
  public String getDetails() {
    return super.getDetails();
  }

  @JsonProperty
  public String getInstance() {
    return instance;
  }

  @JsonProperty("type")
  public String getType() {
    return type;
  }
}
