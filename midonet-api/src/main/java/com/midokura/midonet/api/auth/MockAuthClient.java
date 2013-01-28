/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.auth;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurable auth client that skips authentication but allows setting of
 * roles.
 */
public final class MockAuthClient implements AuthClient {

    private final static Logger log = LoggerFactory
            .getLogger(MockAuthClient.class);
    private final AuthConfig config;
    private final Map<String, UserIdentity> tokenMap;

    /**
     * Create a MockAuthClient object.
     *
     * @param config
     *            AuthConfig object.
     */
    @Inject
    public MockAuthClient(AuthConfig config) {

        this.config = config;
        this.tokenMap = new HashMap<String, UserIdentity>();
        String token = config.getAdminToken();
        if (token != null && token.length() > 0) {
            setRoles(token, AuthRole.ADMIN);
        }

        token = config.getTenantAdminToken();
        if (token != null && token.length() > 0) {
            setRoles(token, AuthRole.TENANT_ADMIN);
        }

        token = config.getTenantUserToken();
        if (token != null && token.length() > 0) {
            setRoles(token, AuthRole.TENANT_USER);
        }

    }

    private UserIdentity createUserIdentity() {
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setTenantId("no_auth_tenant_id");
        userIdentity.setTenantName("no_auth_tenant_name");
        userIdentity.setUserId("no_auth_user");
        userIdentity.setToken("no_auth_token");
        return userIdentity;
    }

    private void setRoles(String tokenStr, String role) {
        String[] tokens = tokenStr.split(",");
        for (String token : tokens) {
            String tok = token.trim();
            if (tok.length() > 0) {
                UserIdentity identity = tokenMap.get(tok);
                if (identity == null) {
                    identity = createUserIdentity();
                    tokenMap.put(tok, identity);
                }
                identity.addRole(role);
            }
        }
    }

    /**
     * Return a UserIdentity object.
     *
     * @param token
     *            Token to use to get the roles.
     * @return UserIdentity object.
     */
    @Override
    public UserIdentity getUserIdentityByToken(String token) {
        log.debug("MockAuthClient.getUserIdentityByToken entered. {}", token);

        UserIdentity user = tokenMap.get(token);
        if (user == null) {
            // For backward compatibility, no token == admin privilege.
            user = createUserIdentity();
            user.addRole(AuthRole.ADMIN);
        }

        log.debug("MockAuthClient.getUserIdentityByToken exiting. {}", user);
        return user;
    }
}
