/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.HighQC;
import java.util.Objects;

/**
 * An event emitted when the high qc has been updated
 */
public final class BFTHighQCUpdate {
	private final VerifiedVertexStoreState vertexStoreState;

	private BFTHighQCUpdate(VerifiedVertexStoreState vertexStoreState) {
		this.vertexStoreState = vertexStoreState;
	}

	public static BFTHighQCUpdate create(VerifiedVertexStoreState vertexStoreState) {
		return new BFTHighQCUpdate(vertexStoreState);
	}

	public HighQC getHighQC() {
		return vertexStoreState.getHighQC();
	}

	public VerifiedVertexStoreState getVertexStoreState() {
		return vertexStoreState;
	}

	@Override
	public String toString() {
		return String.format("%s{highQC=%s}", this.getClass().getSimpleName(), vertexStoreState.getHighQC());
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertexStoreState);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof BFTHighQCUpdate)) {
			return false;
		}

		BFTHighQCUpdate other = (BFTHighQCUpdate) o;
		return Objects.equals(other.vertexStoreState, this.vertexStoreState);
	}
}
