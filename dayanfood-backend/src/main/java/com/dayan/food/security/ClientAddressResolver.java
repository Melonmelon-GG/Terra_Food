package com.dayan.food.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

/** Forwarded addresses are accepted only from explicitly configured proxies. */
@Component
public class ClientAddressResolver {
    private final List<IpAddressMatcher> trusted;
    public ClientAddressResolver(@Value("${app.security.trusted-proxies:127.0.0.1/32,::1/128}") String cidrs) {
        trusted = Arrays.stream(cidrs.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) return peer;
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.length() > 1024) return peer;
        String[] chain = header.split(",");
        if (chain.length > 16) return peer;
        String current = peer;
        for (int i = chain.length - 1; i >= 0 && isTrusted(current); i--) {
            String candidate = chain[i].trim();
            if (!candidate.matches("[0-9a-fA-F:.]+")) return peer;
            try { current = java.net.InetAddress.getByName(candidate).getHostAddress(); }
            catch (java.net.UnknownHostException invalid) { return peer; }
        }
        return current;
    }
    private boolean isTrusted(String address) {
        return trusted.stream().anyMatch(matcher -> matcher.matches(address));
    }
}
