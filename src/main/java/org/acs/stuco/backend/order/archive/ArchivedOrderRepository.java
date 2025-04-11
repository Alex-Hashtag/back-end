package org.acs.stuco.backend.order.archive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ArchivedOrderRepository extends JpaRepository<ArchivedOrder, Long>
{
}

