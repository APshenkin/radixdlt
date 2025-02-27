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

package com.radixdlt.middleware2.network;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageCentralMockProvider;

import com.radixdlt.utils.RandomHasher;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class MessageCentralValidatorSyncTest {
	private BFTNode self;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralValidatorSync sync;
	private Hasher hasher;

	@Before
	public void setUp() {
		this.self = mock(BFTNode.class);
		EUID selfEUID = mock(EUID.class);
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.euid()).thenReturn(selfEUID);
		when(self.getKey()).thenReturn(pubKey);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = MessageCentralMockProvider.get();
		this.hasher = new RandomHasher();
		this.sync = new MessageCentralValidatorSync(self, 0, addressBook, messageCentral, hasher);
	}

	@Test
	public void when_subscribed_to_rpc_requests__then_should_receive_requests() {
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		when(system.getKey()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		when(peer.getSystem()).thenReturn(system);
		HashCode vertexId0 = mock(HashCode.class);
		HashCode vertexId1 = mock(HashCode.class);

		TestSubscriber<GetVerticesRequest> testObserver = sync.requests().map(RemoteEvent::getEvent).test();
		messageCentral.send(peer, new GetVerticesRequestMessage(0, vertexId0, 1));
		messageCentral.send(peer, new GetVerticesRequestMessage(0, vertexId1, 1));

		testObserver.awaitCount(2);
		testObserver.assertValueAt(0, v -> v.getVertexId().equals(vertexId0));
		testObserver.assertValueAt(1, v -> v.getVertexId().equals(vertexId1));
	}
}
