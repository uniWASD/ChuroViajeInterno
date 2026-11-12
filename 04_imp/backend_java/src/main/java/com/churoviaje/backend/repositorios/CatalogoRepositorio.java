package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lee los catalogos que dejo la migracion V1 (T-089): tipos de evento y tipos de combustible. Solo lectura. */
@Repository
public class CatalogoRepositorio {

    private final JdbcTemplate jdbc;

    public CatalogoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TipoEvento> tiposDeEvento() {
        return jdbc.query(
                "select codigo, nombre, color, vigencia_minutos, reportable from tipo_evento",
                (fila, n) -> new TipoEvento(fila.getString("codigo"), fila.getString("nombre"),
                        fila.getString("color"), fila.getInt("vigencia_minutos"), fila.getBoolean("reportable")));
    }

    public Optional<TipoEvento> tipoDeEvento(String codigo) {
        return jdbc.query(
                "select codigo, nombre, color, vigencia_minutos, reportable from tipo_evento where codigo = ?",
                (fila, n) -> new TipoEvento(fila.getString("codigo"), fila.getString("nombre"),
                        fila.getString("color"), fila.getInt("vigencia_minutos"), fila.getBoolean("reportable")),
                codigo).stream().findFirst();
    }

    public List<TipoCombustible> tiposDeCombustible() {
        return jdbc.query(
                "select codigo, nombre from tipo_combustible",
                (fila, n) -> new TipoCombustible(fila.getString("codigo"), fila.getString("nombre")));
    }
}
