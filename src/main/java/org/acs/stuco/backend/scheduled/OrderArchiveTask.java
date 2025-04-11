package org.acs.stuco.backend.scheduled;

import org.acs.stuco.backend.order.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component("scheduledOrderArchiveTask")
public class OrderArchiveTask
{
    private final OrderService orderService;

    public OrderArchiveTask(OrderService orderService)
    {
        this.orderService = orderService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void archiveOldDeliveredOrders()
    {
        orderService.archiveDeliveredOrders();
    }
}


