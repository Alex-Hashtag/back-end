package org.acs.stuco.backend.news;

import org.springframework.data.jpa.repository.JpaRepository;


public interface NewsRepository extends JpaRepository<NewsPost, Long>
{

}


