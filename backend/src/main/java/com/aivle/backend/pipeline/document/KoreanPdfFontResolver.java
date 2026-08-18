package com.aivle.backend.pipeline.document;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KoreanPdfFontResolver {
    public static final String FAMILY = "Korean Report";

    public InputStream open(boolean bold) {
        List<String> candidates = bold ? List.of(
            "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
            "/usr/share/fonts/truetype/nanum/NanumGothic-Bold.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Bold.otf",
            "C:/Windows/Fonts/malgunbd.ttf"
        ) : List.of(
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Regular.otf",
            "C:/Windows/Fonts/malgun.ttf"
        );
        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate);
                if (Files.isRegularFile(path)) return new FileInputStream(path.toFile());
            } catch (Exception ignored) {
                // Continue through known local, embeddable TTF/OTF candidates only.
            }
        }
        throw new IllegalStateException("PDF용 임베딩 가능한 한국어 TTF/OTF 폰트를 찾을 수 없습니다.");
    }
}
