/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class StakedBalance {
	private final Map<ECPublicKey, UInt256> stakes = new ConcurrentHashMap<>();

	public void addStake(ECPublicKey delegate, UInt256 amount) {
		stakes.merge(delegate, amount, UInt256::add);
	}

	public void removeStake(ECPublicKey delegate, UInt256 amount) {
		stakes.computeIfPresent(delegate, (d, cur) -> {
			var newAmt = cur.subtract(amount);
			return newAmt.isZero() ? null : newAmt;
		});
	}

	public void forEach(BiConsumer<ECPublicKey, UInt256> consumer) {
		this.stakes.forEach(consumer);
	}
}
