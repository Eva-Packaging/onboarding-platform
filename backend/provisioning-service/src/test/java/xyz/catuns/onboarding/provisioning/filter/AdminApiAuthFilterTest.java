package xyz.catuns.onboarding.provisioning.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.onboarding.common.security.provider.PayloadTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminApiAuthFilterTest {

    @Mock
    private PayloadTokenProvider tokenProvider;

    @Mock
    private FilterChain chain;

    private AdminApiAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AdminApiAuthFilter(tokenProvider, new ObjectMapper());
    }

    @Test
    void nonAdminPath_filterNotApplied_chainProceedsUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/onboarding/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void adminPath_noAuthorizationHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/provider-health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void adminPath_invalidToken_returns401() throws Exception {
        when(tokenProvider.validate("bad.token")).thenThrow(new RuntimeException("Token expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/provider-health");
        request.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void adminPath_validTokenNonAdminUser_returns403() throws Exception {
        when(tokenProvider.validate("user.token")).thenReturn(new Payload("user-1", "corr-1", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/provider-health");
        request.addHeader("Authorization", "Bearer user.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void adminPath_validAdminToken_chainProceeds() throws Exception {
        when(tokenProvider.validate("admin.token")).thenReturn(new Payload("admin-1", "corr-1", true));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/provider-health");
        request.addHeader("Authorization", "Bearer admin.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
