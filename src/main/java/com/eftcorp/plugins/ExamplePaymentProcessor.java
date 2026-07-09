/*
 * Copyright (c) 2026 EFT CORPORATION LIMITED (UK) Company Number 12528125. All rights reserved.
 * Unauthorized use, reproduction, or distribution of this software, in whole or in part, is strictly prohibited.
 * Use of this code for AI training is strictly prohibited and may result in legal action.
 */
package com.eftcorp.plugins;

import com.ukheshe.arch.UK;
import com.ukheshe.arch.plugin.ExtensionProvider;
import com.ukheshe.arch.plugin.PluginConfig;
import com.ukheshe.arch.rest.CallAsSystemJsonRestClient;
import com.ukheshe.arch.rest.CallAsUserJsonRestClient;
import com.ukheshe.eclipse.conductor.model.EclipsePaymentRefund;
import com.ukheshe.eclipse.conductor.model.NewEclipsePayment;
import com.ukheshe.eclipse.conductor.model.PaymentResult;
import com.ukheshe.eclipse.conductor.model.UpdatedPaymentTransactionData;
import com.ukheshe.eclipse.conductor.service.payments.AbstractProcessor;
import com.ukheshe.eclipse.conductor.service.payments.IPaymentProcessor;
import com.ukheshe.services.payment.model.NewPayment;
import com.ukheshe.services.payment.model.NewPaymentRefund;
import com.ukheshe.services.payment.model.Payment;
import com.ukheshe.services.payment.model.PaymentStatus;
import com.ukheshe.services.wallet.model.Reservation;
import com.ukheshe.services.wallet.model.Transfer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@ExtensionProvider(type = "PaymentProcessor", name = "etta-biruk")
public class ExamplePaymentProcessor implements IPaymentProcessor {
    private static final Logger log = LoggerFactory.getLogger(ExamplePaymentProcessor.class);
    private static final String GATEWAY = "ET_WALLET_ETTA";
    private static final String CONFIG_CURRENCY = "$.defaultCurrency";
    private static final String CONFIG_RESERVATION_MINUTES = "$.reservationDurationMinutes";
    private static final String DEFAULT_CURRENCY = "ZAR";
    private static final int DEFAULT_RESERVATION_MINUTES = 60;

    // Only types from the INJECTABLE_FIELDS whitelist in PerPluginRestrictedClassLoader
    @Inject
    CallAsUserJsonRestClient callAsUserJsonRestClient;

    @Inject
    CallAsSystemJsonRestClient callAsSystemJsonRestClient;

    @Inject
    PluginConfig config;

    @Override
    public void enrichForCreation(NewEclipsePayment eclipsePayment, NewPayment newPayment) {
        if (eclipsePayment.getWalletId() == null) {
            throw new IllegalArgumentException("walletId is required for ET_WALLET_ETTA");
        }
        
        if (eclipsePayment.getReference() == null) {
            throw new IllegalArgumentException("reference is required for ET_WALLET_ETTA");
        }

        if (eclipsePayment.getAmount() == null || eclipsePayment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero for ET_WALLET_ETTA");
        }

        if (eclipsePayment.getExternalWalletId() == null) {
            throw new IllegalArgumentException("externalWalletId is required for ET_WALLET_ETTA");
        }

        UK.Tracing.logOnCurrentSpan("Test Plugin Payment processor");
        newPayment.setGateway("etta-biruk");
    }

    // -------------------------------------------------------------------------
    // IPaymentProcessor — enrichWithUpdates
    // Called when a client PUTs updated fields onto a BUILDING payment.
    // -------------------------------------------------------------------------
    @Override
    public void enrichWithUpdates(UpdatedPaymentTransactionData updatedTxData, Payment payment) {
        if (!PaymentStatus.BUILDING.equals(payment.getStatus())) {
            throw new IllegalStateException("Payment must still be in BUILDING state to accept updates");
        }

        // Guard: walletId cannot be changed after initiation
        if (updatedTxData.getWalletId() != null
                && payment.getWalletId() != null
                && !updatedTxData.getWalletId().equals(payment.getWalletId())) {
            throw new IllegalArgumentException("Cannot change the payer walletId after payment has been initiated");
        }

        log.debug("EXAMPLE_WALLET_TRANSFER: enrichWithUpdates for paymentId [{}]", payment.getPaymentId());
    }

