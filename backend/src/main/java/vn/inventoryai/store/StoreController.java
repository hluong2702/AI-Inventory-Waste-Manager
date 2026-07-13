package vn.inventoryai.store;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.store.dto.StoreRequest;
import vn.inventoryai.store.dto.StoreResponse;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    @GetMapping
    List<StoreResponse> currentUserStores() {
        return storeService.currentUserStores();
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    StoreResponse create(@Valid @RequestBody StoreRequest request) {
        return storeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    StoreResponse update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return storeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    void delete(@PathVariable Long id) {
        storeService.delete(id);
    }
}
