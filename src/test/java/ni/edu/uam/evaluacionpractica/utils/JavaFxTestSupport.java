package ni.edu.uam.evaluacionpractica.utils;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaFxTestSupport {
    private static boolean iniciado;

    private JavaFxTestSupport() { }

    static synchronized void iniciar() throws Exception {
        if (iniciado) return;
        CountDownLatch listo = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            listo.countDown();
        });
        assertTrue(listo.await(10, TimeUnit.SECONDS), "JavaFX no inició a tiempo");
        iniciado = true;
    }

    static <T> T enJavaFx(Callable<T> operacion) throws Exception {
        FutureTask<T> resultado = new FutureTask<>(operacion);
        Platform.runLater(resultado);
        return resultado.get(10, TimeUnit.SECONDS);
    }

    static void esperarCarga(Node vista) throws Exception {
        CountDownLatch lista = new CountDownLatch(1);
        enJavaFx(() -> {
            if (!vista.isDisabled()) {
                lista.countDown();
            } else {
                ChangeListener<Boolean> listener = new ChangeListener<>() {
                    @Override
                    public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs,
                                        Boolean anterior, Boolean ocupada) {
                        if (!ocupada) {
                            vista.disabledProperty().removeListener(this);
                            lista.countDown();
                        }
                    }
                };
                vista.disabledProperty().addListener(listener);
            }
            return null;
        });
        assertTrue(lista.await(10, TimeUnit.SECONDS), "La vista no terminó de cargar");
    }
}
