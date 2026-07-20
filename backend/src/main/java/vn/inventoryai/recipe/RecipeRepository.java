package vn.inventoryai.recipe;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @EntityGraph(attributePaths = {"ingredients", "ingredients.ingredient"})
    Optional<Recipe> findByIdAndStoreId(Long id, Long storeId);

    @EntityGraph(attributePaths = {"ingredients", "ingredients.ingredient"})
    Optional<Recipe> findByStoreIdAndCode(Long storeId, String code);

    boolean existsByStoreIdAndCode(Long storeId, String code);

    boolean existsByStoreIdAndCodeAndIdNot(Long storeId, String code, Long id);

    @EntityGraph(attributePaths = {"ingredients", "ingredients.ingredient"})
    List<Recipe> findByStoreId(Long storeId);

    Page<Recipe> findByStoreId(Long storeId, Pageable pageable);

    long countByStoreId(Long storeId);
}
