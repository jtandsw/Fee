package com.campus.payment.security;

import com.campus.payment.common.Result;
import com.campus.payment.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 解析 Authorization: Bearer &lt;accessToken&gt;，把登录人写入 SecurityContext。
 * 无 token 时直接放行，由 SecurityConfig 的 EntryPoint 返回 40101。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(PREFIX.length()).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            if (!JwtUtil.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
                writeError(response, ResultCode.UNAUTHORIZED);
                return;
            }
            if (tokenStore.isBlacklisted(claims.getId())) {
                writeError(response, ResultCode.UNAUTHORIZED);
                return;
            }
            LoginUser user = new LoginUser(
                    toLong(claims.get("uid")),
                    toLong(claims.get("sid")),
                    claims.get("username", String.class),
                    claims.get("realName", String.class),
                    claims.get("role", String.class));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            writeError(response, ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, ResultCode.UNAUTHORIZED);
        }
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode)));
    }
}
