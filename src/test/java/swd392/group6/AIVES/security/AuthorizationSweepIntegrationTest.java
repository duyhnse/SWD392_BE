package swd392.group6.AIVES.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIV-29 authorization sweep (15 §6): EVERY endpoint of node 1, discovered from the request mappings, is called with no
 * token and with each role it does not allow. The method body must never run for them: the answer has to be 401 (no
 * token) or 403 (wrong role) — 400 / 415 only for a body rejected before authorization. A 2xx, 404, 409, 422 or 5xx
 * would mean business code ran, i.e. a hole. Object-level rules (another lecturer's course, another student's attempt)
 * are tested per module (AC-C1, AC-G8 …).
 */
@IntegrationTest
class AuthorizationSweepIntegrationTest {

    /** Reachable without a JWT on purpose; each has its own guard (credentials, signed link, HMAC, public avatar). */
    static final List<String> PUBLIC = List.of("/api/v1/auth/", "/api/v1/avatars/", "/internal/ai-node/");
    private static final Pattern ROLE = Pattern.compile("'(ADMIN|LECTURER|STUDENT)'");
    private static final Set<Integer> NOT_RUN = Set.of(400, 401, 403, 415);

    @Autowired private MockMvc mockMvc;
    @Autowired private TestUsers users;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    /** @param parts names of the multipart parts the endpoint reads (file uploads) */
    record Endpoint(HttpMethod method, String path, Set<Role> roles, boolean selfAccess, String handler, List<String> parts) {
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            HandlerMethod h = e.getValue();
            PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), PreAuthorize.class);
            if (rule == null) {
                rule = AnnotatedElementUtils.findMergedAnnotation(h.getBeanType(), PreAuthorize.class);
            }
            Set<Role> roles = new TreeSet<>();
            if (rule != null) {
                Matcher m = ROLE.matcher(rule.value());
                while (m.find()) {
                    roles.add(Role.valueOf(m.group(1)));
                }
            }
            boolean self = rule != null && rule.value().contains("principal");
            Set<RequestMethod> verbs = e.getKey().getMethodsCondition().getMethods();
            for (String pattern : e.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/") && !pattern.startsWith("/internal/")) {
                    continue;
                }
                for (RequestMethod verb : verbs.isEmpty() ? Set.of(RequestMethod.GET) : verbs) {
                    List<String> parts = java.util.Arrays.stream(h.getMethodParameters())
                            .map(p -> p.getParameterAnnotation(org.springframework.web.bind.annotation.RequestPart.class))
                            .filter(java.util.Objects::nonNull).map(a -> a.value().isEmpty() ? a.name() : a.value()).toList();
                    out.add(new Endpoint(HttpMethod.valueOf(verb.name()), pattern, roles, self,
                            h.getBeanType().getSimpleName() + "." + h.getMethod().getName(), parts));
                }
            }
        }
        return out;
    }

    private static String concrete(String pattern) {
        return pattern.replace("{kind}", "CAMERA").replace("{chunkIndex}", "0")
                .replaceAll("\\{[^}/]+}", UUID.randomUUID().toString());
    }

    private int call(Endpoint e, String token) throws Exception {
        org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> req;
        if (!e.parts().isEmpty()) { // a real upload, so the role check is what answers
            var multipart = MockMvcRequestBuilders.multipart(e.method(), java.net.URI.create(concrete(e.path())));
            e.parts().forEach(p -> multipart.file(new org.springframework.mock.web.MockMultipartFile(p, "x.webm", "audio/webm",
                    new byte[] {1, 2, 3})));
            req = multipart;
        } else {
            req = MockMvcRequestBuilders.request(e.method(), concrete(e.path()));
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (e.parts().isEmpty() && (e.method() == HttpMethod.POST || e.method() == HttpMethod.PUT || e.method() == HttpMethod.PATCH)) {
            req.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    private boolean isPublic(Endpoint e) {
        return PUBLIC.stream().anyMatch(p -> e.path().startsWith(p));
    }

    @Test
    void everyEndpointRefusesCallersItDoesNotAllow() throws Exception {
        Map<Role, String> tokens = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            User u = users.create(role);
            tokens.put(role, TestUsers.login(mockMvc, u.getUsername(), TestUsers.PASSWORD));
        }
        List<Endpoint> all = endpoints();
        assertThat(all).hasSizeGreaterThan(100);
        List<String> holes = new ArrayList<>();
        List<String> anyRole = new ArrayList<>();
        for (Endpoint e : all) {
            if (!isPublic(e)) {
                int anonymous = call(e, null);
                if (anonymous != 401) {
                    holes.add(e.method() + " " + e.path() + " without a token → " + anonymous + " (" + e.handler() + ")");
                }
            }
            if (e.roles().isEmpty()) {
                if (!isPublic(e)) {
                    anyRole.add(e.method() + " " + e.path());
                }
                continue;
            }
            for (Role role : Role.values()) {
                if (e.roles().contains(role) || e.selfAccess()) {
                    continue;
                }
                int status = call(e, tokens.get(role));
                boolean strict = e.method() == HttpMethod.GET || e.method() == HttpMethod.DELETE || !e.parts().isEmpty();
                if (!NOT_RUN.contains(status) || (strict && status != 403)) { // no body to reject first: must be 403
                    holes.add(e.method() + " " + e.path() + " as " + role + " → " + status + " (" + e.handler() + ")");
                }
            }
        }
        System.out.printf("Authorization sweep: %d endpoints, %d open to any signed-in role (object rules in the services)%n",
                all.size(), anyRole.size());
        anyRole.stream().sorted().forEach(a -> System.out.println("  any role: " + a));
        assertThat(holes).as("endpoints that ran business code for a caller they do not allow").isEmpty();
    }

    @Test
    void anUploadSentAsJsonIsAClientError() throws Exception {
        User student = users.create(Role.STUDENT);
        String token = TestUsers.login(mockMvc, student.getUsername(), TestUsers.PASSWORD);
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/attempts/{id}/live-frame/CAMERA", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("MULTIPART_REQUIRED"));
    }

    @Test
    void internalAiNodeEndpointsNeedTheirSignature() throws Exception {
        for (Endpoint e : endpoints()) {
            if (e.path().startsWith("/internal/ai-node/")) {
                int status = call(e, null);
                assertThat(status).as(e.method() + " " + e.path()).isIn(400, 401, 403);
            }
        }
    }
}
