// NewsPostRequest.java
package org.acs.stuco.backend.news;

public record NewsPostRequest(
        String title,
        String content,
        String bannerPhotoUrl, // These fields may be empty if files are uploaded
        String extraPhotos   // Possibly comma-separated or JSON
)
{
}
