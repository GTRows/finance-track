package com.fintrack.health;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.auth.AbstractWebMvcTestSupport;
import com.fintrack.auth.AutheliaForwardAuthFilter;
import com.fintrack.auth.FinTrackUserDetailsService;
import com.fintrack.auth.JwtAuthFilter;
import com.fintrack.common.exception.GlobalExceptionHandler;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class HealthControllerWebMvcTest extends AbstractWebMvcTestSupport {

    @Autowired MockMvc mockMvc;

    @MockBean DataSource dataSource;
    @MockBean RedisConnectionFactory redisConnectionFactory;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean AutheliaForwardAuthFilter autheliaForwardAuthFilter;
    @MockBean FinTrackUserDetailsService userDetailsService;

    @Test
    void healthReturnsUpWhenComponentsHealthy() throws Exception {
        stubAuthenticatedUser();
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        RedisConnection redis = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(redis);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.redis").value("UP"));
    }

    @Test
    void healthReportsDownWhenDatabaseFails() throws Exception {
        stubAuthenticatedUser();
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("boom"));
        when(redisConnectionFactory.getConnection()).thenReturn(mock(RedisConnection.class));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.database").value("DOWN"));
    }

    @Test
    void systemReturnsJvmMetrics() throws Exception {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/v1/health/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jvm.heapMaxMb").exists())
                .andExpect(jsonPath("$.jvm.availableProcessors").exists());
    }
}
