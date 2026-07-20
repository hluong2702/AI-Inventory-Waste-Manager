package vn.inventoryai.subscription.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.subscription.PaymentStatus;
import vn.inventoryai.subscription.PaymentTransaction;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;
import vn.payos.PayOS;
import vn.payos.exception.ForbiddenException;
import vn.payos.exception.NotFoundException;
import vn.payos.exception.UnauthorizedException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "app.payment.payos", name = "enabled", havingValue = "true")
public class PayOsGatewayService implements PaymentGatewayService {
    private static final long ORDER_CODE_BASE = 2_000_000_000_000_000L;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final String CHECKOUT_BASE_URL = "https://pay.payos.vn/web/";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private final PayOsProperties properties;
    private final PayOS payOS;

    @Autowired
    public PayOsGatewayService(PayOsProperties properties) {
        this(properties, createClient(properties));
    }

    PayOsGatewayService(PayOsProperties properties, PayOS payOS) {
        this.properties = properties;
        this.payOS = payOS;
        requireSafeCallbackUrl(properties.returnUrl(), "PAYOS_RETURN_URL");
        requireSafeCallbackUrl(properties.cancelUrl(), "PAYOS_CANCEL_URL");
    }

    @Override
    public String provider() {
        return "PAYOS";
    }

    @Override
    public String reserveProviderTransactionId(PaymentTransaction transaction) {
        if (transaction.getId() == null || transaction.getId() <= 0) {
            throw new IllegalArgumentException("Persisted payment id is required before reserving a payOS order code");
        }
        long orderCode;
        try {
            orderCode = Math.addExact(ORDER_CODE_BASE, transaction.getId());
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Payment id cannot be represented as a payOS order code", ex);
        }
        if (orderCode > MAX_SAFE_INTEGER) {
            throw new IllegalStateException("Payment id exceeds the payOS safe order-code range");
        }
        return String.valueOf(orderCode);
    }

    @Override
    public PaymentIntent createPayment(PaymentTransaction transaction, String clientIp) {
        long orderCode = reservedOrderCode(transaction);
        long amount = amountForPayOs(transaction.getAmount());
        String fullDescription = "GOI " + transaction.getSubscription().getPlan().getCode() + " " + orderCode;
        String description = fullDescription.substring(0, Math.min(25, fullDescription.length()));

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(properties.returnUrl())
                .cancelUrl(properties.cancelUrl())
                .expiredAt(Instant.now().plusSeconds(properties.expireMinutes() * 60L).getEpochSecond())
                .build();
        var response = payOS.paymentRequests().create(request);
        if (response.getOrderCode() == null || response.getOrderCode() != orderCode) {
            throw new AppException(
                    ErrorCode.PAYMENT_PROVIDER_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned a mismatched order code"
            );
        }
        return new PaymentIntent(
                String.valueOf(response.getOrderCode()),
                response.getCheckoutUrl(),
                mapStatus(response.getStatus())
        );
    }

    @Override
    public PaymentIntent recoverOrCreatePayment(PaymentTransaction transaction, String clientIp) {
        try {
            return recoverExisting(transaction);
        } catch (NotFoundException notCreatedYet) {
            try {
                return createPayment(transaction, clientIp);
            } catch (RuntimeException createFailure) {
                // The create response can be lost after payOS committed the order. Query
                // once more before declaring the operation uncertain.
                try {
                    return recoverExisting(transaction);
                } catch (NotFoundException stillMissing) {
                    createFailure.addSuppressed(stillMissing);
                    throw createFailure;
                } catch (RuntimeException recoveryFailure) {
                    createFailure.addSuppressed(recoveryFailure);
                    throw createFailure;
                }
            }
        }
    }

    @Override
    public boolean isDefinitiveCreationFailure(RuntimeException failure) {
        return failure instanceof UnauthorizedException
                || failure instanceof ForbiddenException
                || (failure instanceof AppException appException && appException.getStatus().is4xxClientError());
    }

    @Override
    public boolean verifyWebhook(PaymentWebhookRequest request) {
        try {
            var verified = payOS.webhooks().verify(request.providerData());
            return verified != null
                    && verified.getOrderCode() != null
                    && String.valueOf(verified.getOrderCode()).equals(request.providerTransactionId());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public boolean validateWebhookPayment(PaymentTransaction transaction, PaymentWebhookRequest request) {
        Map<String, Object> data = nestedData(request.providerData().get("data"));
        return String.valueOf(amountForPayOs(transaction.getAmount())).equals(String.valueOf(data.get("amount")))
                && transaction.getCurrency().equals(String.valueOf(data.get("currency")));
    }

    @Override
    public PaymentStatus queryStatus(PaymentTransaction transaction) {
        try {
            PaymentLinkStatus status = payOS.paymentRequests()
                    .get(reservedOrderCode(transaction))
                    .getStatus();
            return mapStatus(status);
        } catch (RuntimeException ex) {
            return PaymentStatus.PENDING;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedData(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static PayOS createClient(PayOsProperties properties) {
        requireConfiguration(properties.clientId(), "PAYOS_CLIENT_ID");
        requireConfiguration(properties.apiKey(), "PAYOS_API_KEY");
        requireConfiguration(properties.checksumKey(), "PAYOS_CHECKSUM_KEY");
        return new PayOS(properties.clientId(), properties.apiKey(), properties.checksumKey());
    }

    private static long amountForPayOs(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "payOS only accepts whole-number VND amounts");
        }
    }

    private PaymentIntent recoverExisting(PaymentTransaction transaction) {
        long orderCode = reservedOrderCode(transaction);
        PaymentLink link = payOS.paymentRequests().get(orderCode);
        String paymentUrl = link.getId() == null || !link.getId().matches("[A-Za-z0-9_-]{1,128}")
                ? null
                : CHECKOUT_BASE_URL + link.getId();
        return new PaymentIntent(String.valueOf(orderCode), paymentUrl, mapStatus(link.getStatus()));
    }

    private long reservedOrderCode(PaymentTransaction transaction) {
        try {
            long orderCode = Long.parseLong(transaction.getProviderTransactionId());
            if (orderCode < ORDER_CODE_BASE || orderCode > MAX_SAFE_INTEGER) {
                throw new NumberFormatException("outside reserved range");
            }
            return orderCode;
        } catch (NumberFormatException ex) {
            throw new AppException(
                    ErrorCode.PAYMENT_PROVIDER_ERROR,
                    HttpStatus.CONFLICT,
                    "Payment does not have a valid reserved payOS order code"
            );
        }
    }

    private PaymentStatus mapStatus(PaymentLinkStatus status) {
        if (status == PaymentLinkStatus.PAID) return PaymentStatus.SUCCESS;
        if (status == PaymentLinkStatus.CANCELLED) return PaymentStatus.CANCELLED;
        if (status == PaymentLinkStatus.EXPIRED) return PaymentStatus.EXPIRED;
        if (status == PaymentLinkStatus.FAILED) return PaymentStatus.FAILED;
        return PaymentStatus.PENDING;
    }

    private static void requireConfiguration(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " is required when payOS is enabled");
        }
    }

    private static void requireSafeCallbackUrl(String value, String environmentVariable) {
        requireConfiguration(value, environmentVariable);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean secure = "https".equals(scheme);
            boolean localDevelopment = "http".equals(scheme) && LOOPBACK_HOSTS.contains(host);
            if (!uri.isAbsolute() || host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null
                    || (!secure && !localDevelopment)) {
                throw new IllegalArgumentException("unsafe callback URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    environmentVariable + " must be an absolute HTTPS URL (HTTP is allowed only for loopback development)",
                    ex
            );
        }
    }
}
