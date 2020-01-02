package com.appsmith.server.configurations;

import com.appsmith.server.constants.AclConstants;
import com.appsmith.server.domains.LoginSource;
import com.appsmith.server.domains.User;
import com.appsmith.server.domains.UserState;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * This code has been copied from WebSessionServerOAuth2AuthorizedClientRepository.java
 * which also implements ServerOAuth2AuthorizedClientRepository. This was done to make changes
 * to saveAuthorizedClient to also handle adding users to UserRepository.
 * <p>
 * This was done because on authorization, the user needs to be stored in appsmith domain.
 * To achieve this, saveAuthorizedClient function has been edited in the following manner.
 * In the reactive flow, post doOnSuccess transformation, another Mono.then has been added. In this,
 * Authentication object is passed to checkAndCreateUser function. This object is used to get OidcUser from which
 * user attributes like name, email, etc are extracted. If the user doesnt exist in User
 * Repository, a new user is created and saved.
 * <p>
 * The ClientUserRepository is created during SecurityWebFilterChain Bean creation. By
 * configuring to use Oauth2Login, this ServerOAuth2AuthorizedClientRepository implementation
 * is injected. This hack is used to ensure that on successful authentication, we are able
 * to record the user in our database. Since ServerOAuth2AuthorizedClientRepository's
 * saveAuthorizedClient is called on every successful OAuth2 authentication, this solves the problem
 * of plugging a handler for the same purpose.
 */
@Slf4j
@Component
public class ClientUserRepository implements ServerOAuth2AuthorizedClientRepository {

    private static final String DEFAULT_AUTHORIZED_CLIENTS_ATTR_NAME =
            WebSessionServerOAuth2AuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS";

    private final String sessionAttributeName = DEFAULT_AUTHORIZED_CLIENTS_ATTR_NAME;

    UserService userService;

    CommonConfig commonConfig;

    public ClientUserRepository(UserService userService, CommonConfig commonConfig) {
        this.userService = userService;
        this.commonConfig = commonConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> Mono<T> loadAuthorizedClient(String clientRegistrationId, Authentication principal,
                                                                           ServerWebExchange exchange) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.notNull(exchange, "exchange cannot be null");
        return exchange.getSession()
                .map(this::getAuthorizedClients)
                .flatMap(clients -> Mono.justOrEmpty((T) clients.get(clientRegistrationId)));
    }

    @Override
    public Mono<Void> saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                           ServerWebExchange exchange) {
        Assert.notNull(authorizedClient, "authorizedClient cannot be null");
        Assert.notNull(exchange, "exchange cannot be null");
        Assert.notNull(principal, "authentication object cannot be null");

        // Check if the list of configured custom domains match the authenticated principal.
        // This is to provide more control over which accounts can be used to access the application.
        // TODO: This is not a good way to do this. Ideally, we should pass "hd=example.com" to OAuth2 provider to list relevant accounts only
        if (!commonConfig.getAllowedDomains().isEmpty()) {
            DefaultOidcUser userPrincipal = (DefaultOidcUser) principal.getPrincipal();
            String domain = (String) userPrincipal.getAttributes().getOrDefault("hd", "");
            if (!commonConfig.getAllowedDomains().contains(domain)) {
                return Mono.error(new AppsmithException(AppsmithError.UNAUTHORIZED_DOMAIN));
            }
        }

        return exchange.getSession()
                .doOnSuccess(session -> {
                    Map<String, OAuth2AuthorizedClient> authorizedClients = getAuthorizedClients(session);
                    authorizedClients.put(authorizedClient.getClientRegistration().getRegistrationId(), authorizedClient);
                    session.getAttributes().put(this.sessionAttributeName, authorizedClients);
                })
                /*
                 * TODO
                 * Need to test how this behaves in the following :
                 * 1. Clustered environment
                 * 2. Redis saved sessions
                 */
                .then(Mono.empty());
    }

    public Mono<User> checkAndCreateUser(OidcUser user) {
        User newUser = new User();
        newUser.setName(user.getFullName());
        newUser.setEmail(user.getEmail());
        newUser.setSource(LoginSource.GOOGLE);
        newUser.setState(UserState.ACTIVATED);
        newUser.setIsEnabled(true);
        // TODO: Check if this is a valid permission available in the DB
        // TODO: Check to see if this user was invited or is it a new sign up
        Set<String> permissions = new HashSet<>();
        // Adding the create organization permission because this is a new user and we will have to create an organization
        // after this for the user.
        permissions.addAll(AclConstants.PERMISSIONS_CRUD_ORG);
        newUser.setPermissions(permissions);

        return userService.findByEmail(user.getEmail())
                .switchIfEmpty(Mono.defer(() -> userService.create(newUser))); //In case the user doesn't exist, create and save the user.
    }

    @Override
    public Mono<Void> removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                             ServerWebExchange exchange) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.notNull(exchange, "exchange cannot be null");
        return exchange.getSession()
                .doOnSuccess(session -> {
                    Map<String, OAuth2AuthorizedClient> authorizedClients = getAuthorizedClients(session);
                    authorizedClients.remove(clientRegistrationId);
                    if (authorizedClients.isEmpty()) {
                        session.getAttributes().remove(this.sessionAttributeName);
                    } else {
                        session.getAttributes().put(this.sessionAttributeName, authorizedClients);
                    }
                })
                .then(Mono.empty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, OAuth2AuthorizedClient> getAuthorizedClients(WebSession session) {
        Map<String, OAuth2AuthorizedClient> authorizedClients = session == null ? null :
                (Map<String, OAuth2AuthorizedClient>) session.getAttribute(this.sessionAttributeName);
        if (authorizedClients == null) {
            authorizedClients = new HashMap<>();
        }
        return authorizedClients;
    }
}
