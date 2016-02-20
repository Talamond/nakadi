package de.zalando.aruha.nakadi.security;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import de.zalando.aruha.nakadi.config.SecuritySettings;
import de.zalando.aruha.nakadi.repository.TopicRepository;
import de.zalando.aruha.nakadi.repository.db.EventTypeDbRepository;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import java.util.Set;

import static de.zalando.aruha.nakadi.utils.TestUtils.randomString;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class EndpointsSecurityTest {

    @Configuration
    public static class Config {

        @Value("${nakadi.oauth2.scopes.uid}")
        protected String uidScope;

        @Value("${nakadi.oauth2.scopes.nakadiRead}")
        protected String nakadiReadScope;

        @Value("${nakadi.oauth2.scopes.eventTypeWrite}")
        protected String eventTypeWriteScope;

        @Value("${nakadi.oauth2.scopes.eventStreamRead}")
        protected String eventStreamReadScope;

        @Value("${nakadi.oauth2.scopes.eventStreamWrite}")
        protected String eventStreamWriteScope;

        private static final Multimap<String, String> scopesForTokens = ArrayListMultimap.create();

        @PostConstruct
        public void mockTokensScopes() {
            scopesForTokens.put(TOKEN_WITH_UID_SCOPE, uidScope);
            scopesForTokens.put(TOKEN_WITH_RANDOM_SCOPE, randomString());
        }

        @Bean
        @Primary
        public ResourceServerTokenServices mockResourceTokenServices() {
            final ResourceServerTokenServices tokenServices = mock(ResourceServerTokenServices.class);

            when(tokenServices.loadAuthentication(any())).thenAnswer(invocation -> {

                UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken("user", "N/A",
                        AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_USER"));

                final String token = (String) invocation.getArguments()[0];
                final Set<String> scopes = ImmutableSet.copyOf(scopesForTokens.get(token));

                OAuth2Request request = new OAuth2Request(null, null, null, true, scopes, null, null, null, null);
                return new OAuth2Authentication(request, user);
            });
            return tokenServices;
        }

        @Bean
        @Primary
        public SecuritySettings mockSecuritySettings() {
            final SecuritySettings settings = mock(SecuritySettings.class);
            when(settings.getAuthMode()).thenReturn(authMode);
            when(settings.getClientId()).thenReturn("foo");
            when(settings.getTokenInfoUri()).thenReturn("http://foo.bar");
            return settings;
        }

        @Bean
        @Primary
        public TopicRepository mockTopicRepository() {
            return mock(TopicRepository.class);
        }

        @Bean
        @Primary
        public EventTypeDbRepository mockDbRepository() {
            return mock(EventTypeDbRepository.class);
        }
    }

    protected static final ResultMatcher STATUS_NOT_401_OR_403 = status()
            .is(not(isOneOf(UNAUTHORIZED.value(), FORBIDDEN.value())));

    protected static final String TOKEN_WITH_UID_SCOPE = randomString();
    protected static final String TOKEN_WITH_RANDOM_SCOPE = randomString();

    protected static SecuritySettings.AuthMode authMode;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    protected MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    protected MockHttpServletRequestBuilder addTokenHeader(final MockHttpServletRequestBuilder builder,
                                                         final String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

}
