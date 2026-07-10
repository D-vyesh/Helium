package com.helium.core.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawalQueueItemTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void followsTheDurableApprovalAndSigningPath() {
        WithdrawalQueueItem item = WithdrawalQueueItem.enqueue(UUID.randomUUID(), NOW);

        item.transitionTo(WithdrawalQueueStatus.VALIDATING, "authorization complete", NOW.plusSeconds(1));
        item.transitionTo(WithdrawalQueueStatus.APPROVED, "maker checker approved", NOW.plusSeconds(2));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGN, "signer queue", NOW.plusSeconds(3));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.WAITING_SIGN);
    }

    @Test
    void rejectsAJumpToBroadcastingBeforeSigning() {
        WithdrawalQueueItem item = WithdrawalQueueItem.enqueue(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> item.transitionTo(WithdrawalQueueStatus.BROADCASTING, "invalid", NOW.plusSeconds(1)))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("cannot transition");
    }

    @Test
    void persistsARecoverableBuildFailureBeforeTheSignerStage() {
        WithdrawalQueueItem item = WithdrawalQueueItem.enqueue(UUID.randomUUID(), NOW);

        item.transitionTo(WithdrawalQueueStatus.VALIDATING, "authorization complete", NOW.plusSeconds(1));
        item.transitionTo(WithdrawalQueueStatus.APPROVED, "maker checker approved", NOW.plusSeconds(2));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGN, "builder queue", NOW.plusSeconds(3));
        item.transitionTo(WithdrawalQueueStatus.BUILDING_TRANSACTION, "building", NOW.plusSeconds(4));
        item.recordBuildFailure("validator unavailable", NOW.plusSeconds(9), NOW.plusSeconds(5));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.BUILD_FAILED);
        assertThat(item.buildAttempts()).isEqualTo(1);
        assertThat(item.nextBuildAttemptAt()).isEqualTo(NOW.plusSeconds(9));
        assertThat(item.lastError()).isEqualTo("validator unavailable");

        item.transitionTo(WithdrawalQueueStatus.BUILDING_TRANSACTION, "retry", NOW.plusSeconds(10));
        item.transitionTo(WithdrawalQueueStatus.TRANSACTION_BUILT, "draft stored", NOW.plusSeconds(11));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGNER, "custody signer", NOW.plusSeconds(12));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.WAITING_SIGNER);
    }

    @Test
    void persistsARecoverableSignFailureBeforeBroadcasting() {
        WithdrawalQueueItem item = WithdrawalQueueItem.enqueue(UUID.randomUUID(), NOW);

        item.transitionTo(WithdrawalQueueStatus.VALIDATING, "authorization complete", NOW.plusSeconds(1));
        item.transitionTo(WithdrawalQueueStatus.APPROVED, "maker checker approved", NOW.plusSeconds(2));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGN, "builder queue", NOW.plusSeconds(3));
        item.transitionTo(WithdrawalQueueStatus.BUILDING_TRANSACTION, "building", NOW.plusSeconds(4));
        item.transitionTo(WithdrawalQueueStatus.TRANSACTION_BUILT, "draft stored", NOW.plusSeconds(5));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGNER, "custody signer", NOW.plusSeconds(6));
        item.transitionTo(WithdrawalQueueStatus.SIGNING, "signing", NOW.plusSeconds(7));
        item.recordSignFailure("kms unavailable", NOW.plusSeconds(12), NOW.plusSeconds(8));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.SIGN_FAILED);
        assertThat(item.signAttempts()).isEqualTo(1);
        assertThat(item.nextSignAttemptAt()).isEqualTo(NOW.plusSeconds(12));
        assertThat(item.lastError()).isEqualTo("kms unavailable");

        item.transitionTo(WithdrawalQueueStatus.SIGNING, "retry", NOW.plusSeconds(13));
        item.transitionTo(WithdrawalQueueStatus.SIGNED, "signed", NOW.plusSeconds(14));
        item.transitionTo(WithdrawalQueueStatus.WAITING_BROADCAST, "broadcast queue", NOW.plusSeconds(15));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.WAITING_BROADCAST);
    }

    @Test
    void tracksBroadcastAndConfirmationRetriesWithoutSkippingStates() {
        WithdrawalQueueItem item = WithdrawalQueueItem.enqueue(UUID.randomUUID(), NOW);

        item.transitionTo(WithdrawalQueueStatus.VALIDATING, "authorization complete", NOW.plusSeconds(1));
        item.transitionTo(WithdrawalQueueStatus.APPROVED, "maker checker approved", NOW.plusSeconds(2));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGN, "builder queue", NOW.plusSeconds(3));
        item.transitionTo(WithdrawalQueueStatus.BUILDING_TRANSACTION, "building", NOW.plusSeconds(4));
        item.transitionTo(WithdrawalQueueStatus.TRANSACTION_BUILT, "draft stored", NOW.plusSeconds(5));
        item.transitionTo(WithdrawalQueueStatus.WAITING_SIGNER, "custody signer", NOW.plusSeconds(6));
        item.transitionTo(WithdrawalQueueStatus.SIGNING, "signing", NOW.plusSeconds(7));
        item.transitionTo(WithdrawalQueueStatus.SIGNED, "signed", NOW.plusSeconds(8));
        item.transitionTo(WithdrawalQueueStatus.WAITING_BROADCAST, "broadcast queue", NOW.plusSeconds(9));
        item.transitionTo(WithdrawalQueueStatus.BROADCASTING, "broadcasting", NOW.plusSeconds(10));
        item.recordBroadcastFailure("rpc timeout", NOW.plusSeconds(20), NOW.plusSeconds(11));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.BROADCAST_FAILED);
        assertThat(item.broadcastAttempts()).isEqualTo(1);
        assertThat(item.nextBroadcastAttemptAt()).isEqualTo(NOW.plusSeconds(20));

        item.transitionTo(WithdrawalQueueStatus.BROADCASTING, "retry broadcast", NOW.plusSeconds(21));
        item.transitionTo(WithdrawalQueueStatus.BROADCASTED, "broadcasted", NOW.plusSeconds(22));
        item.transitionTo(WithdrawalQueueStatus.CONFIRMING, "confirming", NOW.plusSeconds(23));
        item.recordConfirmationFailure("receipt unavailable", NOW.plusSeconds(33), NOW.plusSeconds(24));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.CONFIRMATION_FAILED);
        assertThat(item.confirmationFailures()).isEqualTo(1);
        assertThat(item.nextConfirmationAttemptAt()).isEqualTo(NOW.plusSeconds(33));

        item.transitionTo(WithdrawalQueueStatus.CONFIRMING, "retry confirmation", NOW.plusSeconds(34));
        item.transitionTo(WithdrawalQueueStatus.CONFIRMED, "confirmed", NOW.plusSeconds(35));

        assertThat(item.status()).isEqualTo(WithdrawalQueueStatus.CONFIRMED);
    }
}
