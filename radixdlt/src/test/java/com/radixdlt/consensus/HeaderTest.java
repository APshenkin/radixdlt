/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HeaderTest {

	private Header testObject;
	private Hash id;
	private LedgerState ledgerState;

	@Before
	public void setUp() {
		View view = View.of(1234567890L);
		this.id = Hash.random();
		this.ledgerState = mock(LedgerState.class);
		this.testObject = new Header(view, id, ledgerState);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Header.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertThat(View.of(1234567890L)).isEqualTo(this.testObject.getView());

		assertThat(id).isEqualTo(this.testObject.getVertexId());
		assertThat(ledgerState).isEqualTo(this.testObject.getLedgerState());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertThat(new Header()).isNotNull();
	}

	@Test
	public void testToString() {
		assertThat(this.testObject.toString()).contains(Header.class.getSimpleName());
	}
}
