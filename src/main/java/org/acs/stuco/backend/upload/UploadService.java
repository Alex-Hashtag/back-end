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


    public boolean delete(String imageUrl)
    {
        try
        {

            String publicId = extractPublicIdFromUrl(imageUrl);

            if (publicId == null)
            {
                throw new IllegalArgumentException("Invalid Cloudinary URL");
            }

            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            return "ok".equals(result.get("result"));
        } catch (IOException ex)
        {
            throw new RuntimeException("Failed to delete image: " + imageUrl, ex);
        }
    }


    private String extractPublicIdFromUrl(String url)
    {

        String[] parts = url.split("/upload/");
        if (parts.length < 2)
        {
            return null;
        }

        String publicIdWithExtension = parts[1].replaceFirst("v\\d+/", "");

        int lastDotIndex = publicIdWithExtension.lastIndexOf('.');
        if (lastDotIndex > 0)
        {
            return publicIdWithExtension.substring(0, lastDotIndex);
        }
        return publicIdWithExtension;
    }
}

