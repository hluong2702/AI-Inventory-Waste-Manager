package vn.inventoryai.subscription.feature;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.subscription.SubscriptionService;

@Component
@RequiredArgsConstructor
public class FeatureAccessInterceptor implements HandlerInterceptor {
    private final SubscriptionService subscriptionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresFeature annotation = method.getMethodAnnotation(RequiresFeature.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(RequiresFeature.class);
        }
        if (annotation == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal) || principal.storeId() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!subscriptionService.hasFeature(principal.storeId(), annotation.value())) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.PAYMENT_REQUIRED, "Current subscription does not include feature " + annotation.value());
        }
        return true;
    }
}
