package com.aivle.backend.pipeline.launchreadiness.application;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import java.awt.Color;
import java.util.List;

public final class KoreanPdfFonts {
    private KoreanPdfFonts() {}
    public static Font font(float size, int style, Color color) {
        for (String path : List.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Regular.otf", "C:/Windows/Fonts/malgun.ttf")) {
            try { return new Font(BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), size, style, color); }
            catch (Exception ignored) { }
        }
        throw new IllegalStateException("한국어 PDF 글꼴을 찾을 수 없습니다.");
    }
}
