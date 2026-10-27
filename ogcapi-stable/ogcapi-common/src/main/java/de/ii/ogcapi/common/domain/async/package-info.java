/**
 * This package contains classes related to asynchronous processing. It should be contained in
 * module ogcapi-async, but that module is at the bottom of the dependency hierarchy and cannot be
 * used by modules in stable and draft. *
 *
 * <p>If and when the async module is moved to stable, this package should be moved to that module.
 */
@Value.Style(deepImmutablesDetection = true, builder = "new")
package de.ii.ogcapi.common.domain.async;

import org.immutables.value.Value;
