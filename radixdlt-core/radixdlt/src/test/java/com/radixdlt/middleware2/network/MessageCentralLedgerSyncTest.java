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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.universe.Universe;
import java.util.Optional;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class MessageCentralLedgerSyncTest {
	private MessageCentralLedgerSync messageCentralLedgerSync;
	private MessageCentral messageCentral;
	private AddressBook addressBook;

	@Before
	public void setup() {
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(123);
		this.messageCentral = MessageCentralMockProvider.get();
		this.addressBook = mock(AddressBook.class);
		this.messageCentralLedgerSync = new MessageCentralLedgerSync(universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_sync_request__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		messageCentralLedgerSync.syncRequestDispatcher().dispatch(node, mock(SyncRequest.class));
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_send_sync_response__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		messageCentralLedgerSync.syncResponseDispatcher().dispatch(node, mock(SyncResponse.class));
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_receive_sync_request__then_should_receive_it() {
		TestSubscriber<RemoteEvent<SyncRequest>> testObserver =
			this.messageCentralLedgerSync.syncRequests().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		SyncRequestMessage syncRequestMessage = mock(SyncRequestMessage.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(syncRequestMessage.getCurrentHeader()).thenReturn(header);
		messageCentral.send(peer, syncRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(syncRequest ->
			syncRequest.getEvent().getHeader().equals(header) && syncRequest.getOrigin().getKey().equals(key)
		);
	}

	@Test
	public void when_receive_sync_response__then_should_receive_it() {
		TestSubscriber<RemoteEvent<SyncResponse>> testObserver = this.messageCentralLedgerSync.syncResponses().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		SyncResponseMessage syncResponseMessage = mock(SyncResponseMessage.class);
		DtoCommandsAndProof commands = mock(DtoCommandsAndProof.class);
		when(syncResponseMessage.getCommands()).thenReturn(commands);
		messageCentral.send(peer, syncResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(resp -> resp.getEvent().getCommandsAndProof().equals(commands));
	}

	@Test
	public void when_receive_status_request__then_should_receive_it() {
		TestSubscriber<RemoteEvent<StatusRequest>> testObserver =
			this.messageCentralLedgerSync.statusRequests().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		StatusRequestMessage statusRequestMessage = mock(StatusRequestMessage.class);
		messageCentral.send(peer, statusRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(statusResponse -> statusResponse.getOrigin().getKey().equals(key));
	}

	@Test
	public void when_receive_status_response__then_should_receive_it() {
		TestSubscriber<RemoteEvent<StatusResponse>> testObserver =
			this.messageCentralLedgerSync.statusResponses().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		final var header = mock(VerifiedLedgerHeaderAndProof.class);
		StatusResponseMessage statusResponseMessage = mock(StatusResponseMessage.class);
		when(statusResponseMessage.getHeader()).thenReturn(header);
		messageCentral.send(peer, statusResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(statusResponse ->
			statusResponse.getEvent().getHeader().equals(header) && statusResponse.getOrigin().getKey().equals(key)
		);
	}
}
