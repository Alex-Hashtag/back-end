package org.acs.stuco.backend.order;

import org.acs.stuco.backend.exceptions.InsufficientStockException;
import org.acs.stuco.backend.exceptions.OrderNotFoundException;
import org.acs.stuco.backend.order.archive.ArchivedOrder;
import org.acs.stuco.backend.order.archive.ArchivedOrderRepository;
import org.acs.stuco.backend.product.Product;
import org.acs.stuco.backend.product.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
public class OrderService
{

    private final OrderRepository orderRepository;
    private final ArchivedOrderRepository archivedOrderRepository;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository,
                        ArchivedOrderRepository archivedOrderRepository,
                        ProductService productService)
    {
        this.orderRepository = orderRepository;
        this.archivedOrderRepository = archivedOrderRepository;
        this.productService = productService;
    }

    @Transactional
    public Order createOrder(Order order)
    {
        Product providedProduct = order.getProduct();

        if (providedProduct != null && providedProduct.getId() != null)
        {

            Optional<Product> productOpt = productService.getProductById(providedProduct.getId());
            if (productOpt.isPresent())
            {
                Product product = productOpt.get();

                if (product.getAvailable() != -1 && product.getAvailable() < order.getQuantity())
                {
                    throw new InsufficientStockException();
                }

                if (product.getAvailable() > 0)
                {
                    productService.reduceStock(product.getId(), order.getQuantity());
                    if (product.getAvailable() == 0)
                        productService.deleteOutOfStockProducts();
                }

                order.setProductName(product.getName());
                order.setProductPrice(product.getPrice());

                order.setProduct(product);
            }
            else
            {

                if (order.getProductName() == null || order.getProductPrice() == null)
                {
                    throw new IllegalArgumentException("Product not found and no product details provided");
                }

                order.setProduct(null);
            }
        }
        else
        {

            if (order.getProductName() == null || order.getProductPrice() == null)
            {
                throw new IllegalArgumentException("Either a product reference or custom product details must be provided");
            }
        }

        return orderRepository.save(order);
    }


    @Transactional
    public Order updateOrderStatus(Long id, OrderStatus status)
    {
        orderRepository.updateStatus(id, status);
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Page<Order> getUserOrders(Long userId, Pageable pageable)
    {
        return orderRepository.findByBuyerId(userId, pageable);
    }

    public Page<Order> getAllOrders(Pageable pageable)
    {
        return orderRepository.findAll(pageable);
    }

    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable)
    {
        return orderRepository.findByStatus(status, pageable);
    }

    public Page<Order> getAssignedOrders(Long repId, Pageable pageable)
    {
        return orderRepository.findByAssignedRepId(repId, pageable);
    }

    public Object[] getOrderStatistics()
    {
        return orderRepository.getOrderStatistics();
    }

    @Transactional
    public void archiveDeliveredOrders()
    {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Order> ordersToArchive = orderRepository.findDeliveredOrdersBefore(cutoffDate);
        for (Order order : ordersToArchive)
        {
            ArchivedOrder archivedOrder = convertToArchivedOrder(order);
            archivedOrderRepository.save(archivedOrder);
            orderRepository.delete(order);
        }
    }

    public ArchivedOrder convertToArchivedOrder(Order order)
    {
        ArchivedOrder archivedOrder = new ArchivedOrder();
        archivedOrder.setId(order.getId());
        archivedOrder.setProduct(order.getProduct());
        archivedOrder.setBuyer(order.getBuyer());
        archivedOrder.setQuantity(order.getQuantity());
        archivedOrder.setStatus(order.getStatus());
        archivedOrder.setPaymentType(order.getPaymentType());
        archivedOrder.setCreatedAt(order.getCreatedAt());
        archivedOrder.setPaidAt(order.getPaidAt());
        archivedOrder.setAssignedRep(order.getAssignedRep());

        archivedOrder.setInstructions(order.getInstructions());
        archivedOrder.setProductName(order.getProductName());
        archivedOrder.setProductPrice(order.getProductPrice());

        return archivedOrder;
    }

    public Page<ArchivedOrder> getArchivedOrders(Pageable pageable)
    {
        return archivedOrderRepository.findAll(pageable);
    }
}


