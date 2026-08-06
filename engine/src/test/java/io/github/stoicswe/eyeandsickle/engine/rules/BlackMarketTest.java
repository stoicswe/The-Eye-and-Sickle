package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.MessageState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * When the darknet vendor makes contact, and the inbox that carries it.
 *
 * <p>The condition is a heat-state gate on a <b>vendor</b> ({@code docs/design/02} §2.5), not a gate
 * on any item — so what these check is reachability, delivered exactly once, and the entitlement the
 * message carries being claimable exactly once.
 */
class BlackMarketTest {

    private static final Instant T0 = Instant.parse("2026-08-06T12:00:00Z");

    private static GameSave rig(int eye, int sickle, int heat) {
        GameSave s = new GameSave();
        s.factionReputationEye = eye;
        s.factionReputationSickle = sickle;
        s.personalHeat = heat;
        return s;
    }

    private static final int REP = Balance.BLACK_MARKET_MIN_REPUTATION;
    private static final int HEAT = Balance.BLACK_MARKET_MIN_HEAT;

    @Nested
    @DisplayName("being noticed")
    class Noticed {

        @Test
        @DisplayName("takes standing AND heat, not either one")
        void bothConditions() {
            assertThat(BlackMarket.noticed(rig(REP, 0, HEAT))).isTrue();

            assertThat(BlackMarket.noticed(rig(REP, 0, HEAT - 1)))
                    .as("standing alone would make this a reputation gate wearing a different hat")
                    .isFalse();
            assertThat(BlackMarket.noticed(rig(REP - 1, 0, HEAT)))
                    .as("heat alone would hand the darknet to anybody careless")
                    .isFalse();
        }

        /**
         * ⚠ <b>The BETTER of the two standings, never their sum.</b>
         *
         * <p>A committed Sickle operative and a committed Eye operative are each somebody worth
         * knowing. Summing them would let a fence-sitter qualify on the strength of neither — and it
         * would quietly make the two faction reputations one pooled number, which {@code CLAUDE.md}
         * and the glossary both forbid.
         */
        @Test
        @DisplayName("standing is the better faction, never the two added together")
        void standingIsNotPooled() {
            int half = REP / 2 + 1;
            assertThat(BlackMarket.noticed(rig(half, half, HEAT)))
                    .as("two middling standings that happen to sum past the line must NOT qualify")
                    .isFalse();

            assertThat(BlackMarket.noticed(rig(0, REP, HEAT)))
                    .as("either faction alone is enough")
                    .isTrue();
            assertThat(BlackMarket.noticed(rig(REP, 0, HEAT))).isTrue();
        }

        /** ⚠ Heat is a FLOOR here — §2.5's broker wants you hunted, which inverts every other gate. */
        @Test
        @DisplayName("more heat than the threshold still qualifies")
        void heatIsAFloorNotACeiling() {
            assertThat(BlackMarket.noticed(rig(REP, 0, Balance.PERSONAL_HEAT_MAX))).isTrue();
        }
    }

    @Nested
    @DisplayName("contact")
    class Contact {

        @Test
        @DisplayName("arrives once, and never again")
        void deliveredOnce() {
            GameSave s = rig(REP, 0, HEAT);

            assertThat(BlackMarket.contactIfDue(s, T0)).isNotNull();
            assertThat(s.messages).hasSize(1);

            assertThat(BlackMarket.contactIfDue(s, T0.plusSeconds(60)))
                    .as("a second tick must not send it again")
                    .isNull();
            assertThat(s.messages).hasSize(1);
        }

