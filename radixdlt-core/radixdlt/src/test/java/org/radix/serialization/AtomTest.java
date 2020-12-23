/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.radix.serialization;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AtomTest {

	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Test
	public void hash_test_atom() {
		Atom atom = new Atom(ImmutableMap.of());
		HashCode hash = hasher.hash(atom);
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("311964d6688530d47baba551393b9a51a5e6a22504133597e3f7c2af5f83a2ce", hashHex);
	}

	@Test
	public void hash_test_particle() {
		RadixAddress address = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RRI rri = RRI.of(address, "FOOBAR");
		RRIParticle particle = new RRIParticle(rri);
		HashCode hash = hasher.hash(particle);
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("a29e3505d9736f4de2a576b2fee1b6a449e56f6b3cbaa86b8388e39a1557c53a", hashHex);
	}

	private void assertIsNotRawDSON(HashCode hash) {
		String hashHex = hash.toString();
		// CBOR/DSON encoding of an object starts with "bf" and ends with "ff", so we are here making
		// sure that Hash of the object is not just the DSON output, but rather a 256 bit hash digest of it.
		// the probability of 'accidentally' getting getting these prefixes and suffixes anyway is minimal (1/2^16)
		// for any DSON bytes as argument.
		assertFalse(hashHex.startsWith("bf") && hashHex.endsWith("ff"));
	}
}
