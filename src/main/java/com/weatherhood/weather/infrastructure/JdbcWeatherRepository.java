package com.weatherhood.weather.infrastructure;

import com.weatherhood.weather.domain.*;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcWeatherRepository implements WeatherRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JdbcWeatherRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override public List<WeatherBatch> find(Grid grid) {
        return jdbc.query("SELECT payload FROM weather_batch WHERE grid_x=? AND grid_y=?",
                (rs, row) -> json.readValue(rs.getString(1), WeatherBatch.class), grid.x(), grid.y());
    }

    @Override public Optional<WeatherBatch> find(Grid grid, WeatherSource source, ZonedDateTime publication) {
        return jdbc.query("SELECT payload FROM weather_batch WHERE grid_x=? AND grid_y=? AND source=? AND published_at=?",
                (rs, row) -> json.readValue(rs.getString(1), WeatherBatch.class),
                grid.x(), grid.y(), source.name(), publication.toOffsetDateTime().toString()).stream().findFirst();
    }

    @Transactional
    @Override public void save(WeatherBatch batch) {
        if (batch.samples().isEmpty()) throw new IllegalArgumentException("빈 수집 자료는 저장하지 않습니다.");
        OffsetDateTime latest = batch.samples().stream().map(WeatherBatch.Sample::time)
                .max(ZonedDateTime::compareTo).orElseThrow().toOffsetDateTime();
        jdbc.update("MERGE INTO weather_batch (grid_x,grid_y,source,published_at,fetched_at,latest_target,payload) "
                        + "KEY(grid_x,grid_y,source,published_at) VALUES (?,?,?,?,?,?,?)",
                batch.grid().x(), batch.grid().y(), batch.source().name(),
                batch.publishedAt().toOffsetDateTime().toString(), batch.fetchedAt().toOffsetDateTime(),
                latest, json.writeValueAsString(batch));
    }

    @Override public void touch(Grid grid, ZonedDateTime now) {
        jdbc.update("MERGE INTO weather_location (grid_x,grid_y,requested_at) KEY(grid_x,grid_y) VALUES (?,?,?)",
                grid.x(), grid.y(), now.toOffsetDateTime());
    }

    @Override public List<Grid> activeGrids(ZonedDateTime since) {
        return jdbc.query("SELECT grid_x,grid_y FROM weather_location WHERE requested_at>=?",
                (rs, row) -> new Grid(rs.getInt(1), rs.getInt(2)), since.toOffsetDateTime());
    }

    @Transactional
    @Override public void prune(ZonedDateTime before) {
        // Keep both the minimum collection history and every still-relevant forecast.
        jdbc.update("DELETE FROM weather_batch WHERE fetched_at<? AND latest_target<?",
                before.toOffsetDateTime(), before.toOffsetDateTime());
        jdbc.update("DELETE FROM weather_location WHERE requested_at<?", before.toOffsetDateTime());
    }
}
