package vn.inventoryai.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.inventory.dto.CreateIngredientRequest;
import vn.inventoryai.inventory.dto.IngredientImportResult;
import vn.inventoryai.inventory.dto.IngredientResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {
    private final IngredientService ingredientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    List<IngredientResponse> list() {
        return ingredientService.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    IngredientResponse create(@Valid @RequestBody CreateIngredientRequest request) {
        return ingredientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    IngredientResponse update(@PathVariable Long id, @Valid @RequestBody CreateIngredientRequest request) {
        return ingredientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    void delete(@PathVariable Long id) {
        ingredientService.delete(id);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    IngredientImportResult importIngredients(@RequestParam("file") MultipartFile file) {
        return ingredientService.importIngredients(file);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    ResponseEntity<String> importTemplate() {
        return ingredientService.exportIngredientTemplate();
    }
}
