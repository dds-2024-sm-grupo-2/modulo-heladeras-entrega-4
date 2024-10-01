package ar.edu.utn.dds.k3003.app;

import ar.edu.utn.dds.k3003.Controller.*;
import ar.edu.utn.dds.k3003.clients.ViandasProxy;
import ar.edu.utn.dds.k3003.worker.MQUtils;
import ar.edu.utn.dds.k3003.worker.TemperaturaWorker;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ar.edu.utn.dds.k3003.facades.dtos.Constants;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.javalin.Javalin;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeoutException;

public class WebApp {
    public static void main(String[] args) throws IOException, TimeoutException {
        EntityManagerFactory entityManagerFactory = startEntityManagerFactory();
        Integer port = Integer.parseInt(System.getProperty("port", "8080"));
        Javalin app = Javalin.create().start(port);
        var fachada = new Fachada();
        fachada.getRepoHeladera().setEntityManagerFactory(entityManagerFactory);
        fachada.getRepoHeladera().setEntityManager(entityManagerFactory.createEntityManager());
        var objectMapper = createObjectMapper();
        var eliminarController= new EliminarController(fachada);
        fachada.setViandasProxy(new ViandasProxy(objectMapper));
        //---------------WORKER--------------------//
        Map<String, String> env = System.getenv();
        ConnectionFactory factory = new ConnectionFactory();
        //factory.setHost(env.get("QUEUE_HOST"));
        factory.setHost("prawn.rmq.cloudamqp.com");
        //factory.setUsername(env.get("QUEUE_USERNAME"));
        factory.setUsername("wcvuathu");
        factory.setVirtualHost("wcvuathu");
        //factory.setPassword(env.get("QUEUE_PASSWORD"));
        factory.setPassword("IkXGMtAKDhWgnR3wIyDCx0eIaBC0xVFt");
        //String queueName = env.get("QUEUE_NAME");
        String queueName = "Temperaturas Queue";

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        TemperaturaWorker worker = new TemperaturaWorker(channel,queueName);
        worker.setFachada(fachada);
        worker.init();

        MQUtils mqutils= new MQUtils("prawn.rmq.cloudamqp.com","wcvuathu","IkXGMtAKDhWgnR3wIyDCx0eIaBC0xVFt","wcvuathu","Temperaturas Queue");
        mqutils.init();
        //---------------WORKER--------------------//
        app.get("/", ctx -> ctx.result("Hola"));
        app.post("/heladeras", new AltaHeladeraController(fachada));
        app.get("/heladeras/{idHeladera}", new SearchHeladeraController(fachada));
        app.get("/heladeras", new ListaHeladerasController(fachada));
        app.post("/temperaturas", new RegistrarTemperaturasController(fachada,mqutils));
        app.get("/heladeras/{idHeladera}/temperaturas", new ObtenerTemperaturasController(fachada));
        app.post("/depositos", new DepositarViandaController(fachada));
        app.post("/retiros", new RetirarViandaController(fachada));
        app.delete("/heladeras", eliminarController::eliminarHeladeras);
        app.delete("/viandas", eliminarController::eliminarViandas);
        app.delete("/temperaturas", eliminarController::eliminarTemperaturas);

    }

    public static ObjectMapper createObjectMapper() {
        var objectMapper = new ObjectMapper();
        configureObjectMapper(objectMapper);
        return objectMapper;
    }

    public static void configureObjectMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var sdf = new SimpleDateFormat(Constants.DEFAULT_SERIALIZATION_FORMAT, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        objectMapper.setDateFormat(sdf);
    }

    public static EntityManagerFactory startEntityManagerFactory() {
// https://stackoverflow.com/questions/8836834/read-environment-variables-in-persistence-xml-file
        Map<String, String> env = System.getenv();
        Map<String, Object> configOverrides = new HashMap<String, Object>();
        String[] keys = new String[]{"javax.persistence.jdbc.url", "javax.persistence.jdbc.user",
                "javax.persistence.jdbc.password", "javax.persistence.jdbc.driver", "hibernate.hbm2ddl.auto",
                "hibernate.connection.pool_size", "hibernate.show_sql"};
        for (String key : keys) {
            if (env.containsKey(key)) {
                String value = env.get(key);
                System.out.println(key + " = " + value);
                configOverrides.put(key, value);
            } else {
                System.out.println("Variable de entorno no encontrada: " + key);
            }
        }
        return Persistence.createEntityManagerFactory("db", configOverrides);
    }


}
