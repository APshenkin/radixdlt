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

package com.radixdlt.epochs;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.utils.UInt256;

import nl.jqno.equalsverifier.EqualsVerifier;

import java.lang.reflect.Modifier;
import java.util.Set;

import org.assertj.core.util.Sets;
import org.junit.Test;

public class EpochsLedgerUpdateTest {
	@Test
	public void testEquals() {
		EqualsVerifier.forClass(EpochsLedgerUpdate.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void testEqualsForEpochsLedgerUpdateHierarchy() {
		final var cls = EpochsLedgerUpdate.class;
		final Set<Class<?>> classesToIgnore = Sets.newLinkedHashSet(
			SerializerDummy.class, // No equals or hashCode
			UInt256.class, // Assumes and ensures non-null fields in hashCode()
			Object.class // for Optional fields
		);
		checkEquals(cls, classesToIgnore);
	}

	private static void checkEquals(Class<?> cls, Set<Class<?>> classesToIgnore) throws SecurityException {
		EqualsVerifier.forClass(cls)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
		for (final var field : cls.getDeclaredFields()) {
			final var fcls = field.getType();
			if (!fcls.isPrimitive() && !fcls.isArray() && !Modifier.isAbstract(fcls.getModifiers()) && classesToIgnore.add(fcls)) {
				checkEquals(fcls, classesToIgnore);
			}
		}
	}
}