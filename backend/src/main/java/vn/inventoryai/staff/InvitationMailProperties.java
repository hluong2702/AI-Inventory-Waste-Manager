package vn.inventoryai.staff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invitation-email")
public record InvitationMailProperties(boolean enabled) {
}
