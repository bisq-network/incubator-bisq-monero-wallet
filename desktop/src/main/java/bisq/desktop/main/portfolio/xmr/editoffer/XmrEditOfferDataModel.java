/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.xmr.editoffer;


import bisq.desktop.Navigation;
import bisq.desktop.main.offer.xmr.XmrMakerFeeProvider;
import bisq.desktop.main.offer.xmr.XmrMutableOfferDataModel;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.xmr.XmrCoin;
import bisq.core.xmr.wallet.XmrRestrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.fee.XmrFeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.XmrCoinUtil;
import bisq.core.util.BsqFormatter;
import bisq.core.util.XmrBSFormatter;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import java.util.Optional;

class XmrEditOfferDataModel extends XmrMutableOfferDataModel {
    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private OpenOffer openOffer;
    private OpenOffer.State initialState;
    private String btcToXmrExchangeRate;
    //TODO(niyid) convert for use with XMR base
    @Inject
    XmrEditOfferDataModel(OpenOfferManager openOfferManager,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService,
                       XmrWalletRpcWrapper xmrWalletWrapper,
                       Preferences preferences,
                       User user,
                       KeyRing keyRing,
                       P2PService p2PService,
                       PriceFeedService priceFeedService,
                       FilterManager filterManager,
                       AccountAgeWitnessService accountAgeWitnessService,
                       XmrFeeService feeService,
                       TxFeeEstimationService txFeeEstimationService,
                       ReferralIdService referralIdService,
                       XmrBSFormatter xmrFormatter,
                       BsqFormatter bsqFormatter,
                       CorePersistenceProtoResolver corePersistenceProtoResolver,
                       XmrMakerFeeProvider makerFeeProvider,
                       Navigation navigation) {
        super(openOfferManager,
                btcWalletService,
                bsqWalletService,
                xmrWalletWrapper,
                preferences,
                user,
                keyRing,
                p2PService,
                priceFeedService,
                filterManager,
                accountAgeWitnessService,
                feeService,
                txFeeEstimationService,
                referralIdService,
                xmrFormatter,
                bsqFormatter,
                makerFeeProvider,
                navigation);
        this.corePersistenceProtoResolver = corePersistenceProtoResolver;
    }

    public void reset() {
        direction = null;
        tradeCurrency = null;
        tradeCurrencyCode.set(null);
        useMarketBasedPrice.set(false);
        amount.set(null);
        minAmount.set(null);
        price.set(null);
        volume.set(null);
        minVolume.set(null);
        buyerSecurityDeposit.set(0);
        paymentAccounts.clear();
        paymentAccount = null;
        marketPriceMargin = 0;
    }

    public void applyOpenOffer(OpenOffer openOffer) {
        this.openOffer = openOffer;

        Offer offer = openOffer.getOffer();
        direction = offer.getDirection();
        CurrencyUtil.getTradeCurrency(offer.getCurrencyCode())
                .ifPresent(c -> this.tradeCurrency = c);
        tradeCurrencyCode.set(offer.getCurrencyCode());

        this.initialState = openOffer.getState();
        PaymentAccount tmpPaymentAccount = user.getPaymentAccount(openOffer.getOffer().getMakerPaymentAccountId());
        Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode());
        if (optionalTradeCurrency.isPresent() && tmpPaymentAccount != null) {
            TradeCurrency selectedTradeCurrency = optionalTradeCurrency.get();
            this.paymentAccount = PaymentAccount.fromProto(tmpPaymentAccount.toProtoMessage(), corePersistenceProtoResolver);
            if (paymentAccount.getSingleTradeCurrency() != null)
                paymentAccount.setSingleTradeCurrency(selectedTradeCurrency);
            else
                paymentAccount.setSelectedTradeCurrency(selectedTradeCurrency);
        }

        // If the security deposit got bounded because it was below the coin amount limit, it can be bigger
        // by percentage than the restriction. We can't determine the percentage originally entered at offer
        // creation, so just use the default value as it doesn't matter anyway.
        double buyerSecurityDepositPercent = XmrCoinUtil.getAsPercentPerXmr(XmrCoin.fromCoin2XmrCoin(offer.getBuyerSecurityDeposit(), "BTC", btcToXmrExchangeRate), XmrCoin.fromCoin2XmrCoin(offer.getAmount(), "BTC", btcToXmrExchangeRate));
        if (buyerSecurityDepositPercent > XmrRestrictions.getMaxBuyerSecurityDepositAsPercent(this.paymentAccount)
                && offer.getBuyerSecurityDeposit().value == XmrRestrictions.getMinBuyerSecurityDepositAsCoin(1.0 / xmrMarketPrice.getPrice()).value)
            buyerSecurityDeposit.set(XmrRestrictions.getDefaultBuyerSecurityDepositAsPercent(this.paymentAccount));
        else
            buyerSecurityDeposit.set(buyerSecurityDepositPercent);

