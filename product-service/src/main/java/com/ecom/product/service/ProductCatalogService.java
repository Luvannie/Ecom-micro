package com.ecom.product.service;

import com.ecom.product.domain.Category;
import com.ecom.product.domain.Product;
import com.ecom.product.repository.CategoryRepository;
import com.ecom.product.repository.ProductRepository;
import com.ecom.product.web.dto.CategoryResponse;
import com.ecom.product.web.dto.CreateCategoryRequest;
import com.ecom.product.web.dto.CreateProductRequest;
import com.ecom.product.web.dto.ProductResponse;
import com.ecom.product.web.dto.ProductSearchCriteria;
import com.ecom.product.web.dto.ProductSummaryResponse;
import com.ecom.product.web.dto.UpdateProductRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductCatalogService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public ProductCatalogService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException(request.slug());
        }
        return toCategoryResponse(categoryRepository.save(new Category(request.name(), request.slug())));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @CacheEvict(cacheNames = {"product-detail", "product-search"}, allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = findCategory(request.categoryId());
        Product product = new Product(category, request.name(), request.slug(), request.description(), request.price(),
                request.imageUrl(), request.promotionTag());
        return toProductResponse(productRepository.save(product));
    }

    @CacheEvict(cacheNames = {"product-detail", "product-search"}, allEntries = true)
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        Category category = findCategory(request.categoryId());
        product.update(category, request.name(), request.slug(), request.description(), request.price(), request.imageUrl(),
                request.promotionTag());
        return toProductResponse(product);
    }

    @CacheEvict(cacheNames = {"product-detail", "product-search"}, allEntries = true)
    public ProductResponse changeStatus(UUID productId, boolean active) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        product.setActive(active);
        return toProductResponse(product);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "product-search", key = "#p0.toCacheKey() + ':' + #p1.pageNumber + ':' + #p1.pageSize")
    public Page<ProductSummaryResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        return productRepository.findAll(specification(criteria), pageable).map(this::toProductSummary);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "product-detail", key = "#p0")
    public ProductResponse getProduct(UUID productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .map(this::toProductResponse)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Specification<Product> specification(ProductSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isTrue(root.get("active")));
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")),
                        "%" + criteria.keyword().trim().toLowerCase() + "%"));
            }
            if (criteria.categoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.isActive());
    }

    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(product.getId(), product.getCategory().getId(), product.getCategory().getName(),
                product.getName(), product.getSlug(), product.getDescription(), product.getPrice(), product.getImageUrl(),
                product.isActive(), product.getPromotionTag());
    }

    private ProductSummaryResponse toProductSummary(Product product) {
        return new ProductSummaryResponse(product.getId(), product.getCategory().getId(), product.getName(),
                product.getSlug(), product.getPrice(), product.getImageUrl(), product.getPromotionTag());
    }
}
