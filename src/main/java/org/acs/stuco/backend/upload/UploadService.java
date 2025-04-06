package org.acs.stuco.backend.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;


@Service
public class UploadService
{

    private final Cloudinary cloudinary;

    public UploadService(
            @Value("${cloudinary.cloud_name}") String cloudName,
            @Value("${cloudinary.api_key}") String apiKey,
            @Value("${cloudinary.api_secret}") String apiSecret
    )
    {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public String upload(MultipartFile file)
    {
        if (file.isEmpty())
        {
            throw new RuntimeException("Cannot upload an empty file.");
        }

        try
        {
            String fileName = UUID.randomUUID().toString();

            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("public_id", fileName)
            );

            return (String) uploadResult.get("secure_url");
        } catch (IOException ex)
        {
            throw new RuntimeException("Could not store file. Please try again!", ex);
        }
    }

    /**
     * Deletes an image from Cloudinary using its public ID (extracted from URL)
     *
     * @param imageUrl The full Cloudinary URL of the image to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean delete(String imageUrl)
    {
        try
        {
            // Extract public ID from the Cloudinary URL
            String publicId = extractPublicIdFromUrl(imageUrl);

            if (publicId == null)
            {
                throw new IllegalArgumentException("Invalid Cloudinary URL");
            }

            // Delete the image
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            // Check if deletion was successful
            return "ok".equals(result.get("result"));
        } catch (IOException ex)
        {
            throw new RuntimeException("Failed to delete image: " + imageUrl, ex);
        }
    }

    /**
     * Extracts the public ID from a Cloudinary URL
     *
     * @param url The Cloudinary URL
     * @return The public ID or null if URL is invalid
     */
    private String extractPublicIdFromUrl(String url)
    {
        // Cloudinary URL pattern: https://res.cloudinary.com/<cloud_name>/image/upload/<public_id>.<format>
        String[] parts = url.split("/upload/");
        if (parts.length < 2)
        {
            return null;
        }

        // Remove any version number (v123456789) if present
        String publicIdWithExtension = parts[1].replaceFirst("v\\d+/", "");

        // Remove file extension
        int lastDotIndex = publicIdWithExtension.lastIndexOf('.');
        if (lastDotIndex > 0)
        {
            return publicIdWithExtension.substring(0, lastDotIndex);
        }
        return publicIdWithExtension;
    }
}