package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.EtchingDesignDTO;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.EtchingDesignMapper;
import com.dayan.food.mapper.FavoriteMapper;
import com.dayan.food.mapper.FoodMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_FLOW_TESTS", matches = "true")
class CollectionMysqlTests {
    @org.junit.jupiter.api.AfterEach void clearActor(){com.dayan.food.support.TestActors.clear();}

    @Test
    @SuppressWarnings("unchecked")
    void realMysqlFavoriteAndEtchingRoundTripInSessionTemporaryTables() throws Exception {
        Path local = Path.of("src/main/resources/application-local.yml");
        Map<String, Object> config = Files.exists(local) ? new Yaml().load(Files.readString(local)) : Map.of();
        Map<String, Object> spring = (Map<String, Object>) config.getOrDefault("spring", Map.of());
        Map<String, Object> ds = (Map<String, Object>) spring.getOrDefault("datasource", Map.of());
        String url = System.getenv().getOrDefault("DB_URL", String.valueOf(ds.getOrDefault("url",
                "jdbc:mysql://localhost:3306/dayan_food?useSSL=false&allowPublicKeyRetrieval=true&useAffectedRows=true")));
        assertTrue(url.startsWith("jdbc:mysql://localhost:") || url.startsWith("jdbc:mysql://127.0.0.1:"),
                "This opt-in test only allows a local MySQL server");
        String username = System.getenv().getOrDefault("DB_USERNAME", String.valueOf(ds.getOrDefault("username", "root")));
        String password = System.getenv("DB_PASSWORD");
        assertNotNull(password, "Set DB_PASSWORD for the opt-in local MySQL test");
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                // Temporary tables shadow the originals only within this connection.
                // No production rows are read, updated, deleted, or committed.
                for (String table : new String[]{"app_user", "food", "region", "food_favorite",
                        "user_etching_design", "user_achievement"}) {
                    statement.execute("CREATE TEMPORARY TABLE " + table + " LIKE " + table);
                }
                statement.executeUpdate("INSERT INTO app_user (id,username,password,display_name,role,active,subject_id) VALUES (1,'flow-test','unused','Test','USER',TRUE,'temporary-flow-subject'),(2,'other-test','unused','Other','USER',TRUE,'temporary-other-subject')");
                statement.executeUpdate("INSERT INTO region (id,name,province) VALUES (1,'Test','Test')");
                statement.executeUpdate("INSERT INTO food (id,name,region_id,summary,story,ingredients,created_by,review_status) VALUES (1,'Test dish',1,'Test','Test','Test','flow-test','APPROVED')");
            }
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(new SingleConnectionDataSource(connection, true));
            var configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
            try (var session = factory.getObject().openSession()) {
                var users = session.getMapper(AppUserMapper.class);
                com.dayan.food.support.TestActors.use(users.findById(1L));
                var favorites = new FavoriteServiceImpl(session.getMapper(FavoriteMapper.class),
                        session.getMapper(FoodMapper.class), users);
                assertFalse(favorites.status(1L, "flow-test").favorited());
                assertTrue(favorites.add(1L, "flow-test").favorited());
                assertTrue(favorites.add(1L, "flow-test").favorited());
                assertEquals(1, favorites.list("flow-test").size());
                com.dayan.food.support.TestActors.use(users.findById(2L));
                assertTrue(favorites.list("other-test").isEmpty());
                com.dayan.food.support.TestActors.use(users.findById(1L));
                assertFalse(favorites.remove(1L, "flow-test").favorited());
                assertTrue(favorites.list("flow-test").isEmpty());

                var etchings = new EtchingDesignServiceImpl(session.getMapper(EtchingDesignMapper.class), users,
                        session.getMapper(AchievementMapper.class), new ObjectMapper());
                var colors = new ArrayList<String>();
                for (int i=0; i<169; i++) colors.add(i==0 ? "#842d26" : "");
                var request = new EtchingDesignDTO("Test seal", colors);
                var saved = etchings.create("flow-test", request);
                assertNotNull(saved.id());
                assertEquals(colors, etchings.listMine("flow-test").getFirst().layerOne());
                assertEquals(saved.id(), etchings.update("flow-test", saved.id(), request).id());
                assertTrue(etchings.select("flow-test", saved.id()).selected());
                com.dayan.food.support.TestActors.use(users.findById(2L));
                assertThrows(IllegalArgumentException.class, () -> etchings.update("other-test", saved.id(), request));
                com.dayan.food.support.TestActors.use(users.findById(1L));
                etchings.delete("flow-test", saved.id());
                assertTrue(etchings.listMine("flow-test").isEmpty());
                session.rollback();
            }
            connection.rollback();
            com.dayan.food.support.TestActors.clear();
        }
    }
}
