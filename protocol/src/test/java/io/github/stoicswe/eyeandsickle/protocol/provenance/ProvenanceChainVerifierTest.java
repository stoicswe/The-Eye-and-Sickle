package io.github.stoicswe.eyeandsickle.protocol.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainFault.Reason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every check in {@code docs/architecture/04-item-provenance.md} §7, proved to fire, one failure
 * mode at a time.
 *
 * <p>This is the module's most security-critical code, and the only reason to trust it is that each
 * way of cheating has a test with that cheat's name on it. Most of these tests assert the
 * <em>exact</em> set of faults rather than merely "rejected", because a check that happens to reject
 * a forgery for the wrong reason is a check that will stop rejecting it the moment the forgery is
 * tidied up.
 */
class ProvenanceChainVerifierTest {

    private final ChainFixture fixture = new ChainFixture();

    /** A mint followed by a duel outcome, with the committee's signatures over the outcome. */
    private List<ProvenanceEnvelope> duelChain(List<String> signingValidators) {
        ProvenancePayload genesis = fixture.genesis();
        ProvenancePayload duel = ChainFixture.Edit.of(fixture.following(
                        genesis, ProvenanceEventType.DUEL_GRANT, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER))
                .issuerDid("duel:" + ChainFixture.DUEL_ID)
                .build();
        return List.of(fixture.singleIssuer(genesis), fixture.quorum(duel, signingValidators));
    }

    private ChainVerificationContext contextFor(QuorumCommittee committee) {
        return fixture.context(DuelCommitteeLookup.ofMap(Map.of(ChainFixture.DUEL_ID, committee)));
    }

    private static QuorumCommittee weighted(double... weights) {
        Map<String, Double> sampled = new LinkedHashMap<>();
        List<String> dids = ChainFixture.validators(weights.length);
        for (int i = 0; i < weights.length; i++) {
            sampled.put(dids.get(i), weights[i]);
        }
        return new QuorumCommittee(ChainFixture.DUEL_ID, sampled);
    }

    // ------------------------------------------------------------------ the happy paths

    @Nested
    @DisplayName("a sound chain")
    class SoundChains {

        @Test
        @DisplayName("of a single genesis record is recognized")
        void singleRecordChain() {
            ChainVerdict verdict = ProvenanceChainVerifier.verify(fixture.validChain(1), fixture.context());

            assertThat(verdict.isRecognized()).isTrue();
            assertThat(verdict.faults()).isEmpty();
        }

        @Test
        @DisplayName("of several single-issuer records is recognized")
        void multiRecordChain() {
            assertThat(ProvenanceChainVerifier.verify(fixture.validChain(5), fixture.context())
                            .isRecognized())
                    .isTrue();
        }

        @Test
        @DisplayName("of twelve records is recognized — length is not a limit")
        void longChain() {
            assertThat(ProvenanceChainVerifier.verify(fixture.validChain(12), fixture.context())
                            .isRecognized())
                    .isTrue();
        }

        @Test
        @DisplayName("with a duel outcome signed by 5 of 7 sampled validators is recognized")
        void duelOutcomeWithAQuorum() {
            List<String> committee = ChainFixture.validators(7);
            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    duelChain(committee.subList(0, 5)),
                    contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.isRecognized()).isTrue();
        }

        @Test
        @DisplayName("continues past a duel outcome — the chain links across the handover")
        void chainContinuesAfterADuel() {
            List<String> committee = ChainFixture.validators(7);
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload duel = ChainFixture.Edit.of(fixture.following(
                            genesis,
                            ProvenanceEventType.DUEL_GRANT,
                            ChainFixture.OTHER_HOLDER,
                            ChainFixture.HOME_SERVER))
                    .issuerDid("duel:" + ChainFixture.DUEL_ID)
                    .build();
            ProvenancePayload afterwards =
                    fixture.following(duel, ProvenanceEventType.TRADE, ChainFixture.HOLDER, ChainFixture.HOME_SERVER);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(
                            fixture.singleIssuer(genesis),
                            fixture.quorum(duel, committee.subList(0, 5)),
                            fixture.singleIssuer(afterwards)),
                    contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.faults()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ shape

    @Nested
    @DisplayName("chain shape")
    class ChainShape {

        @Test
        @DisplayName("an empty chain proves nothing and is not recognized")
        void emptyChain() {
            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(), fixture.context());

            assertThat(verdict.isRecognized()).isFalse();
            assertThat(verdict.reasons()).containsExactly(Reason.EMPTY_CHAIN);
        }

        @Test
        @DisplayName("a record belonging to another item is rejected — chains are per-item")
        void itemIdMismatch() {
            List<ProvenanceEnvelope> chain = fixture.validChain(2);
            ProvenancePayload smuggled = ChainFixture.Edit.of(chain.get(1).payload())
                    .itemId(UUID.fromString("11111111-2222-4333-8444-555555555555"))
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 1, fixture.singleIssuer(smuggled)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.ITEM_ID_MISMATCH);
        }

