package com.qznu.jobhelper.user.converter;

import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.vo.AuthData;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户领域响应转换器，确保密码字段永不进入接口响应。
 */
@Component
public class UserConverter {

    public AuthData toAuthData(User user, String token) {
        return new AuthData(token, user.getNickname(), user.getRole());
    }

    public Map<String, Object> toProfile(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("createdAt", user.getCreatedAt());
        return data;
    }
}
