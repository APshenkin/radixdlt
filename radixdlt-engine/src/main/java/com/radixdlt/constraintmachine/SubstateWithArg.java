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

package com.radixdlt.constraintmachine;

import org.bouncycastle.util.encoders.Hex;

import java.util.Objects;
import java.util.Optional;

public class SubstateWithArg<I extends Particle> {
	private final I particle;
	private final byte[] arg;

	private SubstateWithArg(I particle, byte[] arg) {
		this.particle = particle;
		this.arg = arg;
	}

	public static <I extends Particle> SubstateWithArg<I> withArg(I particle, byte[] arg) {
		Objects.requireNonNull(arg);
		return new SubstateWithArg<>(particle, arg);
	}

	public static <I extends Particle> SubstateWithArg<I> noArg(I particle) {
		return new SubstateWithArg<>(particle, null);
	}

	public I getSubstate() {
		return particle;
	}

	public Optional<byte[]> getArg() {
		return Optional.ofNullable(arg);
	}

	@Override
	public String toString() {
		return String.format("%s{%s %s}", this.getClass().getSimpleName(), particle, arg == null ? null : Hex.toHexString(arg));
	}
}
