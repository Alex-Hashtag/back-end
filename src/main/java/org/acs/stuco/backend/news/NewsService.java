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
import java.util.Arrays;
import java.util.List;

@Service
public class NewsService {
    private final NewsRepository newsRepository;
    private final UploadService uploadService;
    private final ApplicationEventPublisher eventPublisher;

    public NewsService(NewsRepository newsRepository, UploadService uploadService, ApplicationEventPublisher eventPublisher) {
        this.newsRepository = newsRepository;
        this.uploadService = uploadService;
        this.eventPublisher = eventPublisher;
    }

    public Page<NewsPost> findAll(Pageable pageable) {
        return newsRepository.findAll(pageable);
    }

    public NewsPost findById(Long id) {
        return newsRepository.findById(id).orElse(null);
    }

    public ResponseEntity<?> createNewsPost(NewsPostRequest request,
                                            MultipartFile banner, MultipartFile[] extraPhotos) {
        if (request.title() == null || request.title().isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Title is required");
        if (request.content() == null || request.content().isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Content is required");

        NewsPost post = new NewsPost();
        post.setTitle(request.title());
        post.setContent(request.content());

        // Process banner photo
        if (banner != null && !banner.isEmpty()) {
            String bannerUrl = uploadService.upload(banner);
            post.setBannerPhotoUrl(bannerUrl);
        } else {
            // No new banner uploaded—store whatever is sent (if any)
            post.setBannerPhotoUrl(request.bannerPhotoUrl());
        }

        // Process extra photos
        List<String> finalExtraUrls = new ArrayList<>();
        if (extraPhotos != null && extraPhotos.length > 0) {
            // Upload new files
            for (MultipartFile file : extraPhotos) {
                if (!file.isEmpty()) {
                    finalExtraUrls.add(uploadService.upload(file));
                }
            }
        }
        // Also merge with any kept extra URLs from the request DTO
        if (request.extraPhotos() != null && !request.extraPhotos().isBlank()) {
            List<String> kept = Arrays.asList(request.extraPhotos().split(","));
            finalExtraUrls.addAll(kept);
        }
        post.setExtraPhotos(String.join(",", finalExtraUrls));

        post.setCreatedAt(LocalDateTime.now());
        NewsPost savedPost = newsRepository.save(post);

        eventPublisher.publishEvent(new NewsPostCreatedEvent(savedPost));

        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    public ResponseEntity<?> updateNewsPost(Long id, NewsPostRequest request,
                                            MultipartFile banner, MultipartFile[] extraPhotos) {
        NewsPost existing = newsRepository.findById(id).orElse(null);
        if (existing == null)
            return ResponseEntity.notFound().build();

        if (request.title() != null && !request.title().isBlank())
            existing.setTitle(request.title());
        if (request.content() != null && !request.content().isBlank())
            existing.setContent(request.content());

        // --- Handle Banner Update ---
        if (banner != null && !banner.isEmpty()) {
            // New file uploaded: remove old banner if it exists
            if (existing.getBannerPhotoUrl() != null && !existing.getBannerPhotoUrl().isBlank()) {
                uploadService.delete(existing.getBannerPhotoUrl());
            }
            String newBannerUrl = uploadService.upload(banner);
            existing.setBannerPhotoUrl(newBannerUrl);
        } else {
            // No new file uploaded. Check if the client wants to remove the banner.
            // (Client should send an empty bannerPhotoUrl in the JSON if the banner was removed.)
            if ((request.bannerPhotoUrl() == null || request.bannerPhotoUrl().isBlank())
                    && existing.getBannerPhotoUrl() != null && !existing.getBannerPhotoUrl().isBlank()) {
                uploadService.delete(existing.getBannerPhotoUrl());
                existing.setBannerPhotoUrl("");
            } else {
                // Otherwise, leave the banner intact.
                // Optionally, you can update it from the DTO:
                existing.setBannerPhotoUrl(request.bannerPhotoUrl());
            }
        }

        // --- Handle Extra Photos Update ---
        if (extraPhotos != null && extraPhotos.length > 0) {
            // Upload new extra photos
            List<String> newUploadedUrls = new ArrayList<>();
            for (MultipartFile file : extraPhotos) {
                if (!file.isEmpty()) {
                    newUploadedUrls.add(uploadService.upload(file));
                }
            }
            // Combine with kept extra URLs from the request JSON
            List<String> keptUrls = new ArrayList<>();
            if (request.extraPhotos() != null && !request.extraPhotos().isBlank()) {
                keptUrls = Arrays.asList(request.extraPhotos().split(","));
            }
            List<String> finalUrls = new ArrayList<>();
            finalUrls.addAll(keptUrls);
            finalUrls.addAll(newUploadedUrls);

            // Delete any previous images that are not in the final list
            if (existing.getExtraPhotos() != null && !existing.getExtraPhotos().isBlank()) {
                List<String> oldUrls = Arrays.stream(existing.getExtraPhotos().split(","))
                        .map(String::trim)
                        .toList();
                for (String oldUrl : oldUrls) {
                    if (!finalUrls.contains(oldUrl)) {
                        uploadService.delete(oldUrl);
                    }
                }
            }
            existing.setExtraPhotos(String.join(",", finalUrls));
        } else {
            // No new extra files uploaded. Use the kept URLs from the request to update.
            List<String> finalUrls = new ArrayList<>();
            if (request.extraPhotos() != null && !request.extraPhotos().isBlank()) {
                finalUrls = Arrays.asList(request.extraPhotos().split(","));
            }
            // Delete any old images that are not in the kept list.
            if (existing.getExtraPhotos() != null && !existing.getExtraPhotos().isBlank()) {
                List<String> oldUrls = Arrays.stream(existing.getExtraPhotos().split(","))
                        .map(String::trim)
                        .toList();
                for (String oldUrl : oldUrls) {
                    if (!finalUrls.contains(oldUrl)) {
                        uploadService.delete(oldUrl);
                    }
                }
            }
            existing.setExtraPhotos(String.join(",", finalUrls));
        }

        newsRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    public boolean deleteNewsPost(Long id) {
        NewsPost existing = newsRepository.findById(id).orElse(null);
        if (existing == null) {
            return false;
        }

        if (existing.getBannerPhotoUrl() != null && !existing.getBannerPhotoUrl().isEmpty()) {
            uploadService.delete(existing.getBannerPhotoUrl());
        }

        if (existing.getExtraPhotos() != null && !existing.getExtraPhotos().isEmpty()) {
            String[] urls = existing.getExtraPhotos().split(",");
            for (String url : urls) {
                String trimmedUrl = url.trim();
                if (!trimmedUrl.isEmpty()) {
                    uploadService.delete(trimmedUrl);
                }
            }
        }

        newsRepository.delete(existing);
        return true;
    }
}
