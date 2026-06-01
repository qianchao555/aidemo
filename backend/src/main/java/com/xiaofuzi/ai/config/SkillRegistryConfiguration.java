package com.xiaofuzi.ai.config;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class SkillRegistryConfiguration {

    @Value("${skills.file-system.path:./external-skills}")
    private String fileSystemSkillsPath;

    @Bean
    public SkillRegistry classpathSkillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills/")
                .build();
    }

    @Bean
    public SkillRegistry fileSystemSkillRegistry() {
        File skillsDir = new File(fileSystemSkillsPath);
        if (!skillsDir.exists()) {
            skillsDir.mkdirs();
        }
        return FileSystemSkillRegistry.builder()
                .userSkillsDirectory(skillsDir.getAbsolutePath())
                .projectSkillsDirectory(skillsDir.getAbsolutePath())
                .build();
    }
}