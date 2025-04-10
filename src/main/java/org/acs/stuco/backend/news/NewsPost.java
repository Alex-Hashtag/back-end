package org.acs.stuco.backend.news;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@Table(name = "news_posts")
public class NewsPost
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    private String bannerPhotoUrl;

    @Column
    private String extraPhotos;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}

