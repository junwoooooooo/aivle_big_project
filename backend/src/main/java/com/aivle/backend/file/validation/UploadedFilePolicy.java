package com.aivle.backend.file.validation;

import java.io.IOException;
import java.io.InputStream;

public interface UploadedFilePolicy {
    ValidatedUpload validate(UploadedFileMetadata metadata, InputStream inputStream) throws IOException;
}
