package com.dayan.food.security;

import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/** Short-lived, purpose-bound identity delegated by Java, never selected by the model. */
@Component
public final class AgentServiceContext {
    private final String secret;
    private final AppUserMapper users;
    public AgentServiceContext(@Value("${app.agent.context-secret:}") String secret, AppUserMapper users) {
        this.secret=secret; this.users=users;
    }
    public String issue(AppUser user) {
        String payload=user.getId()+":"+user.getSubjectId()+":"+user.getAuthVersion()+":"
                + Instant.now().plusSeconds(60).getEpochSecond()+":recommend";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))+"."+signature(payload);
    }
    public AppUser verify(String context, String subject) {
        try {
            String[] parts=context.split("\\.", -1);
            if (parts.length!=2) throw new IllegalArgumentException();
            String payload=new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(signature(payload).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))
                throw new IllegalArgumentException();
            String[] fields=payload.split(":",-1);
            long now=Instant.now().getEpochSecond();
            if (fields.length!=5 || !fields[1].equals(subject) || !fields[4].equals("recommend")
                    || Long.parseLong(fields[3])<now || Long.parseLong(fields[3])>now+60) throw new IllegalArgumentException();
            AppUser user=users.findById(Long.valueOf(fields[0]));
            if (user==null || !user.isActive() || !subject.equals(user.getSubjectId())
                    || user.getAuthVersion()!=Long.parseLong(fields[2])) throw new IllegalArgumentException();
            return user;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Agent 身份上下文已失效");
        }
    }
    private String signature(String payload) {
        if (secret.length()<32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Agent 身份凭据未配置");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("context signing unavailable"); }
    }
}
