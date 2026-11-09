
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.StandardCopyOption;

@Service
public class EfsService {

    private final Path efsPath;

    public EfsService(@Value("${efs.path:./tickets-local}") String ruta) {
        this.efsPath = Path.of(ruta).toAbsolutePath().normalize();
    }

    private Path obtenerRutaSegura(String nombreArchivo) {
        if (nombreArchivo == null ||
            !nombreArchivo.matches("ticket-[0-9a-fA-F-]{36}\\.txt")) {
            throw new IllegalArgumentException("Nombre de ticket no valido");
        }

        return efsPath.resolve(nombreArchivo).normalize();
    }

    public File guardarTicket(String nombreArchivo, byte[] contenido)
            throws IOException {

        Files.createDirectories(efsPath);

        Path ruta = obtenerRutaSegura(nombreArchivo);
        Files.write(ruta, contenido);

        return ruta.toFile();
    }

    public File obtenerTicket(String nombreArchivo) {
        return obtenerRutaSegura(nombreArchivo).toFile();
    }

    public File modificarTicket(String nombreArchivo, byte[] contenido)
            throws IOException {

        Path ruta = obtenerRutaSegura(nombreArchivo);

        if (!Files.isRegularFile(ruta)) {
            throw new java.io.FileNotFoundException("El ticket no existe");
        }

        Files.write(ruta, contenido);
        return ruta.toFile();
    }

    public boolean eliminarTicket(String nombreArchivo)
            throws IOException {

        Path ruta = obtenerRutaSegura(nombreArchivo);
        return Files.deleteIfExists(ruta);
    }

    public File obtenerDirectorio() {
        return efsPath.toFile();
    }
    public File saveToEfs(String nombreArchivo, MultipartFile archivo)
        throws IOException {

    Files.createDirectories(efsPath);

    Path ruta = efsPath.resolve(nombreArchivo)
            .normalize();

    if (!ruta.startsWith(efsPath)) {
        throw new IllegalArgumentException(
                "Ruta de archivo no valida");
    }

    try (var entrada = archivo.getInputStream()) {
        Files.copy(
                entrada,
                ruta,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    return ruta.toFile();
}
}
