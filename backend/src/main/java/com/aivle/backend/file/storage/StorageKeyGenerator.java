package com.aivle.backend.file.storage;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StorageKeyGenerator {
    public String documentKey(String extension) {
        return "documents/" + UUID.randomUUID() + "." + extension;
    }
}
