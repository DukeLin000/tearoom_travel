package com.tripmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class PhaseOneIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine")
        .withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",DB::getJdbcUrl);
        registry.add("spring.datasource.username",DB::getUsername);
        registry.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    record Session(MockHttpSession cookie,UUID id) {}
    Session alice,bob;
    @BeforeEach void prepare() throws Exception {
        jdbc.execute("TRUNCATE trip_change_log,itinerary_item,trip_day,trip_member,trip,app_user RESTART IDENTITY CASCADE");
        alice=login("alice"); bob=login("bob");
    }
    private Session login(String subject) throws Exception {
        var result=mvc.perform(post("/api/v1/auth/dev-login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"subject\":\""+subject+"\",\"displayName\":\""+subject+"\"}"))
            .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession)result.getRequest().getSession(false),UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText()));
    }
    private ObjectNode tripInput() {
        return mapper.createObjectNode().put("title","沖繩好友旅行").put("destination","沖繩")
            .put("startDate","2027-03-12").put("endDate","2027-03-14").put("timezone","Asia/Tokyo")
            .put("currency","TWD").put("budgetLimit","100.00");
    }
    private JsonNode createTrip(Session user) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/trips").session(user.cookie()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(tripInput().toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode getJson(String path,Session user) throws Exception {
        return mapper.readTree(mvc.perform(get(path).session(user.cookie())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String day(String trip) throws Exception { return getJson("/api/v1/trips/"+trip+"/schedule",alice).get("days").get(0).get("id").asText(); }
    private ObjectNode itemInput(String day,String amount) {
        return mapper.createObjectNode().put("dayId",day).put("title","午餐").put("category","FOOD")
            .put("estimatedAmount",amount).put("note","");
    }
    private JsonNode createItem(String trip,String day,String amount) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/trips/"+trip+"/items").session(alice.cookie()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(itemInput(day,amount).toString())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(0)).andReturn().getResponse().getContentAsString());
    }
    @Test void migrationAndTripOwnershipAndPagination() throws Exception {
        var trip=createTrip(alice); String id=trip.get("id").asText();
        assertThat(trip.get("ownerUserId").asText()).isEqualTo(alice.id().toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_day",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_member WHERE trip_id=? AND user_id=?",Integer.class,UUID.fromString(id),alice.id())).isEqualTo(1);
        assertThat(getJson("/api/v1/trips",alice).get("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/v1/trips",bob).get("items")).isEmpty();
        assertThat(getJson("/api/v1/trips/"+id+"/members",alice)).hasSize(1);
        mvc.perform(get("/api/v1/trips?size=101").session(alice.cookie())).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(2);
    }
    @Test void foundationTablesMetadataAndInviteConstraintsAreMigrated() throws Exception {
        var tables = List.of("app_user", "trip", "trip_member", "trip_day", "itinerary_item", "trip_invite", "trip_change_log");
        for (String table : tables) {
            var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?", String.class, table);
            assertThat(columns).contains("created_at", "updated_at", "version");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='public' AND table_name=? AND constraint_type='PRIMARY KEY'", Integer.class, table)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='trip_change_log' AND column_name='id'", String.class)).isEqualTo("uuid");
        String trip = createTrip(alice).get("id").asText();
        String insert = "INSERT INTO trip_invite(trip_id,created_by,token_hash,expires_at,max_uses,use_count) VALUES (?,?,?,now()+interval '1 day',?,?)";
        byte[] digest = new byte[32]; new java.security.SecureRandom().nextBytes(digest);
        jdbc.update(insert, UUID.fromString(trip), alice.id(), digest, 2, 0);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.fromString(trip), alice.id(), digest, 2, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.fromString(trip), alice.id(), new byte[31], 2, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.fromString(trip), alice.id(), new byte[32], 1, 2)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.fromString(trip), bob.id(), new byte[32], 1, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE trip_invite SET expires_at=created_at-interval '1 second'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_invite", Integer.class)).isEqualTo(1);
    }
    @Test void v1DatabaseUpgradesWithoutLosingUserTripOrAuditData() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").load().migrate();
            UUID user = UUID.randomUUID(), trip = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
                connection.setSchema(schema);
                connection.setAutoCommit(false);
                var legacy = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                legacy.update("INSERT INTO app_user(id,oidc_issuer,oidc_subject,display_name) VALUES (?,'legacy','subject','Existing user')", user);
                legacy.update("INSERT INTO trip(id,title,destination,start_date,end_date,timezone,currency,budget_limit,owner_user_id) VALUES (?,'Existing trip','Taipei','2027-01-01','2027-01-01','Asia/Taipei','TWD',123.45,?)", trip, user);
                legacy.update("INSERT INTO trip_member(trip_id,user_id) VALUES (?,?)", trip, user);
                legacy.update("INSERT INTO trip_change_log(trip_id,actor_id,action,entity_id,after_data) VALUES (?,?,'TRIP_CREATED',?,CAST(? AS jsonb))", trip, user, trip, "{\"title\":\"Existing trip\"}");
                connection.commit();
            }
            Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT display_name FROM " + schema + ".app_user WHERE id=?", String.class, user)).isEqualTo("Existing user");
            assertThat(jdbc.queryForObject("SELECT budget_limit::text FROM " + schema + ".trip WHERE id=?", String.class, trip)).isEqualTo("123.45");
            assertThat(jdbc.queryForObject("SELECT after_data->>'title' FROM " + schema + ".trip_change_log", String.class)).isEqualTo("Existing trip");
            assertThat(jdbc.queryForObject("SELECT id FROM " + schema + ".trip_change_log", UUID.class)).isNotNull();
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
    @Test void jpaIdentityUpsertPreservesIdentityAndIncrementsVersion() throws Exception {
        var again = login("alice");
        assertThat(again.id()).isEqualTo(alice.id());
        assertThat(jdbc.queryForObject("SELECT version FROM app_user WHERE id=?", Long.class, alice.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE oidc_subject='alice'", Integer.class)).isEqualTo(1);
    }
    @Test void sessionAuthenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/v1/trips")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/trips").session(alice.cookie()).contentType(MediaType.APPLICATION_JSON).content(tripInput().toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(get("/api/v1/auth/me").session(alice.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(alice.id().toString()));
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/v1/auth/logout").session(alice.cookie()).with(csrf())).andExpect(status().isNoContent());
        assertThat(alice.cookie().isInvalid()).isTrue();
    }
    @Test void invalidTripFieldsNeverInsertRows() throws Exception {
        for (ObjectNode input:List.of(tripInput().put("budgetLimit","-1"),tripInput().put("endDate","2027-03-11"),
            tripInput().put("endDate","2027-04-01"),tripInput().put("timezone","Mars/Olympus"),tripInput().put("currency","ZZZ"),
            tripInput().put("title"," "),tripInput().put("budgetLimit",10.2),tripInput().put("unknown","field"))) {
            mvc.perform(post("/api/v1/trips").session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip",Integer.class)).isZero();
    }
    @Test void invalidAmountsTimesLinksAndLengthsHaveNoPartialWrites() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        for (ObjectNode input:List.of(itemInput(day,"-0.01"),itemInput(day,"1.001"),itemInput(day,"1000000000000.00"),
            itemInput(day,"0.00").put("estimatedAmount",0.1),itemInput(day,"1.00").put("localStartTime","12:00").put("localEndTime","11:00"),
            itemInput(day,"1.00").put("mapUrl","javascript:alert(1)"),itemInput(day,"1.00").put("title","x".repeat(161)),
            itemInput(day,"1.00").put("category","INVALID"))) {
            mvc.perform(post("/api/v1/trips/"+trip+"/items").session(alice.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_item",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_change_log",Integer.class)).isZero();
    }
    @Test void exactDecimalBudgetUsesMembersAndLiveItems() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        var item=createItem(trip,day,"0.10"); createItem(trip,day,"0.20");
        jdbc.update("INSERT INTO trip_member(trip_id,user_id) VALUES (?,?)",UUID.fromString(trip),bob.id());
        var budget=getJson("/api/v1/trips/"+trip+"/budget",bob);
        assertThat(budget.get("plannedTotal").asText()).isEqualTo("0.30");
        assertThat(budget.get("estimatedPerPerson").asText()).isEqualTo("0.15");
        assertThat(budget.get("remaining").asText()).isEqualTo("99.70");
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item.get("id").asText()).session(bob.cookie()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"estimatedAmount\":\"2500.00\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estimatedAmount").value("2500.00")).andExpect(jsonPath("$.version").value(1));
        budget=getJson("/api/v1/trips/"+trip+"/budget",alice);
        assertThat(budget.get("plannedTotal").asText()).isEqualTo("2500.20");
        assertThat(budget.get("remaining").asText()).isEqualTo("-2400.20");
        assertThat(budget.get("breakdown").get("FOOD").asText()).isEqualTo("2500.20");
    }
    @Test void simultaneousStaleWritesHaveExactlyOneWinner() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        String item=createItem(trip,day,"10.00").get("id").asText();
        var start=new java.util.concurrent.CountDownLatch(1);
        Callable<Integer> write=() -> { start.await(); return mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(alice.cookie()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"estimatedAmount\":\"20.01\"}"))
            .andReturn().getResponse().getStatus(); };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(write);var b=pool.submit(write);start.countDown();
            assertThat(List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(alice.cookie()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"title\":\"stale\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_change_log WHERE action='ITEM_UPDATED'",Integer.class)).isEqualTo(1);
    }
    @Test void deleteIsVersionProtectedCompactsOrderAndAudits() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        String first=createItem(trip,day,"10.00").get("id").asText(); var second=createItem(trip,day,"20.00");
        assertThat(second.get("sortOrder").asInt()).isEqualTo(1);
        mvc.perform(delete("/api/v1/trips/"+trip+"/items/"+first+"?expectedVersion=1").session(alice.cookie()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/trips/"+trip+"/items/"+first).session(alice.cookie()).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/trips/"+trip+"/items/"+first+"?expectedVersion=0").session(alice.cookie()).with(csrf())).andExpect(status().isNoContent());
        var schedule=getJson("/api/v1/trips/"+trip+"/schedule",alice).get("days").get(0);
        assertThat(schedule.get("items")).hasSize(1);
        assertThat(schedule.get("items").get(0).get("sortOrder").asInt()).isZero();
        assertThat(schedule.get("version").asLong()).isEqualTo(3);
        assertThat(getJson("/api/v1/trips/"+trip+"/budget",alice).get("plannedTotal").asText()).isEqualTo("20.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_change_log WHERE action='ITEM_DELETED' AND after_data->>'deletedAt' IS NOT NULL",Integer.class)).isEqualTo(1);
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+first).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":1,\"title\":\"deleted\"}")).andExpect(status().isNotFound());
    }
    @Test void outsidersAndCrossTripItemIdsAreRejected() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        String item=createItem(trip,day,"10.00").get("id").asText();
        for (String suffix:List.of("","/schedule","/budget","/members")) mvc.perform(get("/api/v1/trips/"+trip+suffix).session(bob.cookie())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/trips/"+trip+"/items").session(bob.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(itemInput(day,"1.00").toString())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(bob.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"title\":\"outsider\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/trips/"+trip+"/items/"+item+"?expectedVersion=0").session(bob.cookie()).with(csrf())).andExpect(status().isNotFound());
        String other=createTrip(alice).get("id").asText();
        mvc.perform(post("/api/v1/trips/"+other+"/items").session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(itemInput(day,"1.00").toString())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/trips/"+other+"/items/"+item).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"title\":\"wrong trip\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/trips/"+other+"/items/"+item+"?expectedVersion=0").session(alice.cookie()).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void partialPatchCanClearOptionalTimeButCannotChangeImmutableFields() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        String item=createItem(trip,day,"1.00").get("id").asText();
        for (String body:List.of("{\"title\":\"no version\"}","{\"expectedVersion\":-1,\"title\":\"x\"}",
            "{\"expectedVersion\":0.1,\"title\":\"x\"}","{\"expectedVersion\":0,\"dayId\":\""+day+"\"}",
            "{\"expectedVersion\":0,\"estimatedAmount\":1.2}","{\"expectedVersion\":0,\"title\":null}")) {
            mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":0,\"localStartTime\":\"10:00\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("午餐"));
        mvc.perform(patch("/api/v1/trips/"+trip+"/items/"+item).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":1,\"localStartTime\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.localStartTime").doesNotExist());
    }
    @Test void auditFailureRollsBackItemMutationAndDayVersion() throws Exception {
        String trip=createTrip(alice).get("id").asText(),day=day(trip);
        jdbc.execute("ALTER TABLE trip_change_log ADD CONSTRAINT fail_test_audit CHECK (action <> 'ITEM_CREATED')");
        try {
            mvc.perform(post("/api/v1/trips/"+trip+"/items").session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(itemInput(day,"1.00").toString())).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_item",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT version FROM trip_day WHERE id=?",Long.class,UUID.fromString(day))).isZero();
        } finally { jdbc.execute("ALTER TABLE trip_change_log DROP CONSTRAINT fail_test_audit"); }
    }
    @Test void unsupportedRoutesAndMediaTypesReturnClientErrors() throws Exception {
        String trip=createTrip(alice).get("id").asText();
        mvc.perform(get("/api/v1/trips/"+trip+"/unknown").session(alice.cookie())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/trips/"+trip).session(alice.cookie()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/trips").session(alice.cookie()).with(csrf()).contentType(MediaType.TEXT_PLAIN).content("bad input"))
            .andExpect(status().isUnsupportedMediaType());
    }
}
