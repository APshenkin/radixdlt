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

package com.radixdlt.crypto;

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

/**
 * Interface for signature and public key computation functions.
 * <p>
 * The intent behind this interface is that the actual implementations can
 * easily be replaced when required.
 * <p>
 * Note that all methods must be thread safe.
 */
interface KeyHandler {

	/**
	 * Sign the specified hash with the specified private key.
	 *
	 * @param hash The hash to sign
	 * @param privateKey The private key to sign the hash with
	 * @param enforceLowS If signature should enforce low values of signature part {@code S}, according to
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
	 * @param useDeterministicSignatures If signing should use randomness or be deterministic according to
	 * <a href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
	 * @return An {@link ECDSASignature} with {@code r} and {@code s} values included
	 */
	ECDSASignature sign(byte[] hash, byte[] privateKey, boolean enforceLowS, boolean useDeterministicSignatures);

	/**
	 * Verify the specified signature against the specified hash with the
	 * specified public key.
	 *
	 * @param hash The hash to verify against
	 * @param signature The signature to verify
	 * @param publicKey The public key to verify the signature with
	 * @return An boolean indicating whether the signature could be successfully validated
	 * @throws CryptoException if the {@code publicKey} or {@code signature} is invalid
	 */
	boolean verify(byte[] hash, ECDSASignature signature, byte[] publicKey);

	/**
	 * Compute a public key for the specified private key.
	 *
	 * @param privateKey The private key to compute the public key for
	 * @return A compressed public key
	 * @throws CryptoException If the {@code privateKey} is invalid
	 */
	byte[] computePublicKey(byte[] privateKey) throws PrivateKeyException, PublicKeyException;

	/**
	 * Sign the specified hash with the specified private by using randomness and enforced low {@code S} values,
	 * see documentation of {@link #sign(byte[], byte[], boolean, boolean)} for more details.
	 *
	 * @param hash The hash to sign
	 * @param privateKey The private key to sign the hash with
	 * @return An {@link ECDSASignature} with {@code r} and {@code s} values included
	 */
	default ECDSASignature sign(byte[] hash, byte[] privateKey) {
		return sign(hash, privateKey, true, false);
	}

}
