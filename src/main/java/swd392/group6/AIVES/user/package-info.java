/**
 * User (FG7 + accounts): users, roles, sessions, sign-in (password, Google), courses and lecturer assignment,
 * notifications, system settings.
 * <p>
 * Spec: SWD_Docs/requirements. Public API = types in this package (…Api, events, shared types); other modules use
 * only these. Implementation, by layer: {@code web} (controllers), {@code service} (business logic),
 * {@code repository} (Spring Data), {@code domain} (entities, enums), {@code dto} (request / response records).
 */
@org.springframework.modulith.ApplicationModule
package swd392.group6.AIVES.user;
