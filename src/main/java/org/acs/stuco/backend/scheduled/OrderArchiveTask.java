package org.acs.stuco.backend.scheduled;

import org.acs.stuco.backend.order.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component("scheduledOrderArchiveTask")
public class OrderArchiveTask
{
    @Autowired
    private OrderService orderService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void archiveOldDeliveredOrders()
    {
        orderService.archiveDeliveredOrders();
    }
}
