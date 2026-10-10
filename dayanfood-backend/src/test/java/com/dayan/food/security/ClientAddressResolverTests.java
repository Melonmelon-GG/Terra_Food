package com.dayan.food.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressResolverTests {
    @Test void untrustedPeerCannotChooseItsBucket() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("198.51.100.2");
        request.addHeader("X-Forwarded-For", "203.0.113.9");
        assertEquals("198.51.100.2", new ClientAddressResolver("127.0.0.1/32").resolve(request));
    }
    @Test void chainStopsAtFirstUntrustedAddressAndIgnoresSpoofedPrefix() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("172.30.20.2");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.2, 127.0.0.1");
        assertEquals("198.51.100.2", new ClientAddressResolver("172.30.20.2/32,127.0.0.1/32").resolve(request));
    }
    @Test void forwardingHostnamesAreNeverResolved() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "attacker.invalid");
        assertEquals("127.0.0.1", new ClientAddressResolver("127.0.0.1/32").resolve(request));
    }
}
