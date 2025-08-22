package com.bits.cartservice.dao;

import java.util.List;
import java.util.Optional;

import com.bits.cartservice.model.Cart;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends CrudRepository<Cart, String> {

    Optional<Cart> findByUserId(String userId);

    Optional<Cart> findBySessionId(String sessionId);

    List<Cart> findByStatus(String status);

    void deleteByUserId(String userId);

    void deleteBySessionId(String sessionId);

    boolean existsByUserId(String userId);

    boolean existsBySessionId(String sessionId);
}
