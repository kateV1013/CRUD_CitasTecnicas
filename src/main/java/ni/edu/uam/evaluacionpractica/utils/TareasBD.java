package ni.edu.uam.evaluacionpractica.utils;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class TareasBD {
    // Pausa para observar el trabajo en segundo plano; usar 0 para quitarla.
    private static final long PAUSA_DEMOSTRACION_MS = 6_000;
    private static final ExecutorService EJECUTOR = Executors.newFixedThreadPool(3,
            Thread.ofPlatform().daemon(true).name("citas-bd-", 1).factory());
    private static final Set<Task<?>> ACTIVAS = ConcurrentHashMap.newKeySet();
    private static boolean cerrado;

    private final ReadOnlyBooleanWrapper ocupado = new ReadOnlyBooleanWrapper();
    private int pendientes;

    public ReadOnlyBooleanProperty ocupadoProperty() {
        return ocupado.getReadOnlyProperty();
    }

    public <T> void ejecutar(Callable<T> operacion, Consumer<T> alCompletar,
                            Consumer<Throwable> alFallar) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Las tareas deben solicitarse desde JavaFX.");
        }
        if (cerrado) return;

        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                String hilo = Thread.currentThread().getName();
                System.out.printf("[%s] Pausa de demostración: %d ms.%n", hilo, PAUSA_DEMOSTRACION_MS);
                Thread.sleep(PAUSA_DEMOSTRACION_MS);
                System.out.printf("[%s] Ejecutando operación de base de datos.%n", hilo);
                return operacion.call();
            }
        };
        pendientes++;
        ocupado.set(true);
        ACTIVAS.add(tarea);
        tarea.setOnSucceeded(event -> finalizar(tarea, () -> alCompletar.accept(tarea.getValue())));
        tarea.setOnFailed(event -> finalizar(tarea, () -> alFallar.accept(tarea.getException())));
        tarea.setOnCancelled(event -> finalizar(tarea, () -> { }));
        EJECUTOR.execute(tarea);
    }

    private void finalizar(Task<?> tarea, Runnable respuesta) {
        try {
            if (!cerrado) respuesta.run();
        } finally {
            ACTIVAS.remove(tarea);
            pendientes--;
            ocupado.set(pendientes > 0);
        }
    }


    public static void cerrar() {
        cerrado = true;
        ACTIVAS.forEach(tarea -> tarea.cancel(true));
        ACTIVAS.clear();
        EJECUTOR.shutdownNow();
    }
}
