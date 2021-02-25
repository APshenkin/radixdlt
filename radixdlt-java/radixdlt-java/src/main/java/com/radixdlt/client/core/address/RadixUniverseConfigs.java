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

package com.radixdlt.client.core.address;

import java.io.InputStream;

public final class RadixUniverseConfigs {

    private RadixUniverseConfigs() { }

    public static RadixUniverseConfig getLocalnet() {
        //TODO: stream is left open
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("localnet.json"));
    }

    public static RadixUniverseConfig getBetanet() {
        //TODO: stream is left open
        return RadixUniverseConfig.fromInputStream(getConfigFileStream("betanet.json"));
    }

    private static InputStream getConfigFileStream(String name) {
        var source = "/universe/" + name;
        var configFileStream = RadixUniverseConfig.class.getResourceAsStream(source);

        if (configFileStream == null) {
            throw new RuntimeException("Config file from " + source + " not found");
        }

        return configFileStream;
    }
}