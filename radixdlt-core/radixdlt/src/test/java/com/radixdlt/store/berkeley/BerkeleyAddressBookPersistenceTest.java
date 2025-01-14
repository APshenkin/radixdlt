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

package com.radixdlt.store.berkeley;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.powermock.reflect.Whitebox;
import org.radix.Radix;
import org.radix.serialization.RadixTest;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerPersistence;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.store.DatabaseEnvironment;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BerkeleyAddressBookPersistenceTest extends RadixTest {

	private PeerPersistence abp;
	private DatabaseEnvironment dbEnv;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void setUp() {
		this.dbEnv = new DatabaseEnvironment(folder.getRoot().getAbsolutePath(), 0);
		this.abp = new BerkeleyAddressBookPersistence(getSerialization(), dbEnv, mock(SystemCounters.class));
		this.abp.reset();
	}

	@After
	public void tearDown() {
		this.abp.close();
		this.dbEnv.stop();
	}

	@Test
	public void testStart() {
		// No exceptions, and should have a database when done
		this.abp.start();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testStop() {
		// No exceptions, and should have no database when done
		this.abp.start();
		assertNotNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
		this.abp.close();
		assertNull(Whitebox.getInternalState(this.abp, "peersByNidDB"));
	}

	@Test
	public void testReset() {
		this.abp.start();
		assertTrue(this.abp.savePeer(makePeer()));
		AtomicInteger peercount1 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount1.incrementAndGet());
		assertEquals(1, peercount1.get());

		this.abp.close();
		this.abp.reset();

		this.abp.start();
		AtomicInteger peercount2 = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount2.incrementAndGet());
		assertEquals(0, peercount2.get());
	}

	@Test
	public void testSavePeer() {
		this.abp.start();

		PeerWithSystem pws = makePeer();
		assertTrue(this.abp.savePeer(pws));
		assertEquals(1, peerCount());
		assertEquals(0L, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Update timestamp
		long now = Time.currentTimestamp();
		pws.setTimestamp(Timestamps.ACTIVE, now);
		assertTrue(this.abp.savePeer(pws));
		assertEquals(1, peerCount());
		assertEquals(now, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Add new peer
		PeerWithSystem pws2 = makePeer();
		assertTrue(this.abp.savePeer(pws2));
		assertEquals(2, peerCount());
	}

	@Test
	public void testDeletePeer() {
		this.abp.start();

		PeerWithSystem pws1 = makePeer();
		assertTrue(this.abp.savePeer(pws1));
		assertEquals(1, peerCount());
		PeerWithSystem pws2 = makePeer();
		assertTrue(this.abp.savePeer(pws2));
		assertEquals(2, peerCount());

		// Delete one, and check that the only one left is the right one
		assertTrue(this.abp.deletePeer(pws1.getNID()));
		assertEquals(pws2.getNID(), onlyPeer().getNID());

		// Add back the deleted one, and delete the other
		assertTrue(this.abp.savePeer(pws1));
		assertEquals(2, peerCount());
		assertTrue(this.abp.deletePeer(pws2.getNID()));
		assertEquals(pws1.getNID(), onlyPeer().getNID());

		// Try to delete something that doesn't exist
		assertFalse(this.abp.deletePeer(pws2.getNID()));
	}

	private int peerCount() {
		AtomicInteger peercount = new AtomicInteger(0);
		this.abp.forEachPersistedPeer(p -> peercount.incrementAndGet());
		return peercount.get();
	}

	private Peer onlyPeer() {
		AtomicReference<Peer> peer = new AtomicReference<>();
		this.abp.forEachPersistedPeer(p -> assertTrue(peer.compareAndSet(null, p)));
		assertNotNull(peer.get());
		return peer.get();
	}

	private PeerWithSystem makePeer() {
		ECKeyPair key = ECKeyPair.generateNew();
		RadixSystem sys = new RadixSystem(key.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ImmutableList.of());
		return new PeerWithSystem(sys);
	}
}
