package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.catalogo.TiempoReporte;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Un evento vial: un accidente, control policial, bloqueo u otro que reporta un conductor (CU-01), o una congestion
 * que detecta el sistema (CU-04). Sabe cuando ocurrio, hasta cuando esta vigente y cuando choca con otro reporte.
 */
public class EventoVial {

    /** RN-01.4: si el conductor no elige, el evento ocurrio hace 5 minutos. */
    public static final int MINUTOS_POR_DEFECTO = 5;
    /** RN-01.8 y P-03: dos reportes del mismo tipo a menos de 100 m y dentro de 10 minutos son el mismo. */
    public static final double DISTANCIA_DUPLICADO_METROS = 100;
    public static final Duration VENTANA_DUPLICADO = Duration.ofMinutes(10);
    /** RN-02.13 y P-01: con 3 votos «falso», un evento Activo pasa a Retirado. */
    public static final int VOTOS_PARA_RETIRAR = 3;
    /** RN-02.14 y P-11: con 3 votos «real», pasa a Confirmado y ya no se retira por votos. */
    public static final int VOTOS_PARA_CONFIRMAR = 3;
    /** El código de la congestión en el catálogo, y sus dos niveles (P-02, P-12). */
    public static final String TIPO_CONGESTION = "CONGESTION";
    public static final String NIVEL_CONGESTIONADO = "CONGESTIONADO";
    public static final String NIVEL_MUY_CONGESTIONADO = "MUY_CONGESTIONADO";

    private final Long id;
    private final TipoEvento tipo;
    private final Ubicacion ubicacion;
    private final Instant ocurrioEn;
    private final Instant registradoEn;
    private final Long reportanteId;
    private final OrigenEvento origen;
    private final String nivel;
    private final EstadoEvento estado;
    private final int votosReal;
    private final int votosFalso;
    private final boolean simulado;
    /** Solo en la congestión: el tramo donde la midió el motor, para que afecte solo ese sentido (D-18). */
    private final String tramoId;

    private EventoVial(Long id, TipoEvento tipo, Ubicacion ubicacion, Instant ocurrioEn, Instant registradoEn,
            Long reportanteId, OrigenEvento origen, String nivel, EstadoEvento estado, int votosReal,
            int votosFalso, boolean simulado, String tramoId) {
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.ubicacion = Objects.requireNonNull(ubicacion, "ubicacion");
        this.ocurrioEn = Objects.requireNonNull(ocurrioEn, "ocurrioEn");
        this.registradoEn = Objects.requireNonNull(registradoEn, "registradoEn");
        this.reportanteId = reportanteId;
        this.origen = Objects.requireNonNull(origen, "origen");
        this.nivel = nivel;
        this.estado = Objects.requireNonNull(estado, "estado");
        this.votosReal = votosReal;
        this.votosFalso = votosFalso;
        this.simulado = simulado;
        this.tramoId = tramoId;
    }

