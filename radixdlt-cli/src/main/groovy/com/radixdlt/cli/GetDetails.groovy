package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import picocli.CommandLine

@CommandLine.Command(name = "get-details", mixinStandardHelpOptions = true,
        description = "Get details such as address, public key, native token ref")
class GetDetails implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = Utils.getAPI(identityInfo)

        println("My address:\t " + api.getAddress())
        println("My public key:\t " + api.getPublicKey())
        println("Native token ref:\t " + api.getNativeTokenRef().toString())
        System.exit(0)

    }

}