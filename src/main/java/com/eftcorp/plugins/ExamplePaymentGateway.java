/*
 * Copyright (c) 2026 EFT CORPORATION LIMITED (UK) Company Number 12528125. All rights reserved.
 * Unauthorized use, reproduction, or distribution of this software, in whole or in part, is strictly prohibited.
 * Use of this code for AI training is strictly prohibited and may result in legal action.
 */
package com.eftcorp.plugins;

import com.ukheshe.arch.UK;
import com.ukheshe.arch.plugin.ExtensionProvider;
import com.ukheshe.arch.rest.AdvancedJsonRestClient;
import com.ukheshe.services.payment.gateway.IPaymentGateway;
import com.ukheshe.services.payment.gateway.pluggable.BasePaymentGateway;
import com.ukheshe.services.payment.gateway.pluggable.BiDirectionalDelegatingPaymentGateway;
import com.ukheshe.services.payment.gateway.pluggable.PluggablePaymentGateway;
import com.ukheshe.services.payment.model.PaymentProof;
import com.ukheshe.services.payment.model.db.DbPayment;
import com.ukheshe.services.payment.model.db.DbRefund;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;

@Dependent
@ExtensionProvider(type = "PaymentGateway", name = "etta-biruk")
public class ExamplePaymentGateway extends PluggablePaymentGateway {

    @Inject
    AdvancedJsonRestClient restClient;

    @PostConstruct
    public void init() {
        this.setSuperClass(UK.Runtime.getBean(BasePaymentGateway.class));
    }

    @Override
    public void initiatePayment(Object gatewaySpecificData) throws Exception {
        DbPayment dbPayment = super.getDbPayment();

        HashMap<String, Object> paymentData = new HashMap<>();
        paymentData.put("amount", dbPayment.getAmount().toString());
        paymentData.put("reference", "default_reference");
        
        long walletId = dbPayment.getWalletId();
        if (walletId == 0) {
            throw new IllegalArgumentException("walletId is required for ET_WALLET_ETTA");
        }

        // call external api
        restClient.postResource(paymentData, "https://payment.odoo.et/api/accounts/" + walletId + "/topup");

        UK.Tracing.logOnCurrentSpan(UK.Json.toJson(dbPayment));
    }

    @Override
    public void paymentModified(Object gatewaySpecificData) throws Exception {
        super.paymentModified(gatewaySpecificData);
    }

    @Override
    public void sync() {
        DbPayment dbPayment = super.getDbPayment();
        super.sync();
    }

    @Override
    protected void setSuperClass(BiDirectionalDelegatingPaymentGateway superclass) {
        super.setSuperClass(superclass);
    }

    @Override
    public void init(DbPayment dbPayment) {
        super.init(dbPayment);
    }

    @Override
    public DbPayment getDbPayment() {
        return super.getDbPayment();
    }

    @Override
    public void cancel() {
        super.cancel();
    }

    @Override
    public void reverse(Object gatewaySpecificData) {
        super.reverse(gatewaySpecificData);
    }

    @Override
    public void expire() {
        super.expire();
    }

    @Override
    public void refund(DbRefund refund) {
        super.refund(refund);
    }

    @Override
    public void deleteCardOnFile() {
        super.deleteCardOnFile();
    }

    @Override
    public boolean supportsPreProcessing() {
        return super.supportsPreProcessing();
    }

    @Override
    public boolean supportsReverse() {
        return super.supportsReverse();
    }

    @Override
    public boolean supportsHold() {
        return super.supportsHold();
    }

    @Override
    public List<PaymentProof> getPaymentProofs(DbPayment dbPayment, String fields) {
        return super.getPaymentProofs(dbPayment, fields);
    }
}