        /**
         * ⚠ Keyed on the MESSAGE, not on a flag, and this is the case that proves why.
         *
         * <p>Standing and heat both move in both directions and can cross several times in a
         * session. A boolean "sent" flag would be a second place for that fact to live; looking for
         * the message means there is nothing to fall out of step with the inbox.
         */
        @Test
        @DisplayName("going cold and hot again does not re-send it")
        void crossingTwiceSendsOnce() {
            GameSave s = rig(REP, 0, HEAT);
            BlackMarket.contactIfDue(s, T0);

            s.personalHeat = 0;
            assertThat(BlackMarket.contactIfDue(s, T0.plusSeconds(10))).isNull();

            s.personalHeat = HEAT;
            assertThat(BlackMarket.contactIfDue(s, T0.plusSeconds(20))).isNull();
            assertThat(s.messages).hasSize(1);
        }

        @Test
        @DisplayName("nothing arrives for somebody nobody has noticed")
        void quietForTheUnnoticed() {
            GameSave s = rig(0, 0, 0);
            assertThat(BlackMarket.contactIfDue(s, T0)).isNull();
            assertThat(s.messages).isEmpty();
        }

        @Test
        @DisplayName("the notice carries the module, and carries it unclaimed")
        void carriesTheModule() {
            GameSave s = rig(REP, 0, HEAT);
            MessageState m = BlackMarket.contactIfDue(s, T0);

            assertThat(m.offerItemType).isEqualTo(Catalogue.TOR_MODULE);
            assertThat(m.offerClaimed).isFalse();
            assertThat(m.read).isFalse();
        }
    }

    @Nested
    @DisplayName("the inbox")
    class InboxRules {

        @Test
        @DisplayName("an offer can be claimed exactly once")
        void claimIsSingleUse() {
            GameSave s = rig(REP, 0, HEAT);
            MessageState m = BlackMarket.contactIfDue(s, T0);

            assertThat(Inbox.claim(s, m.messageId)).contains(Catalogue.TOR_MODULE);
            assertThat(Inbox.claim(s, m.messageId))
                    .as("a second claim would be an item printer")
                    .isEmpty();
        }

        @Test
        @DisplayName("claiming marks it read — you cannot collect without having opened it")
        void claimingReads() {
            GameSave s = rig(REP, 0, HEAT);
            MessageState m = BlackMarket.contactIfDue(s, T0);
            Inbox.claim(s, m.messageId);
            assertThat(m.read).isTrue();
        }

        /**
         * ⚠ Trimming is a size bound on HISTORY; an unclaimed offer is an ENTITLEMENT.
         *
         * <p>Dropping one to stay under a display limit would silently delete something the player
         * was given and had not collected — and they would have no way to know it had happened.
         */
        @Test
        @DisplayName("a message holding an unclaimed offer is never trimmed away")
        void trimmingSparesEntitlements() {
            GameSave s = rig(REP, 0, HEAT);
            MessageState offer = BlackMarket.contactIfDue(s, T0);

            for (int i = 0; i < Inbox.LIMIT + 50; i++) {
                MessageState filler = new MessageState();
                filler.subject = "filler " + i;
                filler.receivedAt = T0.plusSeconds(i + 1);
                Inbox.deliver(s, filler);
            }

            assertThat(s.messages).hasSize(Inbox.LIMIT);
            assertThat(s.messages)
                    .as("the entitlement survived a flood of ordinary messages")
                    .anyMatch(m -> m.messageId.equals(offer.messageId));
        }

        @Test
        @DisplayName("unread counts what is unread, and newest is first")
        void unreadAndOrder() {
            GameSave s = new GameSave();
            for (int i = 0; i < 3; i++) {
                MessageState m = new MessageState();
                m.subject = "m" + i;
                m.receivedAt = T0.plusSeconds(i);
                Inbox.deliver(s, m);
            }
            assertThat(Inbox.unread(s)).isEqualTo(3);
            assertThat(Inbox.newestFirst(s).getFirst().subject).isEqualTo("m2");

            Inbox.markRead(s, Inbox.newestFirst(s).getFirst().messageId);
            assertThat(Inbox.unread(s)).isEqualTo(2);
            assertThat(Inbox.markRead(s, Inbox.newestFirst(s).getFirst().messageId))
                    .as("re-reading changes nothing, so nothing is persisted for it")
                    .isFalse();
        }
    }
}
