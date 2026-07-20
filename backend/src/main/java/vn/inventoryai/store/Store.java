package vn.inventoryai.store;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Tọa độ cửa hàng dùng để gọi API thời tiết Open-Meteo.
     * Mặc định: TP.HCM. Owner có thể cập nhật sau.
     */
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude = new BigDecimal("10.823099");

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude = new BigDecimal("106.629662");
}
