package com.shopsphere.core.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Small helper for controllers/services to grab the authenticated caller. */
@Component
public class CurrentUser {

    public UserPrincipal get() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
