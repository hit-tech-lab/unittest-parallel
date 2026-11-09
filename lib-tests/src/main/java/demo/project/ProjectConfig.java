package demo.project;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Cấu hình tối thiểu của một "dự án": chỉ quét các bean ứng dụng, không auto-config; hạ tầng bên ngoài là bean mock-only. */
@Configuration
@ComponentScan(basePackageClasses = ProjectConfig.class)
public class ProjectConfig {
}
