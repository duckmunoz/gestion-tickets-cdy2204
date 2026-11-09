
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TicketService {

    private final EfsService efsService;

    private final AwsS3Service awsS3Service;

@Value("${tickets.s3.bucket}")
private String bucketTickets;

@Value("${tickets.s3.enabled:false}")
private boolean subirAS3;

    public TicketService(
        EfsService efsService,
        AwsS3Service awsS3Service) {

    this.efsService = efsService;
    this.awsS3Service = awsS3Service;
}

    public File generarTicket(
            String evento,
            String usuario,
            int cantidad) throws IOException {

        if (evento == null || evento.isBlank()
                || usuario == null || usuario.isBlank()
                || cantidad <= 0) {
            throw new IllegalArgumentException(
                "Los datos del ticket no son validos");
        }

        String id = UUID.randomUUID().toString();

        String contenido = """
                ===== TICKET DE RESERVA =====
                ID: %s
                Evento: %s
                Usuario: %s
                Cantidad: %d
                Fecha: %s
                Estado: CONFIRMADO
                =============================
                """.formatted(
                    id,
                    evento,
                    usuario,
                    cantidad,
                    LocalDateTime.now()
                );

        String nombreArchivo = "ticket-" + id + ".txt";

        File archivo = efsService.guardarTicket(
    nombreArchivo,
    contenido.getBytes(StandardCharsets.UTF_8)
);

if (subirAS3) {
    String key = "tickets/" + nombreArchivo;

    awsS3Service.subirTicket(
        bucketTickets,
        key,
        archivo
    );
}

return archivo;
    }

    public File obtenerTicket(String nombreArchivo)
        throws IOException {

    if (nombreArchivo == null ||
            !nombreArchivo.matches(
                "ticket-[0-9a-fA-F-]{36}\\.txt")) {
        throw new IllegalArgumentException(
            "Nombre de ticket no valido");
    }

    File archivo = efsService.obtenerTicket(nombreArchivo);

    if (!archivo.exists() || !archivo.isFile()) {
        throw new java.io.FileNotFoundException(
            "El ticket solicitado no existe");
    }

    return archivo;
}
// Modifica los datos de un ticket existente
public File modificarTicket(
        String nombreArchivo,
        String evento,
        String usuario,
        int cantidad) throws IOException {

    if (evento == null || evento.isBlank()
            || usuario == null || usuario.isBlank()
            || cantidad <= 0) {
        throw new IllegalArgumentException(
            "Los datos del ticket no son validos");
    }

    File archivo = obtenerTicket(nombreArchivo);

    if (!archivo.exists()) {
        throw new FileNotFoundException("El ticket no existe");
    }

    String contenidoAnterior = Files.readString(
        archivo.toPath(), StandardCharsets.UTF_8);

    String id = nombreArchivo
        .replace("ticket-", "")
        .replace(".txt", "");

    String contenido = """
            ===== TICKET DE RESERVA =====
            ID: %s
            Evento: %s
            Usuario: %s
            Cantidad: %d
            Fecha de modificacion: %s
            Estado: CONFIRMADO
            =============================
            """.formatted(
                id,
                evento,
                usuario,
                cantidad,
                LocalDateTime.now()
            );

    return efsService.modificarTicket(
        nombreArchivo,
        contenido.getBytes(StandardCharsets.UTF_8)
    );
}

// Elimina un ticket
public boolean eliminarTicket(String nombreArchivo)
        throws IOException {

    // Validamos el nombre reutilizando el método existente
    obtenerTicket(nombreArchivo);

    return efsService.eliminarTicket(nombreArchivo);
}
// Genera estadísticas de reservas por evento
public List<Map<String, Object>> obtenerEstadisticas()
        throws IOException {

    File directorio = efsService.obtenerDirectorio();

    if (!directorio.exists()) {
        return List.of();
    }

    Map<String, int[]> estadisticas = new HashMap<>();

    try (Stream<java.nio.file.Path> archivos =
            Files.list(directorio.toPath())) {

        for (java.nio.file.Path ruta :
                archivos.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName()
                            .toString().matches(
                                "ticket-[0-9a-fA-F-]{36}\\.txt"))
                        .toList()) {

            List<String> lineas = Files.readAllLines(
                ruta, StandardCharsets.UTF_8);

            String evento = null;
            int cantidad = 0;

            for (String linea : lineas) {

                if (linea.startsWith("Evento: ")) {
                    evento = linea.substring(8).trim();
                }

                if (linea.startsWith("Cantidad: ")) {
                    cantidad = Integer.parseInt(
                        linea.substring(10).trim());
                }
            }

            if (evento != null) {
                int[] datos = estadisticas.computeIfAbsent(
                    evento, k -> new int[2]);

                datos[0]++;          // Reservas
                datos[1] += cantidad; // Tickets vendidos
            }
        }
    }

    return estadisticas.entrySet().stream()
        .map(entry -> {
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("evento", entry.getKey());
            resultado.put("reservas", entry.getValue()[0]);
            resultado.put("ticketsVendidos", entry.getValue()[1]);
            return resultado;
        })
        .toList();
}
}
