package vn.inventoryai.recipe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.recipe.dto.CreateRecipeRequest;
import vn.inventoryai.recipe.dto.RecipeResponse;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<RecipeResponse> list() {
        Long storeId = SecurityUtils.storeId();
        return recipeRepository.findByStoreId(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse get(Long id) {
        Long storeId = SecurityUtils.storeId();
        Recipe recipe = recipeRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipe not found"));
        return toResponse(recipe);
    }

    @Transactional
    public RecipeResponse create(CreateRecipeRequest request) {
        Long storeId = SecurityUtils.storeId();
        String normalizedCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (recipeRepository.existsByStoreIdAndCode(storeId, normalizedCode)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.CONFLICT,
                    "Mã công thức '" + normalizedCode + "' đã tồn tại"
            );
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));

        Recipe recipe = new Recipe();
        recipe.setStore(store);
        recipe.setCode(normalizedCode);
        recipe.setName(request.name().trim());
        recipe.setPrice(request.price());
        recipe.setActive(request.active());

        List<RecipeIngredient> recipeIngredients = new ArrayList<>();
        for (CreateRecipeRequest.IngredientItem item : request.ingredients()) {
            Ingredient ingredient = ingredientRepository.findByIdAndStoreIdAndDeletedFalse(item.ingredientId(), storeId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));
            
            RecipeIngredient ri = new RecipeIngredient();
            ri.setStore(store);
            ri.setRecipe(recipe);
            ri.setIngredient(ingredient);
            ri.setQuantity(item.quantity());
            recipeIngredients.add(ri);
        }
        recipe.setIngredients(recipeIngredients);

        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional
    public RecipeResponse update(Long id, CreateRecipeRequest request) {
        Long storeId = SecurityUtils.storeId();
        Recipe recipe = recipeRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipe not found"));

        String normalizedCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (recipeRepository.existsByStoreIdAndCodeAndIdNot(storeId, normalizedCode, id)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.CONFLICT,
                    "Mã công thức '" + normalizedCode + "' đã tồn tại"
            );
        }

        recipe.setCode(normalizedCode);
        recipe.setName(request.name().trim());
        recipe.setPrice(request.price());
        recipe.setActive(request.active());

        // Replace ingredients
        recipe.getIngredients().clear();
        recipeRepository.saveAndFlush(recipe);

        List<RecipeIngredient> recipeIngredients = new ArrayList<>();
        for (CreateRecipeRequest.IngredientItem item : request.ingredients()) {
            Ingredient ingredient = ingredientRepository.findByIdAndStoreIdAndDeletedFalse(item.ingredientId(), storeId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));

            RecipeIngredient ri = new RecipeIngredient();
            ri.setStore(recipe.getStore());
            ri.setRecipe(recipe);
            ri.setIngredient(ingredient);
            ri.setQuantity(item.quantity());
            recipeIngredients.add(ri);
        }
        recipe.getIngredients().addAll(recipeIngredients);

        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional
    public void delete(Long id) {
        Long storeId = SecurityUtils.storeId();
        Recipe recipe = recipeRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipe not found"));
        recipeRepository.delete(recipe);
    }

    private RecipeResponse toResponse(Recipe recipe) {
        List<RecipeResponse.IngredientItemResponse> items = recipe.getIngredients().stream()
                .map(ri -> new RecipeResponse.IngredientItemResponse(
                        ri.getIngredient().getId(),
                        ri.getIngredient().getCode(),
                        ri.getIngredient().getName(),
                        ri.getIngredient().getUnit(),
                        ri.getQuantity()
                ))
                .toList();

        return new RecipeResponse(
                recipe.getId(),
                recipe.getStore().getId(),
                recipe.getCode(),
                recipe.getName(),
                recipe.getPrice(),
                recipe.isActive(),
                items,
                recipe.getCreatedAt()
        );
    }
}
