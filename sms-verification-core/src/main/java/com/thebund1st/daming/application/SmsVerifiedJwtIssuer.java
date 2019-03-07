package com.thebund1st.daming.application;

import com.thebund1st.daming.core.MobilePhoneNumber;
import com.thebund1st.daming.time.Clock;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.security.Key;
import java.util.Date;

@RequiredArgsConstructor
public class SmsVerifiedJwtIssuer {
    @Setter
    private int expiresInSeconds = 900;

    private final Clock clock;

    private final Key key;

    public String issue(MobilePhoneNumber mobilePhoneNumber) {
        return Jwts.builder().setSubject("verifiedMobilePhoneNumber")
                .claim("mobile", mobilePhoneNumber.getValue())
                .signWith(key)
                .setExpiration(Date.from(clock.now().plusSeconds(expiresInSeconds).toInstant()))
                .compact();
    }


}