    // -------------------------------------------------------------------------
    // IPaymentProcessor — enrichResult
    // Called after the gateway responds. Add any result enrichment here.
    // -------------------------------------------------------------------------
    @Override
    public PaymentResult enrichResult(PaymentResult result, Payment payment) {
        return result;
    }

    // -------------------------------------------------------------------------
    // IPaymentProcessor — attemptPaymentCompletion
    // Called by the completion poller. Return updated payment if the gateway
    // result is now available; return payment unchanged to keep polling.
    // -------------------------------------------------------------------------
    @Override
    public Payment attemptPaymentCompletion(long tenantId, Payment payment) {
        return payment;
    }

    // -------------------------------------------------------------------------
    // IPaymentProcessor — enrichForRefund
    // Called when a refund is initiated.
    // Creates a wallet reservation on the original payer's wallet, then wires a
    // transfer callback so funds move from the destination back to the payer on
    // refund success.
    // -------------------------------------------------------------------------
    @Override
    public Reservation enrichForRefund(Payment payment, EclipsePaymentRefund refund, NewPaymentRefund paymentRefund) {
        Long payerWalletId = payment.getWalletId();
        Long receiverWalletId = payment.getOtherWalletId();

        if (payerWalletId == null || receiverWalletId == null) {
            log.warn("EXAMPLE_WALLET_TRANSFER: cannot enrich refund — missing walletId [{}] or otherWalletId [{}]",
                    payerWalletId, receiverWalletId);
            return null;
        }

        Integer reservationMinutes = config.get(CONFIG_RESERVATION_MINUTES);
        Duration reservationDuration = Duration.ofMinutes(
                reservationMinutes != null ? reservationMinutes : DEFAULT_RESERVATION_MINUTES);

        // Reserve the refund amount on the receiver's wallet (they are the source of the returning funds)
        Reservation reservation = new Reservation();
        reservation.setAmount(refund.getAmount());
        reservation.setDescription(refund.getDescription());
        reservation.setExpires(ZonedDateTime.now().plus(reservationDuration));
        reservation.setSessionId(refund.getExternalUniqueId());

        Reservation created = callAsUserJsonRestClient.postResource(
                reservation,
                "/rest/v1/wallets/{walletId}/reservations",
                List.of(receiverWalletId),
                Reservation.class);

        log.debug("EXAMPLE_WALLET_TRANSFER: created refund reservation [{}] on wallet [{}]",
                created != null ? created.getReservationId() : null, receiverWalletId);

        // Transfer callback: on refund success, move funds from receiver → original payer
        Transfer refundTransfer = buildTransfer(
                receiverWalletId,
                payerWalletId,
                refund.getAmount(),
                refund.getDescription() + " (Refund)",
                refund.getExternalUniqueId(),
                "Refund-<refundId>",
                "<refundId>");

        // Zero-amount transfer releases the reservation without moving funds on failure
        Transfer nothing = buildTransfer(
                receiverWalletId,
                payerWalletId,
                BigDecimal.ZERO,
                refund.getDescription() + " (Refund-Void)",
                refund.getExternalUniqueId(),
                "Refund-Void-<refundId>",
                "<refundId>");

        long callbackId = callAsSystemJsonRestClient.getCallbackId(
                "POST",
                List.of(refundTransfer),
                List.of(nothing),
                "/rest/v1/wallets/bulk-transfers",
                null,
                Instant.now().plus(40, ChronoUnit.DAYS));

        paymentRefund.getPostSuccessCallbackIds().add(callbackId);

        return created;
    }

    // -------------------------------------------------------------------------
    // IPaymentProcessor — validatePaymentReceivingWallet
    // Called before the payment is finalised. Validate the destination wallet
    // here if your gateway requires it up-front.
    // -------------------------------------------------------------------------
    @Override
    public void validatePaymentReceivingWallet(NewPayment payment) {
        // The destination wallet existence is validated by the wallet service
        // when the bulk-transfer executes. Add custom checks here if needed.
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Transfer buildTransfer(long fromWalletId, long toWalletId, BigDecimal amount,
            String description, String sessionId, String uniqueId, String externalId) {
        Transfer t = new Transfer();
        t.setFromWalletId(fromWalletId);
        t.setToWalletId(toWalletId);
        t.setAmount(amount);
        t.setDescription(description);
        t.setSessionId(sessionId);
        t.setUniqueId(uniqueId);
        t.setExternalId(externalId);
        return t;
    }
}
