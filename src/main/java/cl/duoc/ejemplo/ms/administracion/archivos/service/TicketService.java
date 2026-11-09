
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final EfsService efsService;
    private final AwsS3Service awsS3Service;

    @Value("${tickets.s3.bucket}")
    private String bucketTickets;

    @Value("${tickets.s3.enabled:false}")
    private boolean subirAS3;

    public TicketService(EfsService efsService,
                         AwsS3Service awsS3Service) {
        this.efsService = efsService;
        this.awsS3Service = awsS3Service;
    }

    public File generarTicket(String evento, String usuario, int cantidad)
            throws IOException {

        validarDatos(evento, usuario, cantidad);

        String id = UUID.randomUUID().toString();
        String nombreArchivo = "ticket-" + id + ".txt";

        String contenido = """
                ===== TICKET DE RESERVA =====
                ID: %s
                Evento: %s
                Usuario: %s
                Cantidad: %d
                Fecha: %s
                Estado: CONFIRMADO
                =============================
                """.formatted(id, evento, usuario, cantidad,
                             LocalDateTime.now());

        File archivo = efsService.guardarTicket(
                nombreArchivo,
                contenido.getBytes(StandardCharsets.UTF_8)
        );

        if (subirAS3) {
            awsS3Service.subirTicket(
                    bucketTickets,
                    construirRutaS3(evento, usuario, nombreArchivo),
                    archivo
            );
        }

        return archivo;
    }

    public File obtenerTicket(String nombreArchivo)
            throws IOException {

        validarNombreArchivo(nombreArchivo);

        File archivo = efsService.obtenerTicket(nombreArchivo);

        if (!archivo.exists() || !archivo.isFile()) {
            throw new FileNotFoundException(
                    "El ticket solicitado no existe");
        }

        return archivo;
    }

    public File modificarTicket(String nombreArchivo,
                                String evento,
                                String usuario,
                                int cantidad) throws IOException {

        validarDatos(evento, usuario, cantidad);

        File archivoAnterior = obtenerTicket(nombreArchivo);

        String rutaAnterior = obtenerRutaS3DesdeArchivo(
                archivoAnterior, nombreArchivo);

        String rutaNueva = construirRutaS3(
                evento, usuario, nombreArchivo);

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
                """.formatted(id, evento, usuario, cantidad,
                             LocalDateTime.now());

        File archivoModificado = efsService.modificarTicket(
                nombreArchivo,
                contenido.getBytes(StandardCharsets.UTF_8)
        );

        if (subirAS3) {
            awsS3Service.subirTicket(
                    bucketTickets,
                    rutaNueva,
                    archivoModificado
            );

            if (!rutaAnterior.equals(rutaNueva)) {
                awsS3Service.deleteObject(
                        bucketTickets,
                        rutaAnterior
                );
            }
        }

        return archivoModificado;
    }

    public boolean eliminarTicket(String nombreArchivo)
            throws IOException {

        File archivo = obtenerTicket(nombreArchivo);

        if (subirAS3) {
            String rutaS3 = obtenerRutaS3DesdeArchivo(
                    archivo, nombreArchivo);

            awsS3Service.deleteObject(
                    bucketTickets,
                    rutaS3
            );
        }

        return efsService.eliminarTicket(nombreArchivo);
    }

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
                                    .toString()
                                    .matches("ticket-[0-9a-fA-F-]{36}\\.txt"))
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

                    datos[0]++;
                    datos[1] += cantidad;
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

    private String construirRutaS3(String evento,
                                   String usuario,
                                   String nombreArchivo) {

        return "tickets/"
                + normalizarSegmento(evento) + "/"
                + normalizarSegmento(usuario) + "/"
                + nombreArchivo;
    }

    private String normalizarSegmento(String valor) {

        String resultado = valor.trim()
                .replaceAll("[^a-zA-Z0-9_-]", "_");

        if (resultado.isBlank()) {
            throw new IllegalArgumentException(
                    "Evento o usuario no valido para S3");
        }

        return resultado;
    }

    private String obtenerRutaS3DesdeArchivo(
            File archivo, String nombreArchivo) throws IOException {

        List<String> lineas = Files.readAllLines(
                archivo.toPath(), StandardCharsets.UTF_8);

        String evento = null;
        String usuario = null;

        for (String linea : lineas) {
            if (linea.startsWith("Evento: ")) {
                evento = linea.substring(8).trim();
            }

            if (linea.startsWith("Usuario: ")) {
                usuario = linea.substring(9).trim();
            }
        }

        if (evento == null || usuario == null) {
            throw new IOException(
                    "No se pudo identificar el evento o usuario");
        }

        return construirRutaS3(evento, usuario, nombreArchivo);
    }

    private void validarNombreArchivo(String nombreArchivo) {

        if (nombreArchivo == null ||
                !nombreArchivo.matches(
                        "ticket-[0-9a-fA-F-]{36}\\.txt")) {
            throw new IllegalArgumentException(
                    "Nombre de ticket no valido");
        }
    }

    private void validarDatos(String evento,
                              String usuario,
                              int cantidad) {

        if (evento == null || evento.isBlank()
                || usuario == null || usuario.isBlank()
                || cantidad <= 0) {
            throw new IllegalArgumentException(
                    "Los datos del ticket no son validos");
        }

        normalizarSegmento(evento);
        normalizarSegmento(usuario);
    }
}
