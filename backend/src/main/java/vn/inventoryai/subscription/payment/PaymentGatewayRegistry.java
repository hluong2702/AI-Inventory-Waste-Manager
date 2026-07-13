package vn.inventoryai.subscription.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGatewayService> gateways;

    public PaymentGatewayRegistry(List<PaymentGatewayService> gateways) {
        this.gateways = gateways.stream()
                .collect(Collectors.toMap(gateway -> gateway.provider().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public PaymentGatewayService get(String provider) {
        PaymentGatewayService gateway = gateways.get(provider.toLowerCase(Locale.ROOT));
        if (gateway == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Unsupported payment provider");
        }
        return gateway;
    }
}
