package vn.inventoryai.subscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;
import vn.inventoryai.subscription.dto.PaymentWebhookResponse;

@RestController
@RequestMapping("/api/webhook/payment")
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final SubscriptionService subscriptionService;

    @PostMapping("/{provider}")
    PaymentWebhookResponse paymentWebhook(@PathVariable String provider, @Valid @RequestBody PaymentWebhookRequest request) {
        return subscriptionService.handleWebhook(provider, request);
    }
}
