package com.bits.inventory.repository;

import com.bits.inventory.enums.ProductStatus;
import com.bits.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findBySku(String sku);
    
    List<Product> findByStatus(ProductStatus status);
    
    List<Product> findByCategory(String category);
    
    List<Product> findByBrand(String brand);
    
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:name%")
    List<Product> findByNameContaining(@Param("name") String name);
    
    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.category = :category")
    List<Product> findByStatusAndCategory(@Param("status") ProductStatus status, @Param("category") String category);
    
    boolean existsBySku(String sku);
}
