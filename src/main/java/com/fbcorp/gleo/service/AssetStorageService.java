package com.fbcorp.gleo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AssetStorageService {

    private final Path rootDir;
    private final Path vendorDir;

    public AssetStorageService(@Value("${gleo.uploads.root:uploads}") String rootDirectory) {
        try {
            this.rootDir = Paths.get(rootDirectory).toAbsolutePath().normalize();
            this.vendorDir = rootDir.resolve("vendors");
            Files.createDirectories(this.vendorDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize upload directories", ex);
        }
    }

    public String storeVendorImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "vendor-image"));
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = vendorDir.resolve(filename);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/vendors/" + filename;
    }

    public Path getRootDir() {
        return rootDir;
    }
}