        allowAmountUpdate = false;
    }

    @Override
    public boolean initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        try {
            return super.initWithData(direction, tradeCurrency);
        } catch (NullPointerException e) {
            if (e.getMessage().contains("tradeCurrency")) {
                throw new IllegalArgumentException("Offers of removed assets cannot be edited. You can only cancel it.", e);
            }
            return false;
        }
    }

    @Override
    protected PaymentAccount getPreselectedPaymentAccount() {
        return paymentAccount;
    }

    public void populateData() {
        Offer offer = openOffer.getOffer();
        btcToXmrExchangeRate = offer.getExtraDataMap().get(OfferPayload.BTC_TO_XMR_RATE);
        // Min amount need to be set before amount as if minAmount is null it would be set by amount
        setMinAmount(XmrCoin.fromCoin2XmrCoin(offer.getMinAmount(), "BTC", btcToXmrExchangeRate));
        setAmount(XmrCoin.fromCoin2XmrCoin(offer.getAmount(), "BTC", btcToXmrExchangeRate));
        setPrice(offer.getPrice());
        setVolume(offer.getVolume());
        setUseMarketBasedPrice(offer.isUseMarketBasedPrice());
        if (offer.isUseMarketBasedPrice()) setMarketPriceMargin(offer.getMarketPriceMargin());
    }

    public void onStartEditOffer(ErrorMessageHandler errorMessageHandler) {
        openOfferManager.editOpenOfferStart(openOffer, () -> {
        }, errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        final OfferPayload offerPayload = openOffer.getOffer().getOfferPayload();
        final OfferPayload editedPayload = new OfferPayload(offerPayload.getId(),
                offerPayload.getDate(),
                offerPayload.getOwnerNodeAddress(),
                offerPayload.getPubKeyRing(),
                offerPayload.getDirection(),
                getPrice().get().getValue(),
                getMarketPriceMargin(),
                isUseMarketBasedPriceValue(),
                getAmount().get().getValue(),
                getMinAmount().get().getValue(),
                offerPayload.getBaseCurrencyCode(),
                offerPayload.getCounterCurrencyCode(),
                offerPayload.getArbitratorNodeAddresses(),
                offerPayload.getMediatorNodeAddresses(),
                offerPayload.getPaymentMethodId(),
                offerPayload.getMakerPaymentAccountId(),
                offerPayload.getOfferFeePaymentTxId(),
                offerPayload.getCountryCode(),
                offerPayload.getAcceptedCountryCodes(),
                offerPayload.getBankId(),
                offerPayload.getAcceptedBankIds(),
                offerPayload.getVersionNr(),
                offerPayload.getBlockHeightAtOfferCreation(),
                offerPayload.getTxFee(),
                offerPayload.getMakerFee(),
                offerPayload.isCurrencyForMakerFeeBtc(),
                offerPayload.getBuyerSecurityDeposit(),
                offerPayload.getSellerSecurityDeposit(),
                offerPayload.getMaxTradeLimit(),
                offerPayload.getMaxTradePeriod(),
                offerPayload.isUseAutoClose(),
                offerPayload.isUseReOpenAfterAutoClose(),
                offerPayload.getLowerClosePrice(),
                offerPayload.getUpperClosePrice(),
                offerPayload.isPrivateOffer(),
                offerPayload.getHashOfChallenge(),
                offerPayload.getExtraDataMap(),
                offerPayload.getProtocolVersion());

        final Offer editedOffer = new Offer(editedPayload);
        editedOffer.setPriceFeedService(priceFeedService);
        editedOffer.setState(Offer.State.AVAILABLE);

        openOfferManager.editOpenOfferPublish(editedOffer, initialState, () -> {
            openOffer = null;
            resultHandler.handleResult();
        }, errorMessageHandler);
    }

    public void onCancelEditOffer(ErrorMessageHandler errorMessageHandler) {
        if (openOffer != null)
            openOfferManager.editOpenOfferCancel(openOffer, initialState, () -> {
            }, errorMessageHandler);
    }
}
