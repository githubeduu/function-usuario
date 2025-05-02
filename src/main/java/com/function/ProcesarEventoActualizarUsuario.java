package com.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.*;

public class ProcesarEventoActualizarUsuario {

    @FunctionName("ProcesarEventoActualizarUsuario")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "event/usuario/actualizar"
        ) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Procesando evento de actualización");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode[] events = mapper.readValue(request.getBody().orElse("[]"), JsonNode[].class);

            for (JsonNode event : events) {
                String eventType = event.get("eventType").asText();

                if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
                    String validationCode = event.get("data").get("validationCode").asText();
                    Map<String, String> response = new HashMap<>();
                    response.put("validationResponse", validationCode);
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(response)
                            .build();
                }

                if ("UsuarioActualizado".equals(eventType)) {
                    String subject = event.get("subject").asText();
                    String id = subject.substring(subject.lastIndexOf("/") + 1);

                    JsonNode data = event.get("data");
                    String contrasena = data.get("contrasena").asText();
                    String direccion = data.get("direccion").asText();
                    String comuna = data.get("comuna").asText();
                    Long rolId = data.get("rolId").asLong();

                    WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
                    String tmpDir = System.getProperty("java.io.tmpdir").replace("\\", "/");
                    String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + tmpDir;
                    String oracleUser = "duoc_fullstack";
                    String oraclePass = "Eduardocr#2610";

                    try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                        conn.setAutoCommit(false);

                        PreparedStatement stmtUsuario = conn.prepareStatement(
                            "UPDATE USUARIO SET DIRECCION = ?, COMUNA = ?, ROL_ID = ? WHERE ID = ?");
                        stmtUsuario.setString(1, direccion);
                        stmtUsuario.setString(2, comuna);
                        stmtUsuario.setLong(3, rolId);
                        stmtUsuario.setString(4, id);
                        int filasUsuario = stmtUsuario.executeUpdate();

                        PreparedStatement stmtAuth = conn.prepareStatement(
                            "UPDATE AUTH SET PASSWORD = ? WHERE USUARIO_ID = ?");
                        stmtAuth.setString(1, contrasena);
                        stmtAuth.setString(2, id);
                        int filasAuth = stmtAuth.executeUpdate();

                        if (filasUsuario > 0 || filasAuth > 0) {
                            conn.commit();
                            context.getLogger().info("Usuario actualizado correctamente.");
                        } else {
                            conn.rollback();
                            context.getLogger().warning("No se encontró usuario con ID: " + id);
                        }
                    }
                }
            }

            return request.createResponseBuilder(HttpStatus.OK).body("Evento procesado.").build();

        } catch (Exception e) {
            context.getLogger().severe("Error procesando evento: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage()).build();
        }
    }
}