        @Test
        @DisplayName("an envelope canonicalized by some other scheme is not recognized")
        void unsupportedCanonicalization() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope envelope = new ProvenanceEnvelope(
                    genesis, "COSE-CBOR", List.of(fixture.sign(genesis, ChainFixture.HOME_SERVER)));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(envelope), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.UNSUPPORTED_CANONICALIZATION);
        }

        @Test
        @DisplayName("a payload schema this build cannot read is a payload it cannot vouch for")
        void unsupportedRecordVersion() {
            ProvenancePayload future =
                    ChainFixture.Edit.of(fixture.genesis()).recordVersion(2).build();

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(List.of(fixture.singleIssuer(future)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.UNSUPPORTED_RECORD_VERSION);
        }
    }

    // ------------------------------------------------------------------ genesis and depth

    @Nested
    @DisplayName("genesis and position")
    class GenesisAndPosition {

        @Test
        @DisplayName("a chain that does not start at genesis cannot be walked back to one")
        void missingGenesis() {
            List<ProvenanceEnvelope> chain = fixture.validChain(4);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(chain.subList(1, 4), fixture.context());

            assertThat(verdict.isRecognized()).isFalse();
            assertThat(verdict.reasons()).contains(Reason.MISSING_GENESIS);
        }

        @Test
        @DisplayName("a chain beginning with anything but an initial_mint is not recognized")
        void genesisMustBeAMint() {
            ProvenancePayload notAMint = ChainFixture.Edit.of(fixture.genesis())
                    .eventType(ProvenanceEventType.SERVER_GRANT)
                    .build();

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(List.of(fixture.singleIssuer(notAMint)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.GENESIS_NOT_INITIAL_MINT);
        }

        @Test
        @DisplayName("an item minted a second time part-way along is not recognized")
        void mintAppearsOnlyAtGenesis() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            ProvenancePayload remint = ChainFixture.Edit.of(chain.get(2).payload())
                    .eventType(ProvenanceEventType.INITIAL_MINT)
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 2, fixture.singleIssuer(remint)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.NON_GENESIS_MINT);
        }

        @Test
        @DisplayName("a gap in chainDepth means records are missing from the walk")
        void depthGap() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload jumped = ChainFixture.Edit.of(fixture.following(
                            genesis, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER))
                    .chainDepth(2)
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(fixture.singleIssuer(genesis), fixture.singleIssuer(jumped)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.CHAIN_DEPTH_GAP);
        }

        @Test
        @DisplayName("a repeated or backwards chainDepth is not recognized")
        void depthOutOfOrder() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            ProvenancePayload repeated =
                    ChainFixture.Edit.of(chain.get(2).payload()).chainDepth(1).build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 2, fixture.singleIssuer(repeated)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.CHAIN_DEPTH_OUT_OF_ORDER);
        }
    }

    // ------------------------------------------------------------------ linkage

    @Nested
    @DisplayName("hash linkage")
    class HashLinkage {

        @Test
        @DisplayName("a record naming the wrong predecessor breaks the chain")
        void brokenHashLink() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            // Points past its actual predecessor, straight at genesis — the shape a spliced-out
            // record leaves behind.
            ProvenancePayload spliced = ChainFixture.Edit.of(chain.get(2).payload())
                    .prevRecordHash(RecordHash.of(chain.get(0).payload()))
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 2, fixture.singleIssuer(spliced)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.BROKEN_HASH_LINK);
        }

        @Test
        @DisplayName("a record tampered with at any position in a long chain is caught")
        void tamperingAnywhereIsCaught() {
            List<ProvenanceEnvelope> chain = fixture.validChain(8);
            ChainVerificationContext context = fixture.context();
            assertThat(ProvenanceChainVerifier.verify(chain, context).isRecognized())
                    .as("the untouched chain must verify, or this test proves nothing")
                    .isTrue();

            for (int position = 0; position < chain.size(); position++) {
                ProvenanceEnvelope original = chain.get(position);
                ProvenancePayload buffed = ChainFixture.Edit.of(original.payload())
                        .itemAttrs(Map.of("power", 9001, "durability", 0.87))
                        .build();
                // The classic attack: rewrite the stats, keep the issuer's old signature.
                List<ProvenanceEnvelope> tampered = ChainFixture.replacing(
                        chain,
                        position,
                        new ProvenanceEnvelope(buffed, ProvenanceEnvelope.JCS_RFC8785, original.signatures()));

                ChainVerdict verdict = ProvenanceChainVerifier.verify(tampered, context);

                assertThat(verdict.isRecognized())
                        .as("tampering at position %d", position)
                        .isFalse();
                assertThat(verdict.hasFault(Reason.INVALID_SIGNATURE))
                        .as("tampering at position %d must break that record's signature", position)
                        .isTrue();
                if (position < chain.size() - 1) {
                    assertThat(verdict.hasFault(Reason.BROKEN_HASH_LINK))
                            .as("tampering at position %d must also break the next record's link", position)
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("a sound tip does not rescue a history issued by an unauthorized server")
        void aSoundTipDoesNotRescueABrokenHistory() {
            ProvenancePayload genesis = ChainFixture.Edit.of(fixture.genesis())
                    .issuerDid(ChainFixture.ROGUE_SERVER)
                    .build();
            ProvenancePayload second = fixture.following(
                    genesis, ProvenanceEventType.TRADE, ChainFixture.HOLDER, ChainFixture.ROGUE_SERVER);
            ProvenancePayload tip = fixture.following(
                    second, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(fixture.singleIssuer(genesis), fixture.singleIssuer(second), fixture.singleIssuer(tip)),
                    fixture.context());

            assertThat(verdict.isRecognized()).isFalse();
            assertThat(verdict.reasons()).containsExactly(Reason.UNAUTHORIZED_ISSUER, Reason.UNAUTHORIZED_ISSUER);
            assertThat(verdict.faults()).extracting(ChainFault::position).containsExactly(0, 1);
        }
    }

    // ------------------------------------------------------------------ single-issuer authority

    @Nested
    @DisplayName("single-issuer authority")
    class SingleIssuerAuthority {

        @Test
        @DisplayName("a server with no authority over this item cannot grant it")
        void unauthorizedIssuer() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload grabbed = fixture.following(
                    genesis, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.ROGUE_SERVER);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(fixture.singleIssuer(genesis), fixture.singleIssuer(grabbed)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.UNAUTHORIZED_ISSUER);
        }

        @Test
        @DisplayName("a signature by someone other than the named issuer is not the issuer's word")
        void signerIsNotTheIssuer() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope envelope =
                    ProvenanceEnvelope.singleIssuer(genesis, fixture.sign(genesis, ChainFixture.ROGUE_SERVER));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(envelope), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.SIGNER_NOT_ISSUER);
        }

        @Test
        @DisplayName("a key that cannot be resolved leaves the record unverifiable")
        void unknownSigningKey() {
            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    fixture.validChain(2), fixture.contextWithKeys(SigningKeyDirectory.empty()));

            assertThat(verdict.reasons()).containsExactly(Reason.UNKNOWN_SIGNING_KEY, Reason.UNKNOWN_SIGNING_KEY);
        }

        @Test
        @DisplayName("rewriting a record's stats invalidates the issuer's signature")
        void tamperedPayload() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope honest = fixture.singleIssuer(genesis);
            ProvenancePayload buffed = ChainFixture.Edit.of(genesis)
                    .itemAttrs(Map.of("power", 9001, "durability", 0.87))
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(new ProvenanceEnvelope(buffed, ProvenanceEnvelope.JCS_RFC8785, honest.signatures())),
                    fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.INVALID_SIGNATURE);
        }

        @Test
        @DisplayName("a signature that is not decodable base64url is not a signature")
        void malformedSignature() {
            ProvenancePayload genesis = fixture.genesis();
            // Signed honestly first so the issuer's key is resolvable — otherwise this would fail
            // as an unknown key and prove nothing about decoding.
            SignatureBlock honest = fixture.sign(genesis, ChainFixture.HOME_SERVER);
            ProvenanceEnvelope envelope =
                    ProvenanceEnvelope.singleIssuer(genesis, SignatureBlock.eddsa(honest.kid(), "not base64!!"));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(envelope), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.MALFORMED_SIGNATURE);
        }

        @Test
        @DisplayName("provenance is signed with EdDSA and nothing else")
        void wrongAlgorithm() {
            ProvenancePayload genesis = fixture.genesis();
            SignatureBlock honest = fixture.sign(genesis, ChainFixture.HOME_SERVER);
            ProvenanceEnvelope envelope =
                    ProvenanceEnvelope.singleIssuer(genesis, new SignatureBlock("RS256", honest.kid(), honest.sig()));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(envelope), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.WRONG_SIGNATURE_ALGORITHM);
        }

        @Test
        @DisplayName("a single-issuer event carrying a committee's signatures is not recognized")
        void unexpectedMultiSignature() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope envelope = new ProvenanceEnvelope(
                    genesis,
                    ProvenanceEnvelope.JCS_RFC8785,
                    List.of(
                            fixture.sign(genesis, ChainFixture.HOME_SERVER),
                            fixture.sign(genesis, ChainFixture.ROGUE_SERVER)));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(List.of(envelope), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.UNEXPECTED_MULTI_SIGNATURE);
        }
    }

    // ------------------------------------------------------------------ duel quorum

    @Nested
    @DisplayName("duel outcomes and the validator quorum")
    class DuelQuorum {

        @Test
        @DisplayName("a duel outcome issued by a single DID is not a quorum decision")
        void malformedQuorumIssuer() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload duel = fixture.following(
                    genesis, ProvenanceEventType.DUEL_GRANT, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER);
            List<String> committee = ChainFixture.validators(7);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(fixture.singleIssuer(genesis), fixture.quorum(duel, committee.subList(0, 5))),
                    contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.reasons()).containsExactly(Reason.MALFORMED_QUORUM_ISSUER);
        }

        @Test
        @DisplayName("without the sampling record a real quorum and five fresh keys look the same")
        void unknownCommittee() {
            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    duelChain(ChainFixture.validators(5)), fixture.context(DuelCommitteeLookup.none()));

            assertThat(verdict.reasons()).containsExactly(Reason.UNKNOWN_DUEL_COMMITTEE);
        }

        @Test
        @DisplayName("a validator that was never sampled has no authority over the outcome")
        void validatorNotSampled() {
            List<String> committee = ChainFixture.validators(7);
            List<String> signers = new ArrayList<>(committee.subList(0, 5));
            signers.add("did:plc:validator8"); // opted in somewhere, but not drawn for this duel

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    duelChain(signers), contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.reasons()).containsExactly(Reason.VALIDATOR_NOT_SAMPLED);
        }

        @Test
        @DisplayName("four of seven does not clear the 2f+1-of-3f+1 threshold")
        void quorumBelowThreshold() {
            List<String> committee = ChainFixture.validators(7);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    duelChain(committee.subList(0, 4)),
                    contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.reasons()).containsExactly(Reason.QUORUM_NOT_REACHED);
            assertThat(verdict.firstFault().orElseThrow().detail()).contains("4 of 7");
        }

        @Test
        @DisplayName("one validator signing twice cannot double-count its weight")
        void duplicateValidatorSignature() {
            List<String> committee = ChainFixture.validators(7);
            List<String> signers = List.of(
                    "did:plc:validator1",
                    "did:plc:validator1",
                    "did:plc:validator2",
                    "did:plc:validator3",
                    "did:plc:validator4");

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    duelChain(signers), contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.reasons())
                    .containsExactly(Reason.DUPLICATE_VALIDATOR_SIGNATURE, Reason.QUORUM_NOT_REACHED);
        }

        @Test
        @DisplayName("enough signatures but too little reputation-weight is not consensus")
        void weightIsEnforcedNotJustCount() {
            // One validator holds most of the committee's reputation. Five of the light ones agreeing
            // is five signatures but nowhere near five sevenths of the sampled power.
            QuorumCommittee lopsided = weighted(10, 1, 1, 1, 1, 1, 1);
            List<String> lightValidators = ChainFixture.validators(7).subList(1, 6);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(duelChain(lightValidators), contextFor(lopsided));

            assertThat(verdict.reasons()).containsExactly(Reason.QUORUM_NOT_REACHED);
        }

        @Test
        @DisplayName("[PROPOSAL] enough weight but too few validators is not consensus either")
        void countIsEnforcedNotJustWeight() {
            // A single validator holding most of the reputation would otherwise decide a
            // cross-server outcome alone, which is exactly what Invariant I15 forbids. The docs
            // speak only of weight; see ProvenanceChainVerifier for why this build requires both.
            QuorumCommittee dominated = weighted(100, 1, 1, 1, 1, 1, 1);

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(duelChain(List.of("did:plc:validator1")), contextFor(dominated));

            assertThat(verdict.reasons()).containsExactly(Reason.QUORUM_NOT_REACHED);
        }

        @Test
        @DisplayName("a forged validator signature is caught even when the rest of the quorum is real")
        void forgedValidatorSignature() {
            List<String> committee = ChainFixture.validators(7);
            List<ProvenanceEnvelope> chain = duelChain(committee.subList(0, 5));
            ProvenanceEnvelope duel = chain.get(1);
            // validator6 was sampled and has a resolvable key, but never signed. Re-pointing
            // validator1's signature at validator6's kid must fail as an invalid signature, not as
            // an unknown key — so its key has to exist for this test to mean anything.
            fixture.keysOf("did:plc:validator6");
            List<SignatureBlock> tampered = new ArrayList<>(duel.signatures());
            tampered.set(
                    0,
                    SignatureBlock.eddsa(
                            ChainFixture.kidOf("did:plc:validator6"),
                            duel.signatures().getFirst().sig()));

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(
                            chain, 1, new ProvenanceEnvelope(duel.payload(), ProvenanceEnvelope.JCS_RFC8785, tampered)),
                    contextFor(ChainFixture.equallyWeighted(ChainFixture.DUEL_ID, committee)));

            assertThat(verdict.reasons()).containsExactly(Reason.INVALID_SIGNATURE, Reason.QUORUM_NOT_REACHED);
        }
    }

    // ------------------------------------------------------------------ replay protection

    @Nested
    @DisplayName("replay protection")
    class ReplayProtection {

        @Test
        @DisplayName("a nonce reused within a chain is what a replayed record looks like")
        void reusedNonce() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            ProvenancePayload replayed = ChainFixture.Edit.of(chain.get(2).payload())
                    .nonce(chain.get(1).payload().nonce())
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 2, fixture.singleIssuer(replayed)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.REPLAYED_NONCE);
        }

        @Test
        @DisplayName("an item's history cannot run backwards")
        void backwardsTimestamp() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            ProvenancePayload backdated = ChainFixture.Edit.of(chain.get(2).payload())
                    .timestamp("2026-06-01T00:00:00Z")
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 2, fixture.singleIssuer(backdated)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.TIMESTAMP_NOT_MONOTONIC);
        }

        @Test
        @DisplayName("a record dated far in the future is not plausible")
        void farFutureTimestamp() {
            ProvenancePayload postDated = ChainFixture.Edit.of(fixture.genesis())
                    .timestamp(ChainFixture.NOW.plus(Duration.ofDays(1)).toString())
                    .build();

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(List.of(fixture.singleIssuer(postDated)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.TIMESTAMP_IN_FUTURE);
        }

        @Test
        @DisplayName("a record inside the tolerated skew is accepted — self-hosted clocks drift")
        void withinSkewIsAccepted() {
            ProvenancePayload slightlyAhead = ChainFixture.Edit.of(fixture.genesis())
                    .timestamp(ChainFixture.NOW.plus(Duration.ofMinutes(2)).toString())
                    .build();

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(List.of(fixture.singleIssuer(slightlyAhead)), fixture.context());

            assertThat(verdict.isRecognized()).isTrue();
        }

        @Test
        @DisplayName("an unparseable timestamp is reported rather than ignored")
        void malformedTimestamp() {
            ProvenancePayload vague = ChainFixture.Edit.of(fixture.genesis())
                    .timestamp("yesterday afternoon")
                    .build();

            ChainVerdict verdict =
                    ProvenanceChainVerifier.verify(List.of(fixture.singleIssuer(vague)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.MALFORMED_TIMESTAMP);
        }

        @Test
        @DisplayName("one unreadable timestamp does not cascade into false ordering faults")
        void malformedTimestampDoesNotCascade() {
            // Built as a real chain around the bad record rather than by editing one in place, so the
            // only thing wrong with it is the timestamp — otherwise the successor's broken link would
            // be doing the work and this would prove nothing about ordering.
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload vague = ChainFixture.Edit.of(fixture.following(
                            genesis, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER))
                    .timestamp("soon")
                    .build();
            ProvenancePayload afterwards =
                    fixture.following(vague, ProvenanceEventType.TRADE, ChainFixture.HOLDER, ChainFixture.HOME_SERVER);

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    List.of(
                            fixture.singleIssuer(genesis),
                            fixture.singleIssuer(vague),
                            fixture.singleIssuer(afterwards)),
                    fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.MALFORMED_TIMESTAMP);
        }
    }

    // ------------------------------------------------------------------ the verdict itself

    @Nested
    @DisplayName("the verdict")
    class Verdict {

        @Test
        @DisplayName("names the failing record by position and by claimed depth")
        void namesTheFailingRecord() {
            List<ProvenanceEnvelope> chain = fixture.validChain(4);
            ProvenancePayload spliced = ChainFixture.Edit.of(chain.get(3).payload())
                    .prevRecordHash(RecordHash.of(chain.get(0).payload()))
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 3, fixture.singleIssuer(spliced)), fixture.context());

            assertThat(verdict.firstFault()).get().satisfies(fault -> {
                assertThat(fault.position()).isEqualTo(3);
                assertThat(fault.chainDepth()).isEqualTo(3);
                assertThat(fault.reason()).isEqualTo(Reason.BROKEN_HASH_LINK);
                assertThat(fault.detail()).isNotBlank();
            });
        }

        @Test
        @DisplayName("reports every fault it finds, not just the first")
        void reportsEveryFault() {
            List<ProvenanceEnvelope> chain = fixture.validChain(3);
            ProvenanceEnvelope original = chain.get(1);
            ProvenancePayload buffed = ChainFixture.Edit.of(original.payload())
                    .itemAttrs(Map.of("power", 9001, "durability", 0.87))
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(
                            chain,
                            1,
                            new ProvenanceEnvelope(buffed, ProvenanceEnvelope.JCS_RFC8785, original.signatures())),
                    fixture.context());

            // Both faults together are the signature of an *edited* record, as opposed to a rotated
            // key (which would break only the signature) or a spliced chain (only the link).
            assertThat(verdict.reasons()).containsExactly(Reason.INVALID_SIGNATURE, Reason.BROKEN_HASH_LINK);
        }

        @Test
        @DisplayName("reads legibly enough to put in a dispute log")
        void readsLegibly() {
            List<ProvenanceEnvelope> chain = fixture.validChain(2);
            // A second record posing as a genesis: depth 0 and no predecessor, which ProvenancePayload
            // insists must travel together.
            ProvenancePayload posing = ChainFixture.Edit.of(chain.get(1).payload())
                    .chainDepth(0)
                    .prevRecordHash(null)
                    .build();

            ChainVerdict verdict = ProvenanceChainVerifier.verify(
                    ChainFixture.replacing(chain, 1, fixture.singleIssuer(posing)), fixture.context());

            assertThat(verdict.reasons()).containsExactly(Reason.CHAIN_DEPTH_OUT_OF_ORDER, Reason.BROKEN_HASH_LINK);
            assertThat(verdict.firstFault().orElseThrow().toString())
                    .contains("record #1")
                    .contains("CHAIN_DEPTH_OUT_OF_ORDER");
        }
    }
}
