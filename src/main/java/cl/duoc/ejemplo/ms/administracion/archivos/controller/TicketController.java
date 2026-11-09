
package cl.duoc.ejemplo.ms.administracion.archivos.controller;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import cl.duoc.ejemplo.ms.administracion.archivos.service.TicketService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.FileNotFoundException;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/generar")
    public ResponseEntity<?> generarTicket(
            @RequestParam String evento,
            @RequestParam String usuario,
            @RequestParam int cantidad) throws IOException {

        try {
            File archivo = ticketService.generarTicket(
                evento, usuario, cantidad);

            return ResponseEntity.ok(Map.of(
                "mensaje", "Ticket generado correctamente",
                "archivo", archivo.getName(),
                "estado", "CONFIRMADO"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/descargar/{nombreArchivo}")
public ResponseEntity<?> descargarTicket(
        @PathVariable String nombreArchivo) {

    try {
        File archivo = ticketService.obtenerTicket(nombreArchivo);

        FileSystemResource recurso =
            new FileSystemResource(archivo);

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" +
                archivo.getName() + "\""
            )
            .contentType(MediaType.TEXT_PLAIN)
            .body(recurso);

    } catch (FileNotFoundException e) {
        return ResponseEntity.status(404)
            .body(Map.of("error", e.getMessage()));

    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", e.getMessage()));

    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of(
                "error", "No se pudo descargar el ticket"
            ));
    }
}
@PutMapping("/modificar/{nombreArchivo}")
public ResponseEntity<?> modificarTicket(
        @PathVariable String nombreArchivo,
        @RequestParam String evento,
        @RequestParam String usuario,
        @RequestParam int cantidad) {

    try {
        File archivo = ticketService.modificarTicket(
            nombreArchivo, evento, usuario, cantidad);

        return ResponseEntity.ok(Map.of(
            "mensaje", "Ticket modificado correctamente",
            "archivo", archivo.getName()
        ));

    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", e.getMessage()));

    } catch (java.io.FileNotFoundException e) {
        return ResponseEntity.status(404)
            .body(Map.of("error", e.getMessage()));

    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Error al modificar ticket"));
    }
}
@DeleteMapping("/eliminar/{nombreArchivo}")
public ResponseEntity<?> eliminarTicket(
        @PathVariable String nombreArchivo) {

    try {
        boolean eliminado = ticketService.eliminarTicket(nombreArchivo);

        if (!eliminado) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Ticket no encontrado"));
        }

        return ResponseEntity.ok(Map.of(
            "mensaje", "Ticket eliminado correctamente"
        ));

    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", e.getMessage()));

    } catch (IOException e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Error al eliminar ticket"));
    }
}
// Consulta estadísticas de reservas por evento
@GetMapping("/estadisticas")
public ResponseEntity<?> obtenerEstadisticas()
        throws IOException {

    return ResponseEntity.ok(
        ticketService.obtenerEstadisticas()
    );
}
}
