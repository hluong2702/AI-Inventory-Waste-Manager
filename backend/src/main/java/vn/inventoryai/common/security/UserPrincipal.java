package vn.inventoryai.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.inventoryai.common.enums.Role;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(
        Long userId,
        Long storeId,
        String email,
        Role role,
        boolean mustChangePassword
) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
