package org.acs.stuco.backend.news;

import lombok.extern.slf4j.Slf4j;
import org.acs.stuco.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RestController
@RequestMapping("/api/news")
public class NewsController
{
    private final NewsService newsService;

    public NewsController(NewsService newsService)
    {
        this.newsService = newsService;
    }

    @GetMapping
    public ResponseEntity<Page<NewsPost>> getAllNews(Pageable pageable)
    {

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").descending());
        return ResponseEntity.ok(newsService.findAll(sorted));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsPost> getNewsById(@PathVariable Long id)
    {
        NewsPost post = newsService.findById(id);
        if (post == null)
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(post);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createNewsPost(
            @AuthenticationPrincipal User user,
            @RequestPart("post") NewsPostRequest request,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @RequestPart(value = "extraPhotos", required = false) MultipartFile[] extraPhotos
    )
    {
        log.info("User {} attempting to create news post", user.getEmail());
        if (user.getRole().ordinal() < 2)
        {
            return ResponseEntity.status(403).body("Forbidden");
        }
        return newsService.createNewsPost(request, banner, extraPhotos);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateNewsPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestPart("post") NewsPostRequest request,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @RequestPart(value = "extraPhotos", required = false) MultipartFile[] extraPhotos
    )
    {
        log.info("User {} attempting to update news post id {}", user.getEmail(), id);
        if (user.getRole().ordinal() < 2)
        {
            return ResponseEntity.status(403).body("Forbidden");
        }
        return newsService.updateNewsPost(id, request, banner, extraPhotos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNewsPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    )
    {
        if (user.getRole().ordinal() < 2)
        {
            return ResponseEntity.status(403).body("Forbidden");
        }
        boolean deleted = newsService.deleteNewsPost(id);
        if (!deleted)
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("News post deleted");
    }
}



