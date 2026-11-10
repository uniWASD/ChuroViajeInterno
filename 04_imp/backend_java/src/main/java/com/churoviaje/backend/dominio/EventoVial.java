package com.churoviaje.backend.dominio;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "evento_vial")
public class EventoVial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEventoVial tipo;

    // Cambiamos "Ubicacion" por "Point" de PostGIS para que la BD lo entienda como geometría espacial
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point ubicacion;

    @Column(name = "hora_confirmacion", nullable = false)
    private OffsetDateTime horaConfirmacion;

    @Column(name = "hora_ocurrencia", nullable = false)
    private OffsetDateTime horaOcurrencia;

    @Column(nullable = false)
    private String estado = "ACTIVO";

    @Column(nullable = false)
    private Integer votos = 0;

    @Column(name = "conductor_id", nullable = false)
    private Long conductorId;

    // Constructor vacío obligatorio para Spring Data JPA
    protected EventoVial() {}

    // Constructor con Reglas de Negocio (RN-01.4 y RN-01.10)
    public EventoVial(TipoEventoVial tipo, OffsetDateTime horaConfirmacion,
                      Integer minutosElegidos, Point ubicacion, Long conductorId) {
        
        // Validamos que el tipo no sea nulo y aplicamos RN-01.3
        this.tipo = Objects.requireNonNull(tipo, "El tipo de evento es obligatorio");
        if (!this.tipo.isReportable()) {
            throw new IllegalArgumentException("El tipo de evento " + tipo.name() + " no es reportable manualmente.");
        }

        this.horaConfirmacion = Objects.requireNonNull(horaConfirmacion, "La hora de confirmacion es obligatoria");
        
        // RN-01.4: Calcula el momento restando minutos. Sin minutos, usa 5.
        int minutosRestar = (minutosElegidos != null) ? minutosElegidos : 5;
        this.horaOcurrencia = this.horaConfirmacion.minusMinutes(minutosRestar);
        
        // RN-01.4: Nunca queda en el futuro
        if (this.horaOcurrencia.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("El evento no puede registrarse en el futuro.");
        }

        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicacion es obligatoria");
        this.conductorId = Objects.requireNonNull(conductorId, "El conductor es obligatorio");
        
        // RN-01.10: Nace Activo y sin votos
        this.estado = "ACTIVO";
        this.votos = 0;
    }

    // --- GETTERS ---
    public Long getId() { return id; }
    public TipoEventoVial getTipo() { return tipo; }
    public Point getUbicacion() { return ubicacion; }
    public OffsetDateTime getHoraConfirmacion() { return horaConfirmacion; }
    public OffsetDateTime getHoraOcurrencia() { return horaOcurrencia; }
    public String getEstado() { return estado; }
    public Integer getVotos() { return votos; }
    public Long getConductorId() { return conductorId; }
}