/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.processes.domain.model.ImmutableOgcInputDescription
import de.ii.ogcapi.processes.domain.model.ImmutableOgcOutputDescription
import de.ii.ogcapi.processes.domain.model.ImmutableOgcSchema
import de.ii.ogcapi.processes.domain.model.ImmutableProcessData
import de.ii.ogcapi.processes.domain.model.OgcInputDescription
import de.ii.ogcapi.processes.domain.model.OgcOutputDescription
import de.ii.ogcapi.processes.domain.model.ProcessData
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions
import de.ii.ogcapi.processes.domain.model.OgcSchema
import de.ii.ogcapi.processes.domain.model.Property.Type

class ProcessFixtures {

    static ProcessData process(List<JobControlOptions> jobControlOptions,
                               Map<String, OgcInputDescription> inputs = [:],
                               Map<String, OgcOutputDescription> outputs = [:],
                               boolean jobSetup = true) {
        new ImmutableProcessData.Builder()
                .id("TestProcess")
                .version("1.0.0")
                .jobControlOptions(jobControlOptions)
                .inputs(inputs)
                .outputs(outputs)
                .jobSetup(jobSetup)
                .build()
    }

    static OgcInputDescription input(OgcSchema schema, int minOccurs = 1, int maxOccurs = 1) {
        new ImmutableOgcInputDescription.Builder()
                .schema(schema)
                .minOccurs(minOccurs)
                .maxOccurs(maxOccurs)
                .build()
    }

    static OgcOutputDescription output(OgcSchema schema, int maxOccurs = 1) {
        new ImmutableOgcOutputDescription.Builder()
                .schema(schema)
                .maxOccurs(maxOccurs)
                .build()
    }

    static OgcSchema schema(Type type) {
        new ImmutableOgcSchema.Builder().type(type).build()
    }
}
