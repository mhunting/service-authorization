/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-authorization
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.auth.endpoint;

import com.epam.reportportal.auth.ReportPortalClient;
import com.epam.reportportal.auth.ReportPortalUser;
import com.epam.reportportal.auth.TokenServicesFacade;
import com.epam.ta.reportportal.commons.Preconditions;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base SSO controller
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@RestController
public class SsoEndpoint {

    private final TokenServicesFacade tokenServicesFacade;
    private final SecurityContextLogoutHandler logoutHandler;

    @Autowired
    public SsoEndpoint(TokenServicesFacade tokenServicesFacade) {
        this.tokenServicesFacade = tokenServicesFacade;
        this.logoutHandler = new SecurityContextLogoutHandler();

        this.logoutHandler.setClearAuthentication(true);
        this.logoutHandler.setInvalidateHttpSession(true);
    }

    @RequestMapping({ "/sso/me", "/sso/user" })
    public Map<String, Object> user(Authentication user) {

        ImmutableMap.Builder<String, Object> details = ImmutableMap.<String, Object>builder().put("user", user.getName())
                .put("authorities", user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

        if (user.getPrincipal() instanceof ReportPortalUser){
            details.put("projects", ((ReportPortalUser) user.getPrincipal()).getProjectRoles());
        }
        return details.build();
    }

    @RequestMapping(value = { "/sso/me" }, method = RequestMethod.DELETE)
    @ApiOperation(value = "Revoke token")
    public OperationCompletionRS revokeToken(HttpServletRequest request, HttpServletResponse response,
            OAuth2Authentication user) {
        String token = ((OAuth2AuthenticationDetails) user.getDetails()).getTokenValue();
        tokenServicesFacade.revokeToken(token);
        logoutHandler.logout(request, response, user);
        return new OperationCompletionRS(String.format("Token '%s' has revoked", token));
    }

    @RequestMapping(value = { "/sso/me/apitoken" }, method = RequestMethod.GET)
    @ApiOperation(value = "Get api token")
    public OAuth2AccessToken getApiToken(Principal user) {
        Optional<OAuth2AccessToken> tokens = tokenServicesFacade.getTokens(user.getName(), ReportPortalClient.api)
                .findAny();
        BusinessRule.expect(tokens, Preconditions.IS_PRESENT).verify(ErrorType.USER_NOT_FOUND, user.getName());
        return tokens.get();
    }

    @RequestMapping(value = { "/sso/me/apitoken" }, method = RequestMethod.POST)
    @ApiOperation(value = "Create api token")
    public OAuth2AccessToken createApiToken(OAuth2Authentication user) {
        tokenServicesFacade.revokeUserTokens(user.getName(), ReportPortalClient.api);
        return tokenServicesFacade.createToken(ReportPortalClient.api, user.getName(), user.getUserAuthentication());
    }

    @RequestMapping(value = { "/sso/internal/user/{user}" }, method = RequestMethod.DELETE)
    @ApiOperation(value = "Revoke all user tokens")
    public OperationCompletionRS revokeUserTokens(@PathVariable String user) {
        tokenServicesFacade.revokeUserTokens(user);
        return new OperationCompletionRS(String.format("Token of user '%s' has been revoked", user));
    }

}
