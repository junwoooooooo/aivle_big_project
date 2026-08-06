package com.aivle.backend.document.application;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface UploadContent {
    InputStream openStream() throws IOException;
}
