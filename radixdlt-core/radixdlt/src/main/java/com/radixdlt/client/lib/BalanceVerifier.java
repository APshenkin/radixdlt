/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib;

import com.radixdlt.client.AccountAddress;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.impl.SynchronousRadixApiClient;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt384;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.functional.Result;

import java.security.Security;
import java.util.List;

import static java.util.Optional.ofNullable;

public class BalanceVerifier {
	private static final String DEFAULT_HOST = "http://localhost:8080/";

	private final Options options = new Options()
		.addOption("h", "help", false, "Show usage information (this message)")
		.addOption("n", "node-url", true, "Node URL (default to " + DEFAULT_HOST + ")");

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		new BalanceVerifier().run(args);
	}

	private void run(String[] args) {
		Result.wrap(() -> new DefaultParser().parse(options, args))
			.filter(cmd -> cmd.getArgList().isEmpty() && !cmd.hasOption('h'), "Invalid command line options")
			.onSuccess(cmd -> {
				var baseUrl = ofNullable(cmd.getOptionValue('h')).orElse(DEFAULT_HOST);

				SynchronousRadixApiClient.connect(baseUrl)
					.onSuccess(client -> {
						retrieveBalance(client, REAddr.ofPubKeyAccount(pubkeyOf(1)));
						retrieveBalance(client, REAddr.ofPubKeyAccount(pubkeyOf(2)));
						retrieveBalance(client, REAddr.ofPubKeyAccount(pubkeyOf(3)));
						retrieveBalance(client, REAddr.ofPubKeyAccount(pubkeyOf(4)));
						retrieveBalance(client, REAddr.ofPubKeyAccount(pubkeyOf(5)));
					})
					.onFailure(failure -> System.out.println(failure.message()));
			})
			.onFailure(failure -> {
				System.err.println(failure.message());
				usage();
			});
	}

	private void retrieveBalance(RadixApi client, REAddr addr) {
		client.tokenBalances(addr).onSuccess(this::printBalances);
	}

	private void printBalances(TokenBalancesDTO balances) {
		System.out.println("Owner: " + AccountAddress.of(balances.getOwner()));
		if (balances.getTokenBalances().isEmpty()) {
			System.out.println("(empty balances)");
		} else {
			balances.getTokenBalances()
				.forEach(balance ->
							 System.out.printf("    %s : %s", balance.getRri(), balance.getAmount()));
		}
		System.out.println();
	}

	private ECPublicKey pubkeyOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey).getPublicKey();
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}

	private void usage() {
		new HelpFormatter().printHelp(BalanceVerifier.class.getSimpleName(), options, true);
	}

}
