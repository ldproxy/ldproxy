/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An output stream that buffers all data written to it until an underlying output stream becomes
 * available. This should be thread-safe but still performant for the given use case by using
 * double-checked locking. It is assumed that there are only two threads involved: one writing to
 * the stream, and one setting the underlying output stream.
 */
public class DelayedOutputStream extends OutputStream {

  private final ByteArrayOutputStream buffer;
  @Nullable private volatile OutputStream outputStream;

  public DelayedOutputStream() {
    this.buffer = new ByteArrayOutputStream();
    this.outputStream = null;
  }

  public synchronized void setOutputStream(OutputStream outputStream) throws IOException {
    buffer.writeTo(outputStream);
    buffer.reset();

    // volatile write - enables fast path
    this.outputStream = outputStream;
  }

  @Override
  public void write(byte[] b) throws IOException {
    // volatile read
    if (Objects.nonNull(outputStream)) {
      // fast path
      outputStream.write(b);
    } else {
      // locking
      synchronized (this) {
        if (Objects.nonNull(outputStream)) {
          outputStream.write(b);
        } else {
          buffer.write(b);
        }
      }
    }
  }

  @Override
  public void write(int b) throws IOException {
    // not needed, ReactiveRx only uses write(byte[])
  }

  @Override
  public void flush() throws IOException {
    if (Objects.nonNull(outputStream)) {
      outputStream.flush();
    }
  }

  @Override
  public void close() throws IOException {
    if (Objects.nonNull(outputStream)) {
      outputStream.close();
    }
  }
}
