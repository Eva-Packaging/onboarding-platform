package xyz.catuns.onboarding.user.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MdcUserInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Object handler;

    private MdcUserInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new MdcUserInterceptor();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void preHandle_authenticatedUser_setsUserIdInMdc() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user-uuid-123", null, List.of())
        );

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(MDC.get("userId")).isEqualTo("user-uuid-123");
    }

    @Test
    void preHandle_noAuthentication_doesNotSetUserId() throws Exception {
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void preHandle_anonymousUser_doesNotSetUserId() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of())
        );

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void afterCompletion_clearsUserIdFromMdc() throws Exception {
        MDC.put("userId", "user-uuid-123");

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void afterCompletion_withException_stillClearsMdc() throws Exception {
        MDC.put("userId", "user-uuid-123");

        interceptor.afterCompletion(request, response, handler, new RuntimeException("boom"));

        assertThat(MDC.get("userId")).isNull();
    }
}
