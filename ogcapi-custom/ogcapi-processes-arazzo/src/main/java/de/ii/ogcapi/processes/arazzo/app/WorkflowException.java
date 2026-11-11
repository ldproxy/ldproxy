/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

/**
 * A workflow that cannot be executed or that failed; both are client errors of the execution. A
 * description that is invalid or not supported is a {@link Kind#PROBLEM}, also in a nested
 * workflow; a workflow that failed, e.g. because a step failed without a failure action, is a
 * {@link Kind#FAILURE}, which a nested workflow reports as the failure of the step that called it.
 */
class WorkflowException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  enum Kind {
    PROBLEM(400),
    FAILURE(422);

    private final int status;

    Kind(int status) {
      this.status = status;
    }

    int getStatus() {
      return status;
    }
  }

  private final Kind kind;

  WorkflowException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  WorkflowException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  static WorkflowException problem(String format, Object... arguments) {
    return new WorkflowException(Kind.PROBLEM, String.format(format, arguments));
  }

  static WorkflowException failure(String format, Object... arguments) {
    return new WorkflowException(Kind.FAILURE, String.format(format, arguments));
  }

  Kind getKind() {
    return kind;
  }

  int getStatus() {
    return kind.getStatus();
  }
}
