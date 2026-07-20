package vn.inventoryai.recipe;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.recipe.dto.CreateRecipeRequest;
import vn.inventoryai.recipe.dto.RecipeResponse;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    public List<RecipeResponse> list() {
        return recipeService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    public RecipeResponse get(@PathVariable Long id) {
        return recipeService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public RecipeResponse create(@Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public RecipeResponse update(@PathVariable Long id, @Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public void delete(@PathVariable Long id) {
        recipeService.delete(id);
    }
}
