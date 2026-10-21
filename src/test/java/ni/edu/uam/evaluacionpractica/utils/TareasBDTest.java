package ni.edu.uam.evaluacionpractica.utils;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static ni.edu.uam.evaluacionpractica.utils.JavaFxTestSupport.enJavaFx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CITAS_TEST_FX", matches = "true")
class TareasBDTest {
    @BeforeAll
    static void iniciar() throws Exception {
        JavaFxTestSupport.iniciar();
    }

    @Test
    void ejecutaFueraDeJavaFxSinBloquearLaInterfazYEntregaElResultadoEnJavaFx() throws Exception {
        TareasBD tareas = new TareasBD();
        CountDownLatch iniciada = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        CompletableFuture<String> resultado = new CompletableFuture<>();
        try {
            enJavaFx(() -> {
                tareas.ejecutar(() -> {
                    assertFalse(Platform.isFxApplicationThread());
                    iniciada.countDown();
                    assertTrue(continuar.await(10, TimeUnit.SECONDS));
                    return "lista";
                }, valor -> {
                    if (Platform.isFxApplicationThread()) resultado.complete(valor);
                    else resultado.completeExceptionally(new AssertionError("Resultado fuera de JavaFX"));
                }, resultado::completeExceptionally);
                return null;
            });
            assertTrue(iniciada.await(10, TimeUnit.SECONDS));
            // Este evento debe ejecutarse aunque la operación de BD siga esperando.
            assertTrue(enJavaFx(() -> tareas.ocupadoProperty().get()));
            continuar.countDown();
            assertEquals("lista", resultado.get(10, TimeUnit.SECONDS));
            assertFalse(enJavaFx(() -> tareas.ocupadoProperty().get()));
        } finally {
            continuar.countDown();
        }
    }

    @Test
    void entregaErroresEnJavaFxYLiberaElEstadoOcupado() throws Exception {
        TareasBD tareas = new TareasBD();
        SQLException error = new SQLException("Sin conexión", "08001");
        CompletableFuture<Throwable> resultado = new CompletableFuture<>();
        enJavaFx(() -> {
            tareas.ejecutar(() -> { throw error; }, valor ->
                    resultado.completeExceptionally(new AssertionError("Se esperaba un error")), fallo -> {
                if (Platform.isFxApplicationThread()) resultado.complete(fallo);
                else resultado.completeExceptionally(new AssertionError("Error fuera de JavaFX"));
            });
            return null;
        });
        assertSame(error, resultado.get(10, TimeUnit.SECONDS));
        assertFalse(enJavaFx(() -> tareas.ocupadoProperty().get()));
    }

    @Test
    void mantieneLaVistaOcupadaHastaQueTerminanTodasLasConsultas() throws Exception {
        TareasBD tareas = new TareasBD();
        CountDownLatch continuar = new CountDownLatch(1);
        CompletableFuture<Boolean> primera = new CompletableFuture<>();
        CompletableFuture<Boolean> segunda = new CompletableFuture<>();
        try {
            enJavaFx(() -> {
                tareas.ejecutar(() -> {
                    assertTrue(continuar.await(10, TimeUnit.SECONDS));
                    return true;
                }, segunda::complete, segunda::completeExceptionally);
                tareas.ejecutar(() -> true, primera::complete, primera::completeExceptionally);
                return null;
            });
            assertTrue(primera.get(10, TimeUnit.SECONDS));
            assertTrue(enJavaFx(() -> tareas.ocupadoProperty().get()));
            continuar.countDown();
            assertTrue(segunda.get(10, TimeUnit.SECONDS));
            assertFalse(enJavaFx(() -> tareas.ocupadoProperty().get()));
        } finally {
            continuar.countDown();
        }
    }
}
