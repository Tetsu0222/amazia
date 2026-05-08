package com.example.shared.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.Properties;

/**
 * {@code @PropertySource(factory = YamlPropertySourceFactory.class)} で
 * 任意の YAML ファイルを Spring Environment に読み込むためのファクトリ。
 *
 * <p>Spring Boot は {@code application.yml} のみ自動読み込みするため、
 * {@code config/batch/*.yml} のような分割ファイルは本ファクトリ経由で取り込む。
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(@Nullable String name, EncodedResource resource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());
        Properties properties = factory.getObject();
        String sourceName = (name != null) ? name : resource.getResource().getFilename();
        return new PropertiesPropertySource(
                sourceName != null ? sourceName : "yaml-property-source",
                properties != null ? properties : new Properties());
    }
}
