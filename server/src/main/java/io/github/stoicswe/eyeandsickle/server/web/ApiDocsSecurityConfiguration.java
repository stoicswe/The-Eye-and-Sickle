package io.github.stoicswe.eyeandsickle.server.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The HTTP security policy — currently, Spring Boot's default with the API documentation carved out.
 *
 * <h2>⚠ READ THIS BEFORE ASSUMING THE REST API WORKS. IT DOES NOT.</h2>
 *
 * {@code spring-boot-starter-security} is on the classpath and, until this class, <strong>no
 * {@code SecurityFilterChain} bean existed anywhere</strong>. Boot's default therefore applied:
 * {@code anyRequest().authenticated()}. Every one of this server's endpoints — sign-in, LAN join, the
 * game session transport, the whole federation surface — answers <strong>401</strong> to every
 * request.
 *
 * <p>⚠ <strong>Nothing had noticed, because no test in this module has ever made an HTTP request to
 * one of its own controllers.</strong> Every integration test is repository- or service-level; the
 * only {@code RestClient} in the test tree is an <em>outbound</em> peer transport. The 401 surfaced
 * the first time anything fetched a URL from this server, which was {@code OpenApiSpecIT}.
 *
 * <h2>⚠ What this class deliberately does NOT do</h2>
 *
 * It does not fix that. Deciding which endpoints are public, which need an allowlisted account and
 * which need a verified service-auth JWT is <em>the application's authentication model</em>, not a
 * detail to settle while adding API documentation — and getting it wrong in the permissive direction
 * would open a server that is meant to be closed by default. So this preserves the existing behaviour
 * exactly and changes one thing: the documentation endpoints are reachable when the operator has
 * enabled them.
 *
 * <p>The real policy belongs with <b>CL-8</b> ({@code docs/design/15-open-questions.md}), which is
 * where the transport's identity leg lives. Note the shape of the eventual answer: this server does
 * not authenticate with HTTP Basic or a form login at all — {@code identity/ServiceAuthVerifier}
 * verifies an AT Protocol service-auth JWT against a DID document the server resolves itself. Boot's
 * default is not a weak version of that; it is an unrelated mechanism that happens to be refusing
 * everybody.
 *
 * <h2>Why the doc endpoints are open rather than authenticated</h2>
 *
 * They are <strong>off by default</strong> ({@code application.yml}), so reaching them at all requires
 * the operator to have turned them on for their own machine. A spec describing a closed API is not
 * itself a secret — it names paths and shapes, never data — and an operator who wants it behind
 * something can put their reverse proxy in front of it, which is where that policy belongs anyway.
 */
@Configuration(proxyBeanMethods = false)
class ApiDocsSecurityConfiguration {

    /** Paths springdoc serves. Both are inert unless the matching {@code springdoc.*} flag is on. */
    private static final String[] DOC_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests.requestMatchers(DOC_PATHS)
                        .permitAll()
                        // ⚠ Everything else stays exactly as Boot had it. This is NOT an endorsement
                        // of the current policy — see the class note; it is a refusal to change the
                        // authentication model as a side effect of adding documentation.
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> {})
                .build();
    }
}
