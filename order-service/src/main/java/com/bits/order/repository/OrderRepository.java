package com.bits.order.repository;

import com.bits.order.model.Order;
import com.bits.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByOrderNumber(String orderNumber);
    
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    List<Order> findByStatus(OrderStatus status);
    
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);
    
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findByUserIdAndDateRange(@Param("userId") String userId, 
                                        @Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByStatusIn(@Param("statuses") List<OrderStatus> statuses);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.userId = :userId")
    Long countByUserId(@Param("userId") String userId);
    
    @Query("SELECT o FROM Order o WHERE o.sagaId = :sagaId")
    Optional<Order> findBySagaId(@Param("sagaId") String sagaId);
}
