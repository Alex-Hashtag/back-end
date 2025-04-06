package org.acs.stuco.backend.news;

import org.acs.stuco.backend.news.event.NewsPostCreatedEvent;
import org.acs.stuco.backend.upload.UploadService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
public class NewsService
{
    private final NewsRepository newsRepository;
    private final UploadService uploadService;
    private final ApplicationEventPublisher eventPublisher;

    public NewsService(NewsRepository newsRepository, UploadService uploadService, ApplicationEventPublisher eventPublisher)
    {
        this.newsRepository = newsRepository;
        this.uploadService = uploadService;
        this.eventPublisher = eventPublisher;
    }

    // Updated method to support pagination
    public Page<NewsPost> findAll(Pageable pageable)
    {
        return newsRepository.findAll(pageable);
    }

    public NewsPost findById(Long id)
    {
        return newsRepository.findById(id).orElse(null);
    }

    public ResponseEntity<?> createNewsPost(String title, String content,
                                            MultipartFile banner, MultipartFile[] extraPhotos)
    {
        if (title == null || title.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Title is required");
        if (content == null || content.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Content is required");

        NewsPost post = new NewsPost();
        post.setTitle(title);
        post.setContent(content);

        if (banner != null && !banner.isEmpty())
        {
            String bannerUrl = uploadService.upload(banner);
            post.setBannerPhotoUrl(bannerUrl);
        }

        if (extraPhotos != null && extraPhotos.length > 0)
        {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : extraPhotos)
                if (!file.isEmpty())
                    urls.add(uploadService.upload(file));
            post.setExtraPhotos(String.join(",", urls));
        }

        post.setCreatedAt(LocalDateTime.now());
        NewsPost savedPost = newsRepository.save(post);

        eventPublisher.publishEvent(new NewsPostCreatedEvent(savedPost));

        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    public ResponseEntity<?> updateNewsPost(Long id, String title, String content,
                                            MultipartFile banner, MultipartFile[] extraPhotos)
    {
        NewsPost existing = newsRepository.findById(id).orElse(null);
        if (existing == null)
            return ResponseEntity.notFound().build();

        if (title != null && !title.isBlank())
            existing.setTitle(title);
        if (content != null && !content.isBlank())
            existing.setContent(content);
        if (banner != null && !banner.isEmpty())
        {
            String bannerUrl = uploadService.upload(banner);
            existing.setBannerPhotoUrl(bannerUrl);
        }
        if (extraPhotos != null && extraPhotos.length > 0)
        {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : extraPhotos)
                if (!file.isEmpty())
                    urls.add(uploadService.upload(file));
            existing.setExtraPhotos(String.join(",", urls));
        }

        newsRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    public boolean deleteNewsPost(Long id)
    {
        NewsPost existing = newsRepository.findById(id).orElse(null);
        if (existing == null)
        {
            return false;
        }
        // Delete banner image if exists
        if (existing.getBannerPhotoUrl() != null && !existing.getBannerPhotoUrl().isEmpty())
        {
            uploadService.delete(existing.getBannerPhotoUrl());
        }
        // Delete each extra photo if exists
        if (existing.getExtraPhotos() != null && !existing.getExtraPhotos().isEmpty())
        {
            String[] urls = existing.getExtraPhotos().split(",");
            for (String url : urls)
            {
                String trimmedUrl = url.trim();
                if (!trimmedUrl.isEmpty())
                {
                    uploadService.delete(trimmedUrl);
                }
            }
        }
        newsRepository.delete(existing);
        return true;
    }
}

