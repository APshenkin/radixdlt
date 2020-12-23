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

package org.radix;

import java.util.Objects;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

/**
 * An initial issuance of tokens to the specified key.
 */
public final class TokenIssuance {
	private final ECPublicKey receiver;
	private final UInt256 amount;

	private TokenIssuance(ECPublicKey receiver, UInt256 amount) {
		this.receiver = Objects.requireNonNull(receiver);
		this.amount = Objects.requireNonNull(amount);
	}

	public static TokenIssuance of(ECPublicKey receiver, UInt256 amount) {
		return new TokenIssuance(receiver, amount);
	}

	public ECPublicKey receiver() {
		return this.receiver;
	}

	public UInt256 amount() {
		return this.amount;
	}

	@Override
	public String toString() {
		return String.format("%s[->%s:%s]", getClass().getSimpleName(), this.receiver, this.amount);
	}
}