
package cl.duoc.ejemplo.ms.administracion.archivos.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EfsService {

    @Value("${efs.path}")
    private String efsPath;

    // Guarda archivos recibidos desde una petición HTTP
    public File saveToEfs(String filename, MultipartFile multipartFile)
            throws IOException {

        Path destino = obtenerRutaSegura(filename);
        Files.createDirectories(destino.getParent());

        multipartFile.transferTo(destino.toFile());

        return destino.toFile();
    }

    // Guarda archivos generados por nuestro sistema de tickets
    public File guardarTicket(String nombreArchivo, byte[] contenido)
            throws IOException {

        Path destino = obtenerRutaSegura(nombreArchivo);
        Files.createDirectories(destino.getParent());

        Files.write(destino, contenido);

        return destino.toFile();
    }

    // Evita guardar archivos fuera del directorio configurado
    private Path obtenerRutaSegura(String nombreArchivo)
            throws IOException {

        Path base = Paths.get(efsPath).toAbsolutePath().normalize();
        Path destino = base.resolve(nombreArchivo).normalize();

        if (!destino.startsWith(base) || destino.equals(base)) {
            throw new IOException("Ruta de archivo no permitida");
        }

        return destino;
    }

	public File obtenerTicket(String nombreArchivo) {
    return new File(efsPath, nombreArchivo);
}
// Modifica el contenido de un ticket existente
public File modificarTicket(String nombreArchivo, byte[] contenido)
        throws IOException {

    Path ruta = obtenerRutaSegura(nombreArchivo);

    if (!Files.isRegularFile(ruta)) {
        throw new java.io.FileNotFoundException(
            "El ticket no existe");
    }

    Files.write(ruta, contenido);

    return ruta.toFile();
}

// Elimina un ticket existente
public boolean eliminarTicket(String nombreArchivo)
        throws IOException {

    Path ruta = obtenerRutaSegura(nombreArchivo);

    return Files.deleteIfExists(ruta);
}

// Obtiene el directorio de almacenamiento configurado
public File obtenerDirectorio() {
    return new File(efsPath);
}
}
