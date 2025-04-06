package org.acs.stuco.backend.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface OrderRepository extends JpaRepository<Order, Long>
{

    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByAssignedRepId(Long repId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = 'PAID' OR o.status = 'DELIVERED'")
    Page<Order> findActiveOrders(Pageable pageable);

    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    int updateStatus(Long id, OrderStatus status);

    @Query("SELECT COUNT(o), SUM(o.quantity), SUM(o.getTotalPrice()) FROM Order o WHERE o.status = 'DELIVERED'")
    Object[] getOrderStatistics();

    @Query("SELECT o FROM Order o WHERE o.status = 'DELIVERED' AND o.paidAt < :cutoffDate")
    List<Order> findDeliveredOrdersBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

}