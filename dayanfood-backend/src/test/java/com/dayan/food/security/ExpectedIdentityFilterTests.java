package com.dayan.food.security;

import com.dayan.food.config.ActiveSessionFilter;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpectedIdentityFilterTests extends ActorTestSupport {
    @Test void staleTabMutationIsRejectedWithoutInvalidatingNewAccountSession() throws Exception {
        var users=mock(AppUserMapper.class);
        var account=new AppUser("new-account","encoded","New", UserRole.USER);
        actor(users,"new-account",account);
        var request=new MockHttpServletRequest("PATCH","/api/profile/signature");
        request.addHeader("X-Expected-User-Id","999999");
        var session=request.getSession(true);var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        new ActiveSessionFilter(users).doFilter(request,response,chain);
        assertEquals(409,response.getStatus());assertTrue(response.getContentAsString().contains("IDENTITY_CHANGED"));
        assertNull(chain.getRequest());assertSame(session,request.getSession(false));
    }
}
