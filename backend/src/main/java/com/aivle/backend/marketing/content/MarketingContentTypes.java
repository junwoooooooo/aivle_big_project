package com.aivle.backend.marketing.content;

public final class MarketingContentTypes {
    private MarketingContentTypes() { }

    public enum Purpose {
        AWARENESS, PRODUCT_INTRODUCTION, EVENT_PROMOTION,
        LEAD_GENERATION, CONVERSION, RETENTION
    }

    public enum Channel {
        SOCIAL, DISPLAY_AD, WEB_BANNER, PRINT_POSTER, PRESENTATION, CUSTOM
    }

    public enum Format {
        SQUARE_1080(1080, 1080),
        PORTRAIT_1080_1350(1080, 1350),
        LANDSCAPE_1200_628(1200, 628),
        WIDE_1920_1080(1920, 1080),
        STORY_1080_1920(1080, 1920),
        A4_PORTRAIT(2480, 3508),
        CUSTOM(null, null);

        private final Integer width;
        private final Integer height;

        Format(Integer width, Integer height) {
            this.width = width;
            this.height = height;
        }

        public Integer width() { return width; }
        public Integer height() { return height; }
    }

    public enum Status { DRAFT, READY, FAILED, ARCHIVED }
    public enum Tone {
        TRUSTWORTHY, PROFESSIONAL, FRIENDLY, ENERGETIC,
        EMOTIONAL, MINIMAL, PREMIUM, BOLD
    }
    public enum Template { HERO_CENTER, SPLIT_VISUAL, EDITORIAL_POSTER, MINIMAL_CARD }
    public enum BackgroundType { SOLID, GRADIENT, PATTERN }
    public enum TextAlignment { LEFT, CENTER, RIGHT }
}
