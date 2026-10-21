package ni.edu.uam.evaluacionpractica.utils;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import ni.edu.uam.evaluacionpractica.modelocrud.HelloApplication;
import ni.edu.uam.evaluacionpractica.modelocrud.Controllers.CitaTecnicaController;
import ni.edu.uam.evaluacionpractica.modelocrud.Controllers.ClienteController;
import ni.edu.uam.evaluacionpractica.modelocrud.dao.CitaTecnicaDAO;
import ni.edu.uam.evaluacionpractica.modelocrud.dao.ClienteDAO;
import ni.edu.uam.evaluacionpractica.modelocrud.model.CitaTecnica;
import ni.edu.uam.evaluacionpractica.modelocrud.model.Cliente;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static ni.edu.uam.evaluacionpractica.utils.JavaFxTestSupport.enJavaFx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CITAS_TEST_FX", matches = "true")
class ControladoresAsyncTest {
    @BeforeAll
    static void iniciar() throws Exception {
        JavaFxTestSupport.iniciar();
    }

    @Test
    void cargaLasVistasSinBloquearJavaFxYMuestraLosResultadosAlTerminar() throws Exception {
        CountDownLatch consultasIniciadas = new CountDownLatch(3);
        CountDownLatch continuar = new CountDownLatch(1);
        Cliente cliente = Cliente.builder().idCliente(1).nombres("Ana").apellidos("Pérez")
                .telefono("88881111").correo("ana@example.com").activo(true).build();
        CitaTecnica cita = CitaTecnica.builder().idCita(1).idCliente(1).nombreCliente("Ana Pérez")
                .fecha(LocalDate.now().plusDays(1)).hora(LocalTime.NOON).tipoServicio("Revisión")
                .descripcion("Equipo").estado(CitaTecnica.PENDIENTE).build();
        ClienteDAO clientes = new ClienteDAO() {
            @Override
            public List<Cliente> listarTodos() {
                esperarConsulta(consultasIniciadas, continuar);
                return List.of(cliente);
            }
        };
        CitaTecnicaDAO citas = new CitaTecnicaDAO() {
            @Override
            public List<CitaTecnica> filtrar(LocalDate fecha, String estado) {
                esperarConsulta(consultasIniciadas, continuar);
                return List.of(cita);
            }
        };
        List<Object> controladores = new ArrayList<>();
        try {
            Parent raiz = enJavaFx(() -> {
                FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("Views/MainView.fxml"));
                loader.setControllerFactory(tipo -> {
                    try {
                        Object controlador = tipo.getDeclaredConstructor().newInstance();
                        asignar(controlador, "clienteDAO", clientes);
                        if (controlador instanceof CitaTecnicaController) asignar(controlador, "citaDAO", citas);
                        controladores.add(controlador);
                        return controlador;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                });
                return loader.load();
            });
            assertNotNull(raiz);
            assertTrue(consultasIniciadas.await(10, TimeUnit.SECONDS));
            assertEquals(2, controladores.size());
            // Este evento se procesa con las tres consultas todavía detenidas.
            enJavaFx(() -> {
                for (Object controlador : controladores) {
                    BorderPane vista = (BorderPane) leer(controlador, "vista");
                    assertTrue(vista.getLeft().isDisabled());
                    assertTrue(vista.getCenter().isDisabled());
                }
                return null;
            });
            continuar.countDown();
            for (Object controlador : controladores) {
                BorderPane vista = enJavaFx(() -> (BorderPane) leer(controlador, "vista"));
                JavaFxTestSupport.esperarCarga(vista.getCenter());
            }
            enJavaFx(() -> {
                for (Object controlador : controladores) {
                    if (controlador instanceof ClienteController) {
                        assertEquals(List.of(cliente), ((TableView<?>) leer(controlador, "tblClientes")).getItems());
                    } else {
                        assertEquals(List.of(cita), ((TableView<?>) leer(controlador, "tblCitas")).getItems());
                        assertEquals(List.of(cliente), ((ComboBox<?>) leer(controlador, "cmbCliente")).getItems());
                    }
                }
                return null;
            });
        } finally {
            continuar.countDown();
        }
    }

    private static void esperarConsulta(CountDownLatch iniciadas, CountDownLatch continuar) {
        assertFalse(Platform.isFxApplicationThread(), "JDBC no debe ejecutarse en JavaFX");
        iniciadas.countDown();
        try {
            assertTrue(continuar.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void asignar(Object instancia, String nombre, Object valor) throws ReflectiveOperationException {
        Field campo = instancia.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(instancia, valor);
    }

    private static Object leer(Object instancia, String nombre) throws ReflectiveOperationException {
        Field campo = instancia.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(instancia);
    }
}
