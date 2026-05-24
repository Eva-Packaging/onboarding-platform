package xyz.catuns.onboarding.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.catuns.onboarding.user.filter.MdcUserInterceptor;

@Configuration
public class MdcWebMvcConfig implements WebMvcConfigurer {

    private final MdcUserInterceptor mdcUserInterceptor;

    public MdcWebMvcConfig(MdcUserInterceptor mdcUserInterceptor) {
        this.mdcUserInterceptor = mdcUserInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mdcUserInterceptor);
    }
}
