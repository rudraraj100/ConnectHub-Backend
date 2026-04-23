package com.api_gateway;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CorsFilter — single source of CORS headers for the entire platform.
 *
 * Runs at HIGHEST_PRECEDENCE so it fires before Spring Security and the
 * Gateway routing filter. Uses setHeader() (not addHeader()) so headers
 * are never duplicated even if any downstream component tries to add them.
 *
 * IMPORTANT for Spring Cloud Gateway MVC:
 *   We must ALWAYS call chain.doFilter() — even for OPTIONS preflights —
 *   because the actual proxy routing happens inside the filter chain.
 *   Returning early on OPTIONS would prevent the gateway from processing
 *   the request and responding correctly.
 *
 * Auth-service has NO CORS config — this is the only place.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Reflect the exact requesting origin (required when allowCredentials = true)
        String origin = request.getHeader("Origin");
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin",   origin);
        } else {
            response.setHeader("Access-Control-Allow-Origin",   "http://localhost:4200");
        }

        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods",     "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",     "Authorization, Content-Type, X-User-Id, X-User-Email, X-Refresh-Token");
        response.setHeader("Access-Control-Max-Age",           "3600");
        response.setHeader("Vary",                             "Origin");

        // Always continue the chain — for OPTIONS the gateway routing will
        // handle the response; for all other methods the proxy forwards the request.
        chain.doFilter(req, res);
    }
}
