package com.aivle.backend.document.parsing;

import java.io.InputStream;

/**
 * Format-neutral document parsing port.
 *
 * <p>The caller owns the supplied stream. Implementations must not close it.</p>
 */
public interface DocumentParser {

    boolean supports(DocumentParseRequest request);

    ParsedDocument parse(InputStream source, DocumentParseRequest request);
}