    /**
     * Crea el evento que reporta un conductor (RN-01.2 a RN-01.4, RN-01.7, RN-01.10, RN-01.17). La ubicacion ya
     * viene validada contra la mancha urbana, y el duplicado y el limite los comprueba el servicio con la base.
     *
     * @param minutosElegidos hace cuantos minutos ocurrio; {@code null} vale {@link #MINUTOS_POR_DEFECTO}
     */
    public static EventoVial reportar(TipoEvento tipo, Ubicacion ubicacion, OffsetDateTime horaConfirmacion,
            Integer minutosElegidos, long reportanteId, boolean simulado, Instant ahora) {
        Objects.requireNonNull(horaConfirmacion, "horaConfirmacion");
        if (!tipo.reportable()) {
            throw tipoNoReportable();
        }
        int minutos = minutosElegidos == null ? MINUTOS_POR_DEFECTO : minutosElegidos;
        if (TiempoReporte.OPCIONES.stream().noneMatch(opcion -> opcion.minutos() == minutos)) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA",
                    "El tiempo elegido no es una de las opciones del reporte.");
        }
        Instant ocurrio = horaConfirmacion.toInstant().minus(Duration.ofMinutes(minutos));
        if (ocurrio.isAfter(ahora)) {
            ocurrio = ahora; // RN-01.4: un reloj del telefono adelantado no deja el evento en el futuro
        }
        EventoVial evento = new EventoVial(null, tipo, ubicacion, ocurrio, ahora, reportanteId,
                OrigenEvento.CONDUCTOR, null, EstadoEvento.ACTIVO, 0, 0, simulado, null);
        if (!evento.estaVigente(ahora)) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "EVENTO_VENCIDO",
                    "Este evento ya no está vigente: pasaron más de " + tipo.vigenciaMinutos()
                            + " minutos desde que ocurrió.");
        }
        return evento;
    }

    /**
     * Crea la congestión que detectó el motor con la telemetría (RN-04.10, D-04): tipo Congestión, origen
     * Telemetría, sin reportante (RN-04.12), con el nivel que informó el motor y el tramo donde la midió. Ocurre
     * ahora, así que dura la vigencia de su tipo desde este momento.
     */
    public static EventoVial detectarCongestion(TipoEvento congestion, Ubicacion punto, String tramoId, String nivel,
            boolean simulado, Instant ahora) {
        Objects.requireNonNull(tramoId, "tramoId");
        if (!TIPO_CONGESTION.equals(congestion.codigo())) {
            throw new IllegalArgumentException("La telemetría solo registra congestión, no " + congestion.codigo());
        }
        return new EventoVial(null, congestion, punto, ahora, ahora, null, OrigenEvento.TELEMETRIA,
                nivelValido(nivel), EstadoEvento.ACTIVO, 0, 0, simulado, tramoId);
    }

    /**
     * RN-04.11: la misma congestión sigue en su tramo. En vez de crear otra, se mueve su momento a {@code ahora}, así
     * dura mientras siga la lentitud, y toma el nivel que informó el motor si cambió.
     */
    public EventoVial extenderCongestion(String nuevoNivel, Instant ahora) {
        if (origen != OrigenEvento.TELEMETRIA) {
            throw new IllegalStateException("Solo se extiende una congestión de telemetría");
        }
        return new EventoVial(id, tipo, ubicacion, ahora.isAfter(ocurrioEn) ? ahora : ocurrioEn, registradoEn,
                reportanteId, origen, nivelValido(nuevoNivel), estado, votosReal, votosFalso, simulado, tramoId);
    }

    private static String nivelValido(String nivel) {
        if (!NIVEL_CONGESTIONADO.equals(nivel) && !NIVEL_MUY_CONGESTIONADO.equals(nivel)) {
            throw new IllegalArgumentException("Nivel de congestión desconocido: " + nivel);
        }
        return nivel;
    }

    /** Reconstruye un evento guardado. */
    public static EventoVial desdeRegistro(long id, TipoEvento tipo, Ubicacion ubicacion, Instant ocurrioEn,
            Instant registradoEn, Long reportanteId, OrigenEvento origen, String nivel, EstadoEvento estado,
            int votosReal, int votosFalso, boolean simulado) {
        return desdeRegistro(id, tipo, ubicacion, ocurrioEn, registradoEn, reportanteId, origen, nivel, estado,
                votosReal, votosFalso, simulado, null);
    }

    /** Reconstruye un evento guardado, con el tramo de la congestión. */
    public static EventoVial desdeRegistro(long id, TipoEvento tipo, Ubicacion ubicacion, Instant ocurrioEn,
            Instant registradoEn, Long reportanteId, OrigenEvento origen, String nivel, EstadoEvento estado,
            int votosReal, int votosFalso, boolean simulado, String tramoId) {
        return new EventoVial(id, tipo, ubicacion, ocurrioEn, registradoEn, reportanteId, origen, nivel, estado,
                votosReal, votosFalso, simulado, tramoId);
    }

    public static ExcepcionRechazo tipoNoReportable() {
        return new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "TIPO_NO_REPORTABLE",
                "Ese tipo de evento no se puede reportar. Elige accidente, control policial, bloqueo u otro.");
    }

    public static ExcepcionRechazo duplicado() {
        return new ExcepcionRechazo(HttpStatus.CONFLICT, "REPORTE_DUPLICADO",
                "Ya reportaste este evento hace poco.");
    }

    /** RN-02.3: hasta cuando cuenta el evento, desde que ocurrio y segun su tipo. */
    public Instant venceEn() {
        return ocurrioEn.plus(Duration.ofMinutes(tipo.vigenciaMinutos()));
    }

    /** RN-02.2 a RN-02.4: se muestra mientras este Activo o Confirmado y no haya pasado su vigencia. */
    public boolean estaVigente(Instant ahora) {
        return estado.seMuestra() && ahora.isBefore(venceEn());
    }

    /**
     * Cuenta el voto de un conductor y devuelve el evento con el estado que le corresponde (RN-02.10, RN-02.12 a
     * RN-02.14). El voto repetido (RN-02.11) lo comprueba el servicio con la base.
     */
    public EventoVial registrarVoto(ValorVoto valor, long votante, Instant ahora) {
        if (origen != OrigenEvento.CONDUCTOR) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "VOTO_NO_PERMITIDO",
                    "La congestión que detecta el sistema no se vota."); // D-20
        }
        if (!estaVigente(ahora)) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "EVENTO_NO_VIGENTE",
                    "Este evento ya no está activo.");
        }
        if (reportanteId != null && reportanteId == votante) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "VOTO_PROPIO",
                    "No puedes votar un evento que reportaste."); // P-17
        }
        int real = votosReal + (valor == ValorVoto.REAL ? 1 : 0);
        int falso = votosFalso + (valor == ValorVoto.FALSO ? 1 : 0);
        EstadoEvento nuevoEstado = estado;
        if (estado == EstadoEvento.ACTIVO && real >= VOTOS_PARA_CONFIRMAR) {
            nuevoEstado = EstadoEvento.CONFIRMADO;
        } else if (estado == EstadoEvento.ACTIVO && falso >= VOTOS_PARA_RETIRAR) {
            nuevoEstado = EstadoEvento.RETIRADO;
        }
        return new EventoVial(id, tipo, ubicacion, ocurrioEn, registradoEn, reportanteId, origen, nivel, nuevoEstado,
                real, falso, simulado, tramoId);
    }

    /** Devuelve el mismo evento con el identificador que le dio la base. */
    public EventoVial conId(long nuevoId) {
        return new EventoVial(nuevoId, tipo, ubicacion, ocurrioEn, registradoEn, reportanteId, origen, nivel, estado,
                votosReal, votosFalso, simulado, tramoId);
    }

    public Long getId() { return id; }
    public TipoEvento getTipo() { return tipo; }
    public Ubicacion getUbicacion() { return ubicacion; }
    public Instant getOcurrioEn() { return ocurrioEn; }
    public Instant getRegistradoEn() { return registradoEn; }
    public Long getReportanteId() { return reportanteId; }
    public OrigenEvento getOrigen() { return origen; }
    public String getNivel() { return nivel; }
    public EstadoEvento getEstado() { return estado; }
    public int getVotosReal() { return votosReal; }
    public int getVotosFalso() { return votosFalso; }
    public boolean isSimulado() { return simulado; }
    public String getTramoId() { return tramoId; }
}
