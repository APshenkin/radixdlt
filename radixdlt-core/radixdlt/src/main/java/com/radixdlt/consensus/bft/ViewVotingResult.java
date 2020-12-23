/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;

import java.util.Objects;

/**
 * The result of a view voting (either QC or TC).
 */
public interface ViewVotingResult {

    static FormedQC qc(QuorumCertificate qc) {
        return new FormedQC(qc);
    }

    static FormedTC tc(TimeoutCertificate tc) {
        return new FormedTC(tc);
    }

    View getView();

    /**
     * Signifies that the view has been completed with a formed quorum certificate.
     */
    final class FormedQC implements ViewVotingResult {

        private final QuorumCertificate qc;

        public FormedQC(QuorumCertificate qc) {
            this.qc = qc;
        }

        public QuorumCertificate getQC() {
            return this.qc;
        }

        @Override
        public View getView() {
            return this.qc.getView();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FormedQC formedQC = (FormedQC) o;
            return Objects.equals(qc, formedQC.qc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qc);
        }

        @Override
        public String toString() {
            return String.format("FormedQC{qc=%s}", qc);
        }
    }

    /**
     * Signifies that the view has been completed with a timeout certificate.
     */
    final class FormedTC implements ViewVotingResult {

        private final TimeoutCertificate tc;

        public FormedTC(TimeoutCertificate tc) {
            this.tc = tc;
        }

        public TimeoutCertificate getTC() {
            return this.tc;
        }

        @Override
        public View getView() {
            return this.tc.getView();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FormedTC formedTC = (FormedTC) o;
            return Objects.equals(tc, formedTC.tc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tc);
        }

        @Override
        public String toString() {
            return String.format("FormedTC{tc=%s}", tc);
        }
    }
}
