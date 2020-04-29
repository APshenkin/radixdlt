/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.mempool.Mempool;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Logic for generating new proposals
 */
public final class MempoolProposalGenerator implements ProposalGenerator {
	private final Mempool mempool;
	private final VertexStore vertexStore;

	@Inject
	public MempoolProposalGenerator(
		VertexStore vertexStore,
		Mempool mempool
	) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.mempool = Objects.requireNonNull(mempool);
	}

	// TODO: check that next proposal works with current vertexStore state
	@Override
	public Vertex generateProposal(View view) {
		final QuorumCertificate highestQC = vertexStore.getHighestQC();
		final List<Vertex> preparedVertices = vertexStore.getPathFromRoot(highestQC.getProposed().getId());
		final Set<AID> preparedAtoms = preparedVertices.stream()
			.map(Vertex::getAtom)
			.filter(Objects::nonNull)
			.map(Atom::getAID)
			.collect(Collectors.toSet());

		final List<Atom> atoms = mempool.getAtoms(1, preparedAtoms);

		return Vertex.createVertex(highestQC, view, !atoms.isEmpty() ? atoms.get(0) : null);
	}
}
