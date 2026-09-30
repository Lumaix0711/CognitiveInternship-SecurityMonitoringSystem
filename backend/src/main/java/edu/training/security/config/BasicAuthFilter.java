package edu.training.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

/**
 * 公网演示鉴权，三层并行：
 * 1) 页面外壳(HTML)放行，数据接口与WebSocket受保护；
 * 2) 接受凭证：Authorization Basic / ?pw=密码 / Cookie sm_auth=密码（任一命中即放行，
 *    命中后通过 Set-Cookie 把密码下发给浏览器，使 WebSocket 握手可自动携带）；
 * 3) /api/ai/event 为识别服务机器回调，白名单。
 * 仅当配置了 app.auth.user/app.auth.password 时启用。
 */
@Component
@Order(1)
public class BasicAuthFilter extends OncePerRequestFilter {

    private static final Set<String> OPEN_PAGES = Set.of("/", "/index.html", "/favicon.ico");

    @Value("${app.auth.user:}")
    private String user;

    @Value("${app.auth.password:}")
    private String password;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (password == null || password.isBlank()) {
            chain.doFilter(req, resp);
            return;
        }
        String uri = req.getRequestURI();
        boolean open = OPEN_PAGES.contains(uri) || uri.startsWith("/api/ai/event") || uri.startsWith("/error")
                || uri.startsWith("/js/") || uri.startsWith("/css/") || uri.endsWith(".ico");
        if (open) {
            chain.doFilter(req, resp);
            return;
        }

        boolean ok = checkBasic(req.getHeader("Authorization"))
                || password.equals(req.getParameter("pw"))
                || checkCookie(req);

        if (ok) {
            if (req.getCookies() == null || req.getParameter("pw") != null) {
                Cookie c = new Cookie("sm_auth", password);
                c.setPath("/");
                c.setMaxAge(7 * 24 * 3600);
                resp.addCookie(c);
            }
            chain.doFilter(req, resp);
            return;
        }
        resp.setStatus(401);
        resp.setHeader("WWW-Authenticate", "Basic realm=\"SecurityMonitor\", charset=\"UTF-8\"");
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().write("需要访问密码");
    }

    private boolean checkBasic(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        try {
            String decoded = new String(Base64.getDecoder()
                    .decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int i = decoded.indexOf(':');
            return i >= 0
                    && MessageDigest.isEqual(decoded.substring(i + 1).getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean checkCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if ("sm_auth".equals(c.getName())
                    && MessageDigest.isEqual(c.getValue().getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }
}
