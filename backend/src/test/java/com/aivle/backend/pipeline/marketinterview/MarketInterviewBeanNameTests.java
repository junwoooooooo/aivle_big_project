package com.aivle.backend.pipeline.marketinterview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

class MarketInterviewBeanNameTests {

    @Test
    void mainCoreAndFullAdapterRegisterWithDistinctSpringBeanNames() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registry.setAllowBeanDefinitionOverriding(false);

        AnnotatedGenericBeanDefinition mainDefinition = new AnnotatedGenericBeanDefinition(
            com.aivle.backend.pipeline.market.MarketInterviewInputFactory.class);
        String mainName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(mainDefinition, registry);
        registry.registerBeanDefinition(mainName, mainDefinition);

        AnnotatedGenericBeanDefinition adapterDefinition = new AnnotatedGenericBeanDefinition(
            MarketInterviewInputFactory.class);
        String adapterName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(adapterDefinition, registry);

        assertThat(mainName).isEqualTo("marketInterviewInputFactory");
        assertThat(adapterName).isEqualTo("fullMarketInterviewInputFactory");
        assertThat(adapterName).isNotEqualTo(mainName);
        assertThatCode(() -> registry.registerBeanDefinition(adapterName, adapterDefinition))
            .doesNotThrowAnyException();
    }
}
