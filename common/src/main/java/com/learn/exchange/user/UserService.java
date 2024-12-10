package com.learn.exchange.user;

import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.enums.UserType;
import com.learn.exchange.model.ui.PasswordAuthEntity;
import com.learn.exchange.model.ui.UserEntity;
import com.learn.exchange.model.ui.UserProfileEntity;
import com.learn.exchange.support.AbstractDbService;
import com.learn.exchange.util.HashUtil;
import com.learn.exchange.util.RandomUtil;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class UserService extends AbstractDbService {

    public UserProfileEntity getUserProfile(Long userId) {
        return db.get(UserProfileEntity.class, userId);
    }

    @Nullable
    public UserProfileEntity fetchUserProfileByEmail(String email) {
        return db.from(UserProfileEntity.class).where("email = ?", email).first();
    }

    public UserProfileEntity getUserProfileByEmail(String email) {
        UserProfileEntity userProfile = fetchUserProfileByEmail(email);
        if(userProfile == null)
            throw new ApiException(ApiError.AUTH_SIGNIN_FAILED);
        return userProfile;
    }

    public UserProfileEntity signup(String email, String name, String password) {
        final long ts = System.currentTimeMillis();
        // insert user
        UserEntity user = new UserEntity();
        user.type = UserType.TAKER;
        user.createdAt = ts;
        db.insert(user);
        // insert user profile
        var up = new UserProfileEntity();
        up.userId = user.id;
        up.email = email;
        up.name = name;
        up.createdAt = up.updatedAt = ts;
        db.insert(up);
        var pa = new PasswordAuthEntity();
        pa.userId = user.id;
        pa.random = RandomUtil.createRandomString(32);
        pa.passwd = HashUtil.hmacSha256(password, pa.random);
        db.insert(pa);
        return up;
    }

    public UserProfileEntity signin(String email, String passwd) {
        UserProfileEntity userProfile = getUserProfileByEmail(email);
        // 通过 userId 找 PasswordAuthEntity
        PasswordAuthEntity pa = db.fetch(PasswordAuthEntity.class, userProfile.userId);
        if(pa == null)
            throw new ApiException(ApiError.USER_CANNOT_SIGNIN);
        // check password hash
        String hash = HashUtil.hmacSha256(passwd, pa.random);
        if(!hash.equals(pa.passwd))
            throw new ApiException(ApiError.AUTH_SIGNIN_FAILED);
        return userProfile;
    }
}
